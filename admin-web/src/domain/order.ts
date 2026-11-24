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
  PatchPaymentRequest,
  Payment,
  PaymentDraft,
  PriceLog,
  Shipment,
  Technician,
  TechnicianSchedule,
} from '@/api/types'
import { ApiError } from './errors'

export interface IdSource {
  next(): number
}

export function cloneData<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function paidAmount(order: AdminOrder): number {
  return order.payments.filter((p) => p.status === 'SUCCESS').reduce((sum, p) => sum + p.amount, 0)
}

function fulfillmentStarted(order: AdminOrder): boolean {
  return order.items.some((item) => {
    if (item.shipment && item.shipment.status !== 'CANCELLED' && item.shipment.status !== 'PENDING') return true
    return item.deliveryMethod === 'ON_SITE_INSTALL' && !!item.technicianId && !!item.workOrderNo
  })
}

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

export function syncOrderStatus(order: AdminOrder): void {
  const paid = paidAmount(order)
  order.paidAmount = paid
  if (order.status === 'CANCELLED' || order.status === 'COMPLETED') return
  const waits = order.payments.filter((p) => p.status === 'WAIT')
  if (waits.length === 0 && paid >= order.payableAmount) {
    order.status = fulfillmentStarted(order) ? 'FULFILLING' : 'PAID'
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

export function confirmOffline(
  payment: Payment,
  req: OfflineConfirmRequest,
  operator: string,
  operatorId: number,
  now: string,
): void {
  if (payment.status !== 'WAIT') throw new ApiError(3003, '该应付单不是待付状态')
  const vouchers = (req.voucherUrls ?? []).map((item) => item.trim()).filter(Boolean)
  const remark = req.remark?.trim() ?? ''
  if (vouchers.length === 0 && !remark) throw new ApiError(1001, '请填写收款凭证')
  payment.status = 'SUCCESS'
  payment.channel = 'OFFLINE'
  payment.voucherUrls = vouchers.length > 0 ? vouchers : [remark]
  payment.remark = remark || undefined
  payment.paidAt = req.paidAt?.trim() || now
  payment.operatorId = operatorId
  payment.operatorName = operator
  payment.updatedAt = now
}

export function offlineConfirmOrder(
  order: AdminOrder,
  paymentId: number,
  req: OfflineConfirmRequest,
  operator: string,
  operatorId: number,
  now: string,
): AdminOrder {
  if (order.status === 'CANCELLED') throw new ApiError(3003, '已取消订单不能登记收款')
  const next = cloneData(order)
  const payment = next.payments.find((p) => p.id === paymentId)
  if (!payment) throw new ApiError(4040, '应付单不存在')
  confirmOffline(payment, req, operator, operatorId, now)
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

export function addShipment(
  order: AdminOrder,
  req: CreateShipmentRequest,
  now: string,
  ids: IdSource,
): AdminOrder {
  if (order.status !== 'PAID' && order.status !== 'FULFILLING') {
    throw new ApiError(3003, '付款结清后才能发货')
  }
  const next = cloneData(order)
  const item = next.items.find((it) => it.id === req.orderItemId)
  if (!item) throw new ApiError(4040, '订单行不存在')
  if (req.method && req.method !== item.deliveryMethod) {
    throw new ApiError(3004, '发货方式与订单行不一致')
  }
  if (item.shipment && item.shipment.status !== 'CANCELLED') {
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
  next.status = 'FULFILLING'
  next.updatedAt = now
  return next
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
  if (!req.installDate?.trim() || !req.installSlot?.trim()) {
    throw new ApiError(1001, '请填写安装日期和时段')
  }
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
  if (next.status === 'PAID' || next.status === 'FULFILLING') {
    const appointAt = `${req.installDate} ${req.installSlot}`
    if (!item.shipment || item.shipment.status === 'CANCELLED') {
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
    } else {
      item.shipment.courierName = technician.name
      item.shipment.courierMobile = technician.mobile
      item.shipment.appointAt = appointAt
      item.shipment.updatedAt = now
    }
    next.status = 'FULFILLING'
  }
  if (req.reason?.trim()) {
    next.adminRemark = [next.adminRemark, `派工：${req.reason.trim()}`].filter(Boolean).join('\n')
  }
  next.updatedAt = now
  return { order: next, schedules: nextSchedules }
}

export function cancelOrder(order: AdminOrder, req: CancelOrderRequest, now: string): AdminOrder {
  if (!req.reason?.trim()) throw new ApiError(1001, '请填写取消原因')
  if (order.status === 'CANCELLED') throw new ApiError(3003, '订单已取消')
  if (order.status === 'COMPLETED' || order.status === 'FULFILLING') {
    throw new ApiError(3003, '履约中或已完成的订单不能直接取消')
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
