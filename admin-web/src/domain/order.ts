/**
 * 管理端订单规则，纯函数：传入订单副本，返回新对象，不请求网络。
 * Mock 的 handle.ts 调用它们；Java 后端还没有同样的实现。
 * 金额单位是分。应付 = 商品行金额 + 上门安装费（未定价的行按 0 计）+ 运费 + 整单调整。
 * 已取消、已完成不能改价、发货或派工。待付款也可以发货和派安装师傅。
 * 已有成功付款，或已有未取消的发货 / 派工，不能直接取消（退款未做）。
 * 新应付低于已付时拒绝修改，不自动退差。安装费只能出现在 ON_SITE_INSTALL 行；null 表示还没定价，0 表示明确免费。
 * 线下收款可以少于这一笔应付：实收记成功，差额拆成标题带「（余款）」的新待付。
 * 派安装师傅会占档期。没有这一格时新建，容量 2；已用满则报档期冲突（业务码 3002）。
 */
import type {
  AdminOrder,
  AssignOrderRequest,
  CancelOrderRequest,
  CreatePaymentsRequest,
  CreateShipmentRequest,
  AdjustPriceRequest,
  DeliveryMethod,
  FreightRequest,
  OfflineConfirmRequest,
  OrderItem,
  PatchPaymentRequest,
  Payment,
  PaymentDraft,
  PriceLog,
  Shipment,
  Technician,
  TechnicianSchedule,
} from '@/api/types'
import { ApiError } from './errors'
import { OFFLINE_METHOD_LABEL } from './labels'
import { formatYuan } from './money'

export interface IdSource {
  next(): number
}

export function cloneData<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function paidAmount(order: AdminOrder): number {
  return order.payments.filter((p) => p.status === 'SUCCESS').reduce((sum, p) => sum + p.amount, 0)
}

function hasActiveShipment(item: OrderItem): boolean {
  return !!item.shipment && item.shipment.status !== 'CANCELLED'
}

function fulfillmentStarted(order: AdminOrder): boolean {
  return order.items.some((item) => {
    if (item.shipment && item.shipment.status !== 'CANCELLED' && item.shipment.status !== 'PENDING') return true
    return item.deliveryMethod === 'ON_SITE_INSTALL' && !!item.technicianId && !!item.workOrderNo
  })
}

/**
 * 发货进度，和有没有收到钱无关。
 * shipped：状态不是待发出、也不是已取消的行数。done：其中已完成的行数。total：全部订单行。
 */
export function fulfillmentProgress(order: Pick<AdminOrder, 'items'>): { shipped: number; done: number; total: number } {
  let shipped = 0
  let done = 0
  for (const item of order.items) {
    if (!item.shipment || item.shipment.status === 'CANCELLED' || item.shipment.status === 'PENDING') continue
    shipped += 1
    if (item.shipment.status === 'DONE') done += 1
  }
  return { shipped, done, total: order.items.length }
}

/**
 * 这一行现在能不能发货。不看付款状态。
 * 订单已取消或已完成则不行；该行已经有一条未取消的发货记录也不行。
 */
export function canShipItem(order: Pick<AdminOrder, 'status'>, item: OrderItem): boolean {
  if (order.status === 'CANCELLED' || order.status === 'COMPLETED') return false
  return !hasActiveShipment(item)
}

/** 按行重算商品合计、安装费合计和应付。不改已付，也不改状态。 */
export function recalcOrder(order: AdminOrder): void {
  order.goodsAmount = order.items.reduce((sum, item) => sum + item.lineAmount, 0)
  order.installAmount = order.items.reduce((sum, item) => {
    if (item.deliveryMethod !== 'ON_SITE_INSTALL') return sum
    return sum + (item.installFee ?? 0)
  }, 0)
  order.payableAmount = order.goodsAmount + order.installAmount + order.freightAmount + order.adjustAmount
}

export type InstallFeeState =
  | { kind: 'none' }
  | { kind: 'pending'; amount: number }
  | { kind: 'set'; amount: number }

