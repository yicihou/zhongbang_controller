/**
 * Mock 的「后端」：按方法和路径分发，返回 {status, body}，body 外形与 ApiResponse 相同。
 * http.ts 在 USE_MOCK 时调用 handle。不访问网络，不读 MySQL。
 * 登录账号写在 mock/db.ts。除登录外都要未过期的 STAFF 令牌，否则业务码 2001 或 2003。
 * 订单和报修的规则在 domain/order.ts、domain/repair.ts，这里只负责查找、保存和套上响应壳。
 */
import type {
  AdminProduct,
  AdjustPriceRequest,
  ApiResponse,
  AssignOrderRequest,
  CancelOrderRequest,
  CreatePaymentsRequest,
  CreateShipmentRequest,
  FreightRequest,
  OfflineConfirmRequest,
  PatchPaymentRequest,
  Receivable,
  RepairAssignRequest,
  RepairQuoteRequest,
  StaffLoginResponse,
} from '@/api/types'
import { formatDateTime } from '@/domain/clock'
import { ApiError, httpStatusFor } from '@/domain/errors'
import { imageFileError } from '@/domain/image'
import { isBossRole } from '@/domain/customer'
import { decodeJwtPayload, isExpired, issueMockStaffToken } from '@/domain/jwt'
import {
  addShipment,
  adjustPrice,
  assignInstall,
  cancelOrder,
  createPayments,
  offlineConfirmOrder,
  patchOrderPayment,
  patchPayment,
  setFreight,
  type IdSource,
} from '@/domain/order'
import { paginate } from '@/domain/page'
import { assignRepair, cancelRepair, completeRepair, offlineConfirmRepair, quoteRepair } from '@/domain/repair'
import {
  changeStaffStatus,
  createStaff,
  listStaff,
  resetStaffPassword,
  updateStaff,
  type StaffSave,
} from '@/domain/staff'
import { dispatchCustomer } from './customers'
import { db } from './db'

export interface MockResult {
  status: number
  body: ApiResponse<unknown>
}

function traceId(): string {
  return Math.random().toString(16).slice(2, 8)
}

function ok(data: unknown): MockResult {
  return { status: 200, body: { code: 0, msg: 'ok', data, traceId: traceId() } }
}

function fromError(error: ApiError): MockResult {
  return {
    status: httpStatusFor(error.code),
    body: { code: error.code, msg: error.message, data: null, traceId: traceId() },
  }
}

function ids(): IdSource {
  return { next: () => ++db.seq }
}

function asRecord(body: unknown): Record<string, unknown> {
  return body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
}

/** 只解码 JWT，不校验签名。Mock 令牌的签名段是字面量 mock，真实后端不会承认它。 */
function requireStaff(token: string): { id: number; name: string } {
  const payload = decodeJwtPayload(token)
  if (!payload || isExpired(payload, Date.now() / 1000)) {
    throw new ApiError(2001, '未登录或 token 已过期')
  }
  const staff = payload.type === 'STAFF' || payload.role === 'ROLE_STAFF' || payload.role === 'STAFF'
  if (!staff) throw new ApiError(2003, '无权限')
  return { id: Number(payload.sub) || 0, name: payload.name || '员工' }
}

/** 员工管理只允许老板。其他后台接口仍然只要 STAFF。派工用的师傅名单不走这里。 */
function requireBoss(token: string): { id: number; name: string } {
  const staff = requireStaff(token)
  const payload = decodeJwtPayload(token)
  if (payload?.role !== 'ROLE_BOSS' && payload?.role !== 'BOSS') {
    throw new ApiError(2003, '只有老板可以管理员工')
  }
  return staff
}

