/**
 * Mock 的「后端」：按方法和路径分发，返回 {status, body}，body 外形与 ApiResponse 相同。
 * http.ts 在 USE_MOCK 时调用 handle。不访问网络，不读 MySQL。
 * 登录账号写在 mock/db.ts。除登录外都要未过期的 STAFF 令牌，否则业务码 2001 或 2003。
 * 订单和报修的规则在 domain/order.ts、domain/repair.ts，这里只负责查找、保存和套上响应壳。
 */
import type {
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
import { assignRepair, offlineConfirmRepair, quoteRepair } from '@/domain/repair'
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
  return {
    token: issueMockStaffToken(user),
    expiresIn: 7 * 24 * 3600,
    staff: { id: user.id, name: user.name, type: 'STAFF', role: 'ROLE_STAFF' },
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

function listNotices(query: Record<string, string>) {
  const unread = query.unread === '1' || query.unread === 'true'
  const filtered = db.notices.filter((notice) => (unread ? !notice.readFlag : true))
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

function dispatch(method: string, path: string, query: Record<string, string>, body: unknown, token: string): unknown {
  if (method === 'POST' && path === '/api/auth/staff/login') return login(body)

  const operator = requireStaff(token)
  const now = formatDateTime()

  if (method === 'GET' && path === '/api/admin/orders') return listOrders(query)
  if (method === 'GET' && path === '/api/admin/repairs') return listRepairs(query)
  if (method === 'GET' && path === '/api/admin/notices') return listNotices(query)
  if (method === 'GET' && path === '/api/admin/receivables') return listReceivables(query)
  if (method === 'GET' && path === '/api/admin/technicians') return db.technicians
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
        const next = offlineConfirmOrder(order, paymentId, req, operator.name, operator.id, now)
        saveOrder(order.id, next)
        return next.payments.find((payment) => payment.id === paymentId)
      }
      const next = offlineConfirmRepair(ticket!, paymentId, req, operator.name, operator.id, now)
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