/** 订单级安装费展示：无上门安装行为 none；任一安装行未定价为 pending。 */
export function installFeeState(order: Pick<AdminOrder, 'items' | 'installAmount'>): InstallFeeState {
  const installs = order.items.filter((item) => item.deliveryMethod === 'ON_SITE_INSTALL')
  if (installs.length === 0) return { kind: 'none' }
  if (installs.some((item) => item.installFee == null)) return { kind: 'pending', amount: order.installAmount }
  return { kind: 'set', amount: order.installAmount }
}

/**
 * 收款和发货分开决定状态。已取消、已完成保持不动。
 * 没有待付且已付不少于应付：每一行都已完成则为已完成；已经发出或已派安装则为履约中；否则已付款。
 * 还有待付且已付大于 0：部分付款。还没结清、又不满足上一种情况时是待付款，已经发货也不会改成履约中。
 */
export function syncOrderStatus(order: AdminOrder): void {
  const paid = paidAmount(order)
  order.paidAmount = paid
  if (order.status === 'CANCELLED' || order.status === 'COMPLETED') return
  const waits = order.payments.filter((p) => p.status === 'WAIT')
  if (waits.length === 0 && paid >= order.payableAmount) {
    const progress = fulfillmentProgress(order)
    if (progress.total > 0 && progress.done === progress.total) order.status = 'COMPLETED'
    else order.status = fulfillmentStarted(order) ? 'FULFILLING' : 'PAID'
    return
  }
  if (waits.length > 0 && paid > 0) {
    order.status = 'PARTIALLY_PAID'
    return
  }
  order.status = 'PENDING_PAYMENT'
}

function addLog(
  order: AdminOrder,
  ids: IdSource,
  now: string,
  log: Omit<PriceLog, 'id' | 'orderId' | 'createdAt'>,
): void {
  order.priceLogs.unshift({
    id: ids.next(),
    orderId: order.id,
    createdAt: now,
    ...log,
  })
  order.priceAdjusted = true
}

function realignWaits(order: AdminOrder, previousPayable: number, now: string): string | null {
  const paid = paidAmount(order)
  if (order.payableAmount < paid) {
    return '已付金额超过新应付，退差规则未定（O1），未改价'
  }
  const delta = order.payableAmount - previousPayable
  if (delta === 0) return null
  const waits = order.payments.filter((p) => p.status === 'WAIT')
  if (waits.length === 0) {
    if (delta > 0) return '没有未付应付单可调整，请先追加一笔待付'
    return null
  }
  if (waits.length === 1) {
    const unpaid = order.payableAmount - paid
    const wait = waits[0]
    if (!wait) return null
    if (unpaid <= 0) wait.status = 'CLOSED'
    else wait.amount = unpaid
    wait.updatedAt = now
    return null
  }
  const last = waits[waits.length - 1]
  if (!last) return null
  const nextAmount = last.amount + delta
  if (nextAmount <= 0) return '存在多笔未付，自动分摊后金额不为正，请手工修改各笔应付'
  last.amount = nextAmount
  last.updatedAt = now
  return null
}

function suffixOf(type: PaymentDraft['type']): string {
  switch (type) {
    case 'FULL':
      return 'F'
    case 'DEPOSIT':
      return 'D'
    case 'BALANCE':
      return 'B'
    case 'REPAIR':
      return 'R'
    case 'FREIGHT':
      return 'FR'
  }
}

export function buildPayment(opts: {
  ids: IdSource
  bizType: Payment['bizType']
  bizId: number
  bizNo: string
  draft: PaymentDraft
  sortNo: number
  now: string
}): Payment {
  const id = opts.ids.next()
  const suffix = suffixOf(opts.draft.type)
  return {
    id,
    bizType: opts.bizType,
    bizId: opts.bizId,
    paymentNo: `${opts.bizNo}-${suffix}-${opts.sortNo}`,
    outTradeNo: `${opts.bizNo}-${suffix}-${opts.sortNo}`,
    channel: opts.draft.channel ?? 'OFFLINE',
    type: opts.draft.type,
    title: opts.draft.title.trim(),
    amount: opts.draft.amount,
    status: 'WAIT',
    sortNo: opts.sortNo,
    createdAt: opts.now,
    updatedAt: opts.now,
  }
}