function num(value: string | undefined, fallback?: number): number | undefined {
  if (value == null || value === '') return fallback
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function findOrder(id: number) {
  const order = db.orders.find((item) => item.id === id)
  if (!order) throw new ApiError(4040, '订单不存在')
  return order
}

function findRepair(id: number) {
  const ticket = db.repairs.find((item) => item.id === id)
  if (!ticket) throw new ApiError(4040, '报修单不存在')
  return ticket
}

function saveOrder(orderId: number, next: (typeof db.orders)[number]): void {
  const index = db.orders.findIndex((item) => item.id === orderId)
  if (index >= 0) db.orders[index] = next
}

function saveRepair(ticketId: number, next: (typeof db.repairs)[number]): void {
  const index = db.repairs.findIndex((item) => item.id === ticketId)
  if (index >= 0) db.repairs[index] = next
}

function login(body: unknown): StaffLoginResponse {
  const record = asRecord(body)
  const username = String(record.username ?? '')
  const password = String(record.password ?? '')
  const user = db.staff.find((item) => item.username === username && item.password === password)
  if (!user) throw new ApiError(2001, '账号或密码错误')
  if (user.status === 'DISABLED') throw new ApiError(2003, '账号已停用')
  const roleName = user.role || 'STAFF'
  const role = roleName.startsWith('ROLE_') ? roleName : `ROLE_${roleName}`
  return {
    token: issueMockStaffToken({ id: user.id, name: user.name, role: roleName }),
    expiresIn: 7 * 24 * 3600,
    staff: { id: user.id, name: user.name, type: 'STAFF', role },
  }
}

function listOrders(query: Record<string, string>) {
  const status = query.status?.trim()
  const keyword = query.keyword?.trim().toLowerCase()
  const filtered = db.orders.filter((order) => {
    if (status && order.status !== status) return false
    if (!keyword) return true
    return [order.orderNo, order.customerName, order.customerMobile].some((field) =>
      field.toLowerCase().includes(keyword),
    )
  })
  return paginate(filtered, num(query.page, 1), num(query.size, 20))
}

function listRepairs(query: Record<string, string>) {
  const status = query.status?.trim()
  const keyword = query.keyword?.trim().toLowerCase()
  const filtered = db.repairs.filter((ticket) => {
    if (status && ticket.status !== status) return false
    if (!keyword) return true
    const haystack = [
      ticket.ticketNo,
      ticket.customerName,
      ticket.contactMobile,
      ...ticket.lines.flatMap((line) => [line.brand, line.model, line.sn ?? '']),
    ]
    return haystack.some((field) => field.toLowerCase().includes(keyword))
  })
  return paginate(filtered, num(query.page, 1), num(query.size, 20))
}

function listNotices(query: Record<string, string>, token: string) {
  const unread = query.unread === '1' || query.unread === 'true'
  const boss = isBossRole(decodeJwtPayload(token)?.role)
  const filtered = db.notices.filter((notice) => {
    if (!boss && notice.audience === 'BOSS') return false
    return unread ? !notice.readFlag : true
  })
  return paginate(filtered, num(query.page, 1), num(query.size, 20))
}

function listReceivables(query: Record<string, string>) {
  const rows: Receivable[] = []
  for (const order of db.orders) {
    for (const payment of order.payments) {
      if (payment.status !== 'WAIT') continue
      rows.push({
        bizType: 'ORDER',
        bizId: order.id,
        bizNo: order.orderNo,
        customerName: order.customerName,
        mobile: order.customerMobile,
        paymentId: payment.id,
        title: payment.title,
        type: payment.type,
        amount: payment.amount,
        status: payment.status,
        createdAt: payment.createdAt,
      })
    }
  }
  for (const ticket of db.repairs) {
    for (const payment of ticket.payments) {
      if (payment.status !== 'WAIT') continue
      rows.push({
        bizType: 'REPAIR',
        bizId: ticket.id,
        bizNo: ticket.ticketNo,
        customerName: ticket.customerName,
        mobile: ticket.contactMobile,
        paymentId: payment.id,
        title: payment.title,
        type: payment.type,
        amount: payment.amount,
        status: payment.status,
        createdAt: payment.createdAt,
      })
    }
  }
  rows.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  return paginate(rows, num(query.page, 1), num(query.size, 20))
}

function listSchedules(query: Record<string, string>) {
  const from = query.from
  const to = query.to
  return db.schedules
    .filter((row) => {
      if (from && row.serviceDate < from) return false
      if (to && row.serviceDate > to) return false
      return true
    })
    .slice()
    .sort((a, b) => a.serviceDate.localeCompare(b.serviceDate) || a.timeSlot.localeCompare(b.timeSlot, 'zh'))
}

function summarizeProduct(product: AdminProduct): AdminProduct {
  const price = product.skus.reduce((min, sku) => Math.min(min, sku.price), product.skus[0]?.price ?? 0)
  const stock = product.skus.reduce((sum, sku) => sum + sku.stock, 0)
  return { ...product, price, stock }
}

/** Mock 里的商品管理。规则很短：至少一条规格，价格和库存不能为负，下架只改 status。 */
function handleProduct(method: string, path: string, query: Record<string, string>, body: unknown): unknown | undefined {
  if (path !== '/api/admin/products' && !path.startsWith('/api/admin/products/')) return undefined
  const now = formatDateTime()
  if (method === 'GET' && path === '/api/admin/products') {
    const keyword = query.keyword?.trim()
    const status = query.status?.trim()
    const rows = db.products.filter((item) => {
      if (status && item.status !== status) return false
      if (!keyword) return true
      return item.name.includes(keyword) || (item.model ?? '').includes(keyword)
    })
    return paginate(rows.map(summarizeProduct), num(query.page, 1) ?? 1, num(query.size, 20) ?? 20)
  }
  const matched = path.match(/^\/api\/admin\/products\/(\d+)(?:\/([a-z-]+))?$/)
  if (!matched && method === 'POST' && path === '/api/admin/products') {
    return saveProduct(undefined, asRecord(body), now)
  }
  if (!matched) return undefined
  const id = Number(matched[1])
  const action = matched[2]
  const index = db.products.findIndex((item) => item.id === id)
  if (index < 0) throw new ApiError(4040, '商品不存在')
  const current = db.products[index]
  if (!current) throw new ApiError(4040, '商品不存在')
  if (!action && method === 'GET') return summarizeProduct(current)
  if (!action && method === 'PUT') return saveProduct(id, asRecord(body), now)
  if (!action && method === 'DELETE') {
    db.products.splice(index, 1)
    return null
  }
  if (action === 'on-shelf' && method === 'POST') {
    current.status = 'ON'
    current.updatedAt = now
    return summarizeProduct(current)
  }
  if (action === 'off-shelf' && method === 'POST') {
    current.status = 'OFF'
    current.updatedAt = now
    return summarizeProduct(current)
  }
  if (action === 'price' && method === 'POST') {
    const record = asRecord(body)
    const price = Number(record.price)
    if (!Number.isInteger(price) || price < 0) throw new ApiError(1001, '价格不能为负')
    const skuId = record.skuId == null ? current.skus[0]?.id : Number(record.skuId)
    const sku = current.skus.find((item) => item.id === skuId)
    if (!sku) throw new ApiError(4040, '商品规格不存在')
    sku.price = price
    current.updatedAt = now
    return summarizeProduct(current)
  }
  return undefined
}

function saveProduct(id: number | undefined, record: Record<string, unknown>, now: string): AdminProduct {
  const name = String(record.name ?? '').trim()
  if (!name) throw new ApiError(1001, '请填写商品名称')
  const rawSkus = Array.isArray(record.skus) ? record.skus : []
  if (id == null && rawSkus.length === 0) throw new ApiError(1001, '请至少填写一条规格')
  const skus = rawSkus.map((item) => {
    const row = asRecord(item)
    const title = String(row.title ?? '').trim()
    const price = Number(row.price)
    const stock = Number(row.stock)
    if (!title) throw new ApiError(1001, '请填写规格名称')
    if (!Number.isInteger(price) || price < 0) throw new ApiError(1001, '价格不能为负')
    if (!Number.isInteger(stock) || stock < 0) throw new ApiError(1001, '库存不能为负')
    return { id: Number(row.id) || ids().next(), title, price, stock }
  })
  const status = record.status === 'OFF' ? 'OFF' : 'ON'
  if (id == null) {
    const created = summarizeProduct({
      id: ids().next(),
      name,
      brand: String(record.brand ?? ''),
      category: String(record.category ?? ''),
      categoryId: record.categoryId == null ? undefined : Number(record.categoryId),
      model: String(record.model ?? ''),
      subtitle: String(record.subtitle ?? ''),
      coverImage: String(record.coverImage ?? ''),
      tags: String(record.tags ?? ''),
      status,
      price: 0,
      stock: 0,
      createdAt: now,
      updatedAt: now,
      skus,
    })
    db.products.unshift(created)
    return created
  }
  const current = db.products.find((item) => item.id === id)
  if (!current) throw new ApiError(4040, '商品不存在')
  current.name = name
  current.brand = String(record.brand ?? '')
  current.category = String(record.category ?? current.category ?? '')
  current.categoryId = record.categoryId == null ? current.categoryId : Number(record.categoryId)
  current.model = String(record.model ?? '')
  current.subtitle = String(record.subtitle ?? '')
  current.coverImage = String(record.coverImage ?? '')
  current.tags = String(record.tags ?? '')
  current.status = status
  if (skus.length) current.skus = skus
  current.updatedAt = now
  return summarizeProduct(current)
}

function staffStore() {
  return {
    users: db.staff,
    technicians: db.technicians,
    nextId: () => ++db.seq,
  }
}

function handleStaff(
  method: string,
  path: string,
  query: Record<string, string>,
  body: unknown,
  token: string,
): unknown | undefined {
  if (path !== '/api/admin/staff' && !path.startsWith('/api/admin/staff/')) return undefined
  const operator = requireBoss(token)
  if (method === 'GET' && path === '/api/admin/staff') {
    return listStaff(db.staff, db.technicians, {
      keyword: query.keyword,
      role: query.role,
      status: query.status,
      page: num(query.page, 1),
      size: num(query.size, 20),
    })
  }
  if (method === 'POST' && path === '/api/admin/staff') {
    return createStaff(staffStore(), asRecord(body) as StaffSave)
  }
  const match = path.match(/^\/api\/admin\/staff\/(user-\d+|tech-\d+)(?:\/(status|password))?$/)
  if (!match) return undefined
  const ref = match[1]
  const action = match[2]
  const record = asRecord(body)
  if (!action && method === 'PUT') return updateStaff(staffStore(), ref, record as StaffSave, operator.id)
  if (action === 'status' && method === 'POST') {
    return changeStaffStatus(staffStore(), ref, String(record.status ?? ''), operator.id)
  }
  if (action === 'password' && method === 'POST') {
    resetStaffPassword(staffStore(), ref, String(record.password ?? ''))
    return null
  }
  return undefined
}

/** Mock 不写磁盘。页面把文件读成 data URL 传来。协议材料还可以是录音和文档。 */
function mockUpload(body: unknown, biz?: string): unknown {
  const files = asRecord(body).files
  if (!Array.isArray(files) || files.length === 0) throw new ApiError(1001, '请选择图片')
  if (files.length > 9) throw new ApiError(1001, '一次最多上传 9 张')
  const agreement = biz === 'agreement'
  return files.map((item) => {
    const file = asRecord(item)
    const name = String(file.name ?? '')
    const type = String(file.type ?? '')
    const size = Number(file.size ?? 0)
    const dataUrl = String(file.dataUrl ?? '')
    if (!agreement) {
      const error = imageFileError(name, type, size || dataUrl.length)
      if (error) throw new ApiError(1001, error)
      const url = dataUrl.startsWith('data:image/') ? dataUrl : `/uploads/mock/${encodeURIComponent(name)}`
      return { url, name, size: size || dataUrl.length, contentType: type || 'image/jpeg', kind: 'IMAGE' }
    }
    const kind = agreementKind(name, size)
    const url = dataUrl || `/uploads/mock/${encodeURIComponent(name)}`
    return { url, name, size, contentType: type || 'application/octet-stream', kind }
  })
}

function agreementKind(name: string, size: number): string {
  const lower = name.toLowerCase()
  if (/\.(jpe?g|png|webp)$/.test(lower)) {
    if (size > 10 * 1024 * 1024) throw new ApiError(1001, '图片不能超过 10MB')
    return 'IMAGE'
  }
  if (/\.(mp3|m4a|wav|amr)$/.test(lower)) {
    if (size > 30 * 1024 * 1024) throw new ApiError(1001, '录音不能超过 30MB')
    return 'AUDIO'
  }
  if (/\.(pdf|docx?|xlsx?)$/.test(lower)) {
    if (size > 30 * 1024 * 1024) throw new ApiError(1001, '文档不能超过 30MB')
    return 'DOCUMENT'
  }
  throw new ApiError(1001, '协议材料只支持图片、录音（mp3/m4a/wav/amr）和文档（pdf/doc/docx/xls/xlsx）')
}

function dispatch(method: string, path: string, query: Record<string, string>, body: unknown, token: string): unknown {
  if (method === 'POST' && path === '/api/auth/staff/login') return login(body)

  const operator = requireStaff(token)
  const productHit = handleProduct(method, path, query, body)
  if (productHit !== undefined) return productHit
  const now = formatDateTime()
  const customerHit = dispatchCustomer(method, path, query, body, {
    name: operator.name,
    boss: isBossRole(decodeJwtPayload(token)?.role),
  })
  if (customerHit !== undefined) return customerHit

  if (method === 'GET' && path === '/api/admin/orders') return listOrders(query)
  if (method === 'GET' && path === '/api/admin/repairs') return listRepairs(query)
  if (method === 'GET' && path === '/api/admin/notices') return listNotices(query, token)
  if (method === 'GET' && path === '/api/admin/receivables') return listReceivables(query)
  if (method === 'POST' && path === '/api/admin/files') {
    if (query.biz === 'agreement' && !isBossRole(decodeJwtPayload(token)?.role)) {
      throw new ApiError(2003, '只有老板可以上传协议材料')
    }
    return mockUpload(body, query.biz)
  }
  const staffHit = handleStaff(method, path, query, body, token)
  if (staffHit !== undefined) return staffHit
  if (method === 'GET' && path === '/api/admin/technicians') {
    return db.technicians.filter((item) => item.status !== 'DISABLED')
  }
  if (method === 'GET' && path === '/api/admin/technicians/schedules') return listSchedules(query)

  const orderMatch = path.match(/^\/api\/admin\/orders\/(\d+)(?:\/([a-z-]+))?$/)
  if (orderMatch) {
    const orderId = Number(orderMatch[1])
    const action = orderMatch[2]
    const order = findOrder(orderId)
    if (!action && method === 'GET') return order
    if (action === 'adjust-price' && method === 'POST') {
      const next = adjustPrice(order, body as AdjustPriceRequest, operator.name, now, ids())
      saveOrder(orderId, next)
      return next
    }
    if (action === 'freight' && method === 'POST') {
      const next = setFreight(order, body as FreightRequest, operator.name, now, ids())
      saveOrder(orderId, next)
      return next
    }
    if (action === 'payments' && method === 'POST') {
      const next = createPayments(order, body as CreatePaymentsRequest, now, ids())
      saveOrder(orderId, next)
      return next
    }
    if (action === 'shipments' && method === 'POST') {
      const next = addShipment(order, body as CreateShipmentRequest, now, ids())
      saveOrder(orderId, next)
      return next
    }
    if (action === 'assign' && method === 'POST') {
      const result = assignInstall(order, db.schedules, db.technicians, body as AssignOrderRequest, now, ids())
      db.schedules = result.schedules
      saveOrder(orderId, result.order)
      return result.order
    }
    if (action === 'cancel' && method === 'POST') {
      const next = cancelOrder(order, body as CancelOrderRequest, now)
      saveOrder(orderId, next)
      return next
    }
  }

  const repairMatch = path.match(/^\/api\/admin\/repairs\/(\d+)(?:\/([a-z-]+))?$/)
  if (repairMatch) {
    const ticketId = Number(repairMatch[1])
    const action = repairMatch[2]
    const ticket = findRepair(ticketId)
    if (!action && method === 'GET') return ticket
    if (action === 'quote' && method === 'POST') {
      const next = quoteRepair(ticket, body as RepairQuoteRequest, now, ids())
      saveRepair(ticketId, next)
      return next
    }
    if (action === 'assign' && method === 'POST') {
      const result = assignRepair(ticket, db.schedules, db.technicians, body as RepairAssignRequest, now, ids())
      db.schedules = result.schedules
      saveRepair(ticketId, result.ticket)
      return result.ticket
    }
    if (action === 'complete' && method === 'POST') {
      const next = completeRepair(ticket, now)
      saveRepair(ticketId, next)
      return next
    }
    if (action === 'cancel' && method === 'POST') {
      const next = cancelRepair(ticket, body as CancelOrderRequest, now)
      saveRepair(ticketId, next)
      return next
    }
  }

  const paymentMatch = path.match(/^\/api\/admin\/payments\/(\d+)(?:\/([a-z-]+))?$/)
  if (paymentMatch) {
    const paymentId = Number(paymentMatch[1])
    const action = paymentMatch[2]
    const order = db.orders.find((item) => item.payments.some((payment) => payment.id === paymentId))
    const ticket = db.repairs.find((item) => item.payments.some((payment) => payment.id === paymentId))
    if (!order && !ticket) throw new ApiError(4040, '应付单不存在')
    if (!action && method === 'PATCH') {
      if (order) {
        const next = patchOrderPayment(order, paymentId, body as PatchPaymentRequest, now)
        saveOrder(order.id, next)
        return next.payments.find((payment) => payment.id === paymentId)
      }
      const payment = ticket?.payments.find((item) => item.id === paymentId)
      if (!payment || !ticket) throw new ApiError(4040, '应付单不存在')
      patchPayment(payment, body as PatchPaymentRequest, now)
      ticket.updatedAt = now
      return payment
    }
    if (action === 'offline-confirm' && method === 'POST') {
      const req = body as OfflineConfirmRequest
      if (order) {
        const next = offlineConfirmOrder(order, paymentId, req, operator, now, ids())
        saveOrder(order.id, next)
        return next.payments.find((payment) => payment.id === paymentId)
      }
      const next = offlineConfirmRepair(ticket!, paymentId, req, operator, now, ids())
      saveRepair(ticket!.id, next)
      return next.payments.find((payment) => payment.id === paymentId)
    }
  }

  throw new ApiError(4040, '接口不存在')
}

export function handle(
  method: string,
  path: string,
  query: Record<string, string>,
  body: unknown,
  token: string,
): MockResult {
  try {
    return ok(dispatch(method.toUpperCase(), path, query, body, token))
  } catch (error) {
    if (error instanceof ApiError) return fromError(error)
    return {
      status: 500,
      body: { code: 5000, msg: '服务内部错误', data: null, traceId: traceId() },
    }
  }
}
