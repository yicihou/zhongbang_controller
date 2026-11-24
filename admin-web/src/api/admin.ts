/**
 * 管理端每个按钮对应的函数。它们只拼路径和请求体，规则在 Mock 的 domain 或 Java 的 Service 里。
 * 金额字段单位是分。关掉 Mock 之后，这些路径打 Spring Boot。未带令牌是 401。
 */
import type {
  AdminNotice,
  AdminOrder,
  AdjustPriceRequest,
  AssignOrderRequest,
  CancelOrderRequest,
  CreatePaymentsRequest,
  CreateShipmentRequest,
  FreightRequest,
  NoticeQuery,
  OfflineConfirmRequest,
  OrderQuery,
  PageQuery,
  PageResult,
  PatchPaymentRequest,
  Payment,
  Receivable,
  RepairAssignRequest,
  RepairQuery,
  RepairQuoteRequest,
  RepairTicket,
  ScheduleQuery,
  StaffLoginResponse,
  Technician,
  TechnicianSchedule,
  AdminProduct,
  ChangeProductPriceRequest,
  SaveProductRequest,
} from './types'
import { paths } from './paths'
import { request } from './http'

export function staffLogin(username: string, password: string): Promise<StaffLoginResponse> {
  return request<StaffLoginResponse>('POST', paths.staffLogin, {
    auth: false,
    body: { username, password },
  })
}

export function listOrders(query: OrderQuery = {}): Promise<PageResult<AdminOrder>> {
  return request('GET', paths.orders, { query })
}

export function getOrder(id: number): Promise<AdminOrder> {
  return request('GET', paths.order(id))
}

export function adjustOrderPrice(id: number, body: AdjustPriceRequest): Promise<AdminOrder> {
  return request('POST', paths.adjustPrice(id), { body })
}

export function setOrderFreight(id: number, body: FreightRequest): Promise<AdminOrder> {
  return request('POST', paths.freight(id), { body })
}

export function createOrderPayments(id: number, body: CreatePaymentsRequest): Promise<AdminOrder> {
  return request('POST', paths.orderPayments(id), { body })
}

export function updatePayment(id: number, body: PatchPaymentRequest): Promise<Payment> {
  return request('PATCH', paths.payment(id), { body })
}

export function offlineConfirm(id: number, body: OfflineConfirmRequest): Promise<Payment> {
  return request('POST', paths.offlineConfirm(id), { body })
}

export function createShipment(id: number, body: CreateShipmentRequest): Promise<AdminOrder> {
  return request('POST', paths.shipments(id), { body })
}

export function assignOrder(id: number, body: AssignOrderRequest): Promise<AdminOrder> {
  return request('POST', paths.assignOrder(id), { body })
}

export function cancelOrder(id: number, body: CancelOrderRequest): Promise<AdminOrder> {
  return request('POST', paths.cancelOrder(id), { body })
}

export function listRepairs(query: RepairQuery = {}): Promise<PageResult<RepairTicket>> {
  return request('GET', paths.repairs, { query })
}

export function getRepair(id: number): Promise<RepairTicket> {
  return request('GET', paths.repair(id))
}

export function quoteRepair(id: number, body: RepairQuoteRequest): Promise<RepairTicket> {
  return request('POST', paths.repairQuote(id), { body })
}

export function assignRepair(id: number, body: RepairAssignRequest): Promise<RepairTicket> {
  return request('POST', paths.repairAssign(id), { body })
}

export function completeRepair(id: number): Promise<RepairTicket> {
  return request('POST', paths.repairComplete(id))
}

export function cancelRepair(id: number, body: CancelOrderRequest): Promise<RepairTicket> {
  return request('POST', paths.repairCancel(id), { body })
}

export function listNotices(query: NoticeQuery = {}): Promise<PageResult<AdminNotice>> {
  return request('GET', paths.notices, { query })
}

export function listReceivables(query: PageQuery = {}): Promise<PageResult<Receivable>> {
  return request('GET', paths.receivables, { query })
}

export function listTechnicians(): Promise<Technician[]> {
  return request('GET', paths.technicians)
}

export function listSchedules(query: ScheduleQuery = {}): Promise<TechnicianSchedule[]> {
  return request('GET', paths.schedules, { query })
}

export function listProducts(query: { keyword?: string; status?: string; page?: number; size?: number } = {}): Promise<PageResult<AdminProduct>> {
  return request('GET', paths.products, { query })
}

export function createProduct(body: SaveProductRequest): Promise<AdminProduct> {
  return request('POST', paths.products, { body })
}

export function updateProduct(id: number, body: SaveProductRequest): Promise<AdminProduct> {
  return request('PUT', paths.product(id), { body })
}

export function deleteProduct(id: number): Promise<void> {
  return request('DELETE', paths.product(id))
}

export function shelveProduct(id: number, on: boolean): Promise<AdminProduct> {
  return request('POST', on ? paths.productOn(id) : paths.productOff(id))
}

export function changeProductPrice(id: number, body: ChangeProductPriceRequest): Promise<AdminProduct> {
  return request('POST', paths.productPrice(id), { body })
}