function assertOpen(order: AdminOrder, action: string): void {
  if (order.status === 'CANCELLED' || order.status === 'COMPLETED') {
    throw new ApiError(3003, `订单已${order.status === 'CANCELLED' ? '取消' : '完成'}，不能${action}`)
  }
}

export function adjustPrice(
  order: AdminOrder,
  req: AdjustPriceRequest,
  operator: string,
  now: string,
  ids: IdSource,
): AdminOrder {
  if (!req.reason?.trim()) throw new ApiError(1001, '请填写改价原因')
  assertOpen(order, '改价')
  const next = cloneData(order)
  const previousPayable = next.payableAmount
  for (const line of req.lines ?? []) {
    const item = next.items.find((it) => it.id === line.orderItemId)
    if (!item) throw new ApiError(4040, '订单行不存在')
    if (line.unitPrice != null) {
      if (line.unitPrice < 0) throw new ApiError(1001, '单价不能为负')
      if (line.unitPrice !== item.unitPrice) {
        addLog(next, ids, now, {
          scope: 'LINE',
          orderItemId: item.id,
          field: 'unitPrice',
          beforeAmount: item.unitPrice,
          afterAmount: line.unitPrice,
          reason: req.reason.trim(),
          operatorName: operator,
        })
        item.unitPrice = line.unitPrice
        item.lineAmount = line.unitPrice * item.quantity
      }
    }
    if (line.installFee != null) {
      if (line.installFee < 0) throw new ApiError(1001, '安装费不能为负')
      if (item.deliveryMethod !== 'ON_SITE_INSTALL' && line.installFee > 0) {
        throw new ApiError(3004, '仅上门安装行可设置安装费')
      }
      if (line.installFee !== item.installFee) {
        addLog(next, ids, now, {
          scope: 'LINE',
          orderItemId: item.id,
          field: 'installFee',
          beforeAmount: item.installFee,
          afterAmount: line.installFee,
          reason: req.reason.trim(),
          operatorName: operator,
        })
        item.installFee = line.installFee
      }
    }
  }
  if (req.adjustAmount != null && req.adjustAmount !== next.adjustAmount) {
    addLog(next, ids, now, {
      scope: 'ORDER',
      field: 'adjustAmount',
      beforeAmount: next.adjustAmount,
      afterAmount: req.adjustAmount,
      reason: req.reason.trim(),
      operatorName: operator,
    })
    next.adjustAmount = req.adjustAmount
  }
  recalcOrder(next)
  if (next.payableAmount < 0) throw new ApiError(3004, '应付金额不能为负')
  next.updatedAt = now
  const message = realignWaits(next, previousPayable, now)
  if (message) throw new ApiError(3003, message)
  syncOrderStatus(next)
  return next
}

export function setFreight(
  order: AdminOrder,
  req: FreightRequest,
  operator: string,
  now: string,
  ids: IdSource,
): AdminOrder {
  if (req.freightAmount < 0) throw new ApiError(1001, '运费不能为负')
  assertOpen(order, '修改运费')
  const next = cloneData(order)
  if (req.freightAmount === next.freightAmount) return next
  const previousPayable = next.payableAmount
  addLog(next, ids, now, {
    scope: 'ORDER',
    field: 'freightAmount',
    beforeAmount: next.freightAmount,
    afterAmount: req.freightAmount,
    reason: '管理员手加运费',
    operatorName: operator,
  })
  next.freightAmount = req.freightAmount
  recalcOrder(next)
  next.updatedAt = now
  const paid = paidAmount(next)
  if (next.payableAmount < paid) {
    throw new ApiError(3003, '已付金额超过新应付，退差规则未定（O1），未改运费')
  }
  const waits = next.payments.filter((p) => p.status === 'WAIT')
  if (waits.length === 0) {
    const gap = next.payableAmount - paid
    if (gap > 0) {
      const sortNo = next.payments.reduce((max, p) => Math.max(max, p.sortNo), 0) + 1
      next.payments.push(
        buildPayment({
          ids,
          bizType: 'ORDER',
          bizId: next.id,
          bizNo: next.orderNo,
          draft: { type: 'FREIGHT', title: '运费', amount: gap, channel: 'OFFLINE' },
          sortNo,
          now,
        }),
      )
    }
    syncOrderStatus(next)
    return next
  }
  const message = realignWaits(next, previousPayable, now)
  if (message) throw new ApiError(3003, message)
  syncOrderStatus(next)
  return next
}

export function createPayments(
  order: AdminOrder,
  req: CreatePaymentsRequest,
  now: string,
  ids: IdSource,
): AdminOrder {
  assertOpen(order, '调整应付')
  if (!req.payments?.length) throw new ApiError(1001, '请填写应付阶段')
  for (const draft of req.payments) {
    if (!draft.title?.trim()) throw new ApiError(1001, '请填写应付标题')
    if (draft.amount <= 0) throw new ApiError(1001, '应付金额须大于 0')
  }
  const next = cloneData(order)
  if (req.mode === 'SPLIT') {
    const fulls = next.payments.filter((p) => p.status === 'WAIT' && p.type === 'FULL')
    if (fulls.length === 0) throw new ApiError(3003, '没有可拆分的未付全款')
    for (const full of fulls) {
      full.status = 'CLOSED'
      full.updatedAt = now
    }
  } else if (req.mode !== 'APPEND') {
    throw new ApiError(1001, 'mode 只能是 SPLIT 或 APPEND')
  }
  let sortNo = next.payments.reduce((max, p) => Math.max(max, p.sortNo), 0)
  for (const draft of req.payments) {
    sortNo += 1
    next.payments.push(
      buildPayment({
        ids,
        bizType: 'ORDER',
        bizId: next.id,
        bizNo: next.orderNo,
        draft,
        sortNo,
        now,
      }),
    )
  }
  next.updatedAt = now
  syncOrderStatus(next)
  return next
}

export function patchPayment(payment: Payment, req: PatchPaymentRequest, now: string): void {
  if (payment.status !== 'WAIT') throw new ApiError(3003, '只能修改未付应付单')
  if (req.amount != null) {
    if (req.amount <= 0) throw new ApiError(1001, '应付金额须大于 0')
    payment.amount = req.amount
  }
  if (req.title != null) {
    if (!req.title.trim()) throw new ApiError(1001, '标题不能为空')
    payment.title = req.title.trim()
  }
  payment.updatedAt = now
}

export function patchOrderPayment(
  order: AdminOrder,
  paymentId: number,
  req: PatchPaymentRequest,
  now: string,
): AdminOrder {
  const next = cloneData(order)
  const payment = next.payments.find((p) => p.id === paymentId)
  if (!payment) throw new ApiError(4040, '应付单不存在')
  patchPayment(payment, req, now)
  next.updatedAt = now
  syncOrderStatus(next)
  return next
}

const PAID_AT_PATTERN = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/
const SPLIT_SUFFIX = '（余款）'

/** 本次线下收款金额（分）校验，弹窗与 mock 共用；通过返回 null。 */
export function offlineAmountError(amount: number | undefined | null, remaining: number): string | null {
  if (amount == null || !Number.isFinite(amount)) return '请填写本次收款金额'
  if (!Number.isInteger(amount)) return '收款金额须精确到分'
  if (amount <= 0) return '本次收款金额须大于 0'
  if (amount > remaining) return `本次收款金额不能超过剩余 ¥${formatYuan(remaining)}`
  return null
}

/** 余款标题。已经带「（余款）」时先去掉再补上，避免叠成两截。 */
export function splitTitle(title: string): string {
  return `${title.replace(SPLIT_SUFFIX, '')}${SPLIT_SUFFIX}`
}

/**
 * 登记一笔线下到账。amount 小于应付单金额时，该笔按实收金额记为已收，
 * 剩余部分拆成一笔新的 WAIT（同类型，标题加「余款」），保证每条 SUCCESS 对应一次真实到账，
 * 且未关闭应付合计仍等于订单应付。
 */
export function confirmOffline(
  payments: Payment[],
  paymentId: number,
  bizNo: string,
  req: OfflineConfirmRequest,
  operator: { name: string; id: number },
  now: string,
  ids: IdSource,
): Payment {
  const payment = payments.find((p) => p.id === paymentId)
  if (!payment) throw new ApiError(4040, '应付单不存在')
  if (payment.status !== 'WAIT') throw new ApiError(3003, '该应付单不是待付状态')
  if (!req.method || !(req.method in OFFLINE_METHOD_LABEL)) throw new ApiError(1001, '请选择收款方式')
  const amount = req.amount ?? payment.amount
  const amountError = offlineAmountError(amount, payment.amount)
  if (amountError) throw new ApiError(1001, amountError)
  const paidAt = req.paidAt?.trim() || now
  if (!PAID_AT_PATTERN.test(paidAt)) throw new ApiError(1001, '收款时间格式应为 yyyy-MM-dd HH:mm:ss')
  // 格式补齐了年月日时分秒，按字符串比较就等于按时间先后比较。
  if (paidAt > now) throw new ApiError(1001, '收款时间不能晚于当前时间')

  const remaining = payment.amount - amount
  if (remaining > 0) {
    const sortNo = payments.reduce((max, p) => Math.max(max, p.sortNo), 0) + 1
    const rest = buildPayment({
      ids,
      bizType: payment.bizType,
      bizId: payment.bizId,
      bizNo,
      draft: {
        type: payment.type,
        title: splitTitle(payment.title),
        amount: remaining,
        channel: 'OFFLINE',
      },
      sortNo,
      now,
    })
    rest.splitFromId = payment.id
    payments.push(rest)
  }

  const remark = req.remark?.trim() ?? ''
  payment.amount = amount
  payment.status = 'SUCCESS'
  payment.channel = 'OFFLINE'
  payment.offlineMethod = req.method
  payment.voucherUrls = (req.voucherUrls ?? []).map((item) => item.trim()).filter(Boolean)
  payment.remark = remark || undefined
  payment.paidAt = paidAt
  payment.operatorId = operator.id
  payment.operatorName = operator.name
  payment.updatedAt = now
  return payment
}

export function offlineConfirmOrder(
  order: AdminOrder,
  paymentId: number,
  req: OfflineConfirmRequest,
  operator: { name: string; id: number },
  now: string,
  ids: IdSource,
): AdminOrder {
  if (order.status === 'CANCELLED') throw new ApiError(3003, '已取消订单不能登记收款')
  const next = cloneData(order)
  confirmOffline(next.payments, paymentId, next.orderNo, req, operator, now, ids)
  next.updatedAt = now
  syncOrderStatus(next)
  return next
}

function requireText(value: string | undefined, label: string): string {
  const text = value?.trim() ?? ''
  if (!text) throw new ApiError(1001, `请填写${label}`)
  return text
}

function assertVoucher(method: DeliveryMethod, req: CreateShipmentRequest): void {
  if (method === 'EXPRESS') {
    requireText(req.carrier, '承运商')
    requireText(req.trackingNo, '快递单号')
  } else if (method === 'FREIGHT') {
    requireText(req.logisticsCompany, '物流公司')
    if (!req.freightNo?.trim() && !req.vehicleNo?.trim()) {
      throw new ApiError(1001, '请填写物流单号或车牌')
    }
  } else if (method === 'HOME_DELIVERY') {
    requireText(req.courierName, '送货人')
    requireText(req.courierMobile, '送货人电话')
  } else if (method === 'ON_SITE_INSTALL') {
    requireText(req.courierName, '师傅姓名')
  } else if (method === 'STORE_PICKUP') {
    // 自提码可空，空则生成
  }
}

/**
 * 按行发货，不要求已经付款。已取消、已完成，或该行已有未取消的发货记录时拒绝。
 * 快递要承运商和单号；物流要公司，以及物流单号或车牌；上门送货要送货人和电话；
 * 上门安装要师傅姓名；到店自提没有自提码时生成 ZT 加 id 末 6 位。
 */
export function addShipment(
  order: AdminOrder,
  req: CreateShipmentRequest,
  now: string,
  ids: IdSource,
): AdminOrder {
  assertFulfillable(order, '发货')
  const next = cloneData(order)
  const item = next.items.find((it) => it.id === req.orderItemId)
  if (!item) throw new ApiError(4040, '订单行不存在')
  if (req.method && req.method !== item.deliveryMethod) {
    throw new ApiError(3004, '发货方式与订单行不一致')
  }
  if (hasActiveShipment(item)) {
    throw new ApiError(3003, '该行已有发货记录')
  }
  assertVoucher(item.deliveryMethod, req)
  const id = ids.next()
  const shipment: Shipment = {
    id,
    orderId: next.id,
    orderItemId: item.id,
    method: item.deliveryMethod,
    status: 'DISPATCHED',
    carrier: req.carrier?.trim() || undefined,
    trackingNo: req.trackingNo?.trim() || undefined,
    logisticsCompany: req.logisticsCompany?.trim() || undefined,
    freightNo: req.freightNo?.trim() || undefined,
    vehicleNo: req.vehicleNo?.trim() || undefined,
    courierName: req.courierName?.trim() || undefined,
    courierMobile: req.courierMobile?.trim() || undefined,
    appointAt: req.appointAt?.trim() || undefined,
    pickupCode: req.pickupCode?.trim() || (item.deliveryMethod === 'STORE_PICKUP' ? `ZT${String(id).slice(-6)}` : undefined),
    pickupStoreId: req.pickupStoreId,
    remark: req.remark?.trim() || undefined,
    createdAt: now,
    updatedAt: now,
  }
  item.shipment = shipment
  item.shipmentId = shipment.id
  next.updatedAt = now
  syncOrderStatus(next)
  return next
}

function assertFulfillable(order: AdminOrder, action: string): void {
  if (order.status === 'CANCELLED') throw new ApiError(3003, `已取消订单不能${action}`)
  if (order.status === 'COMPLETED') throw new ApiError(3003, `已完成订单不能${action}`)
}

function findSchedule(
  schedules: TechnicianSchedule[],
  technicianId: number,
  serviceDate: string,
  timeSlot: string,
): TechnicianSchedule | undefined {
  return schedules.find(
    (row) => row.technicianId === technicianId && row.serviceDate === serviceDate && row.timeSlot === timeSlot,
  )
}

function releaseSchedule(
  schedules: TechnicianSchedule[],
  technicianId?: number,
  serviceDate?: string,
  timeSlot?: string,
): void {
  if (!technicianId || !serviceDate || !timeSlot) return
  const row = findSchedule(schedules, technicianId, serviceDate, timeSlot)
  if (row && row.used > 0) row.used -= 1
}

function occupySchedule(
  schedules: TechnicianSchedule[],
  technicianId: number,
  serviceDate: string,
  timeSlot: string,
  ids: IdSource,
  hint?: { orderNo?: string; customerName?: string },
): void {
  let row = findSchedule(schedules, technicianId, serviceDate, timeSlot)
  if (!row) {
    row = {
      id: ids.next(),
      technicianId,
      serviceDate,
      timeSlot,
      capacity: 2,
      used: 0,
      orderNo: hint?.orderNo,
      customerName: hint?.customerName,
    }
    schedules.push(row)
  }
  if (row.used >= row.capacity) throw new ApiError(3002, '师傅档期冲突')
  row.used += 1
  if (!row.orderNo && hint?.orderNo) row.orderNo = hint.orderNo
  if (!row.customerName && hint?.customerName) row.customerName = hint.customerName
}

/**
 * 给上门安装行派师傅，不要求已经付款。换了人或时段会先释放旧档期再占新档期。
 * 这一行还没有发货记录时，补一条状态为已发出的安装发货，所以派工之后就不能再点「发货」。
 */
export function assignInstall(
  order: AdminOrder,
  schedules: TechnicianSchedule[],
  technicians: Technician[],
  req: AssignOrderRequest,
  now: string,
  ids: IdSource,
): { order: AdminOrder; schedules: TechnicianSchedule[] } {
  const technician = technicians.find((item) => item.id === req.technicianId)
  if (!technician) throw new ApiError(4040, '师傅不存在')
  if (technician.status === 'DISABLED') throw new ApiError(3003, '师傅已停用，不能派工')
  if (!req.installDate?.trim() || !req.installSlot?.trim()) {
    throw new ApiError(1001, '请填写安装日期和时段')
  }
  assertFulfillable(order, '派工')
  const next = cloneData(order)
  const nextSchedules = cloneData(schedules)
  const item = next.items.find((it) => it.id === req.orderItemId)
  if (!item) throw new ApiError(4040, '订单行不存在')
  if (item.deliveryMethod !== 'ON_SITE_INSTALL') throw new ApiError(3003, '仅上门安装行可派师傅')
  const sameSlot =
    item.technicianId === req.technicianId &&
    item.installDate === req.installDate &&
    item.installSlot === req.installSlot
  if (!sameSlot) {
    releaseSchedule(nextSchedules, item.technicianId, item.installDate, item.installSlot)
    occupySchedule(nextSchedules, req.technicianId, req.installDate, req.installSlot, ids, {
      orderNo: next.orderNo,
      customerName: next.customerName,
    })
  }
  item.technicianId = technician.id
  item.technicianName = technician.name
  item.installDate = req.installDate
  item.installSlot = req.installSlot
  if (!item.workOrderNo) {
    item.workOrderNo = `WO${req.installDate.replace(/-/g, '')}${String(item.id).slice(-4)}`
  }
  const appointAt = `${req.installDate} ${req.installSlot}`
  if (!hasActiveShipment(item)) {
    const shipmentId = ids.next()
    item.shipment = {
      id: shipmentId,
      orderId: next.id,
      orderItemId: item.id,
      method: 'ON_SITE_INSTALL',
      status: 'DISPATCHED',
      courierName: technician.name,
      courierMobile: technician.mobile,
      appointAt,
      remark: req.reason?.trim() || undefined,
      createdAt: now,
      updatedAt: now,
    }
    item.shipmentId = shipmentId
  } else if (item.shipment) {
    item.shipment.courierName = technician.name
    item.shipment.courierMobile = technician.mobile
    item.shipment.appointAt = appointAt
    item.shipment.updatedAt = now
  }
  if (req.reason?.trim()) {
    next.adminRemark = [next.adminRemark, `派工：${req.reason.trim()}`].filter(Boolean).join('\n')
  }
  next.updatedAt = now
  syncOrderStatus(next)
  return { order: next, schedules: nextSchedules }
}

export function cancelOrder(order: AdminOrder, req: CancelOrderRequest, now: string): AdminOrder {
  if (!req.reason?.trim()) throw new ApiError(1001, '请填写取消原因')
  if (order.status === 'CANCELLED') throw new ApiError(3003, '订单已取消')
  if (order.status === 'COMPLETED' || order.status === 'FULFILLING') {
    throw new ApiError(3003, '履约中或已完成的订单不能直接取消')
  }
  if (order.items.some(hasActiveShipment)) {
    throw new ApiError(3003, '已有发货或派工记录，不能直接取消')
  }
  if (order.payments.some((p) => p.status === 'SUCCESS')) {
    throw new ApiError(3003, '已有成功付款，取消需走退款（二期）')
  }
  const next = cloneData(order)
  for (const payment of next.payments) {
    if (payment.status === 'WAIT') {
      payment.status = 'CLOSED'
      payment.updatedAt = now
    }
  }
  next.status = 'CANCELLED'
  next.adminRemark = [next.adminRemark, `取消：${req.reason.trim()}`].filter(Boolean).join('\n')
  next.updatedAt = now
  next.paidAmount = paidAmount(next)
  return next
}
