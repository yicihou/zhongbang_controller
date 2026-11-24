/** 对齐后端 ApiResponse：code=0 成功，金额字段单位为分。 */
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
  traceId?: string
}

/** 对齐 PageResult：page 从 1 开始，列表字段名为 list。 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export type DeliveryMethod =
  | 'EXPRESS'
  | 'FREIGHT'
  | 'HOME_DELIVERY'
  | 'ON_SITE_INSTALL'
  | 'STORE_PICKUP'

export type PaymentChannel = 'OFFLINE' | 'WXPAY'
export type PaymentStatus = 'WAIT' | 'SUCCESS' | 'CLOSED' | 'REFUNDED'
export type PaymentType = 'FULL' | 'DEPOSIT' | 'BALANCE' | 'REPAIR' | 'FREIGHT'
export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'FULFILLING'
  | 'COMPLETED'
  | 'CANCELLED'
export type RepairStatus =
  | 'SUBMITTED'
  | 'QUOTED'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'CONFIRMED'
  | 'IN_SERVICE'
  | 'DONE'
  | 'CANCELLED'
export type ShipmentStatus = 'PENDING' | 'DISPATCHED' | 'DONE' | 'CANCELLED'
export type AssetSource = 'INTERNAL' | 'EXTERNAL'
export type StreamEventType = 'ORDER_CREATED' | 'REPAIR_CREATED' | 'PAYMENT_OFFLINE_PENDING'

export interface Shipment {
  id: number
  orderId: number
  orderItemId: number
  method: DeliveryMethod
  status: ShipmentStatus
  carrier?: string
  trackingNo?: string
  logisticsCompany?: string
  freightNo?: string
  vehicleNo?: string
  courierName?: string
  courierMobile?: string
  appointAt?: string
  pickupCode?: string
  pickupStoreId?: number
  pickedAt?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface OrderItem {
  id: number
  orderId: number
  skuId: number
  productName: string
  skuName: string
  coverImage?: string
  quantity: number
  unitPrice: number
  lineAmount: number
  deliveryMethod: DeliveryMethod
  /** null：上门安装行尚未由管理员定价（D3）；0 表示明确免安装费。 */
  installFee: number | null
  installDate?: string
  installSlot?: string
  technicianId?: number
  technicianName?: string
  addressSnapshotJson?: string
  shipmentId?: number
  shipment?: Shipment
  /** 展示用工单号，来源于 work_order.work_no */
  workOrderNo?: string
}

export interface Payment {
  id: number
  bizType: 'ORDER' | 'REPAIR'
  bizId: number
  paymentNo: string
  outTradeNo?: string
  channel: PaymentChannel
  type: PaymentType
  title: string
  amount: number
  status: PaymentStatus
  sortNo: number
  transactionId?: string
  voucherUrls?: string[]
  paidAt?: string
  operatorId?: number
  operatorName?: string
  remark?: string
  createdAt: string
  updatedAt: string
}

export interface PriceLog {
  id: number
  orderId: number
  scope: 'ORDER' | 'LINE'
  orderItemId?: number
  field: 'unitPrice' | 'installFee' | 'adjustAmount' | 'freightAmount'
  /** null：改价前该项未设定（如首次设定安装费）。 */
  beforeAmount: number | null
  afterAmount: number
  reason: string
  operatorName: string
  createdAt: string
}

export interface AdminOrder {
  id: number
  orderNo: string
  customerId: number
  customerName: string
  customerMobile: string
  storeId?: number
  status: OrderStatus
  goodsAmount: number
  installAmount: number
  freightAmount: number
  adjustAmount: number
  payableAmount: number
  paidAmount: number
  contactRemark?: string
  adminRemark?: string
  priceAdjusted: boolean
  addressText?: string
  createdAt: string
  updatedAt: string
  items: OrderItem[]
  payments: Payment[]
  priceLogs: PriceLog[]
}

export interface RepairLine {
  id: number
  ticketId: number
  lineNo: number
  assetId?: number
  brand: string
  model: string
  sn?: string
  source: AssetSource
  faultType: string
  faultDesc: string
  images: string[]
  purchaseProofUrl?: string
  purchaseChannel?: string
  lineQuoteAmount?: number
  workOrderId?: number
  technicianId?: number
  technicianName?: string
  appointAt?: string
}

export interface RepairTicket {
  id: number
  ticketNo: string
  customerId: number
  customerName: string
  status: RepairStatus
  expectDate?: string
  expectSlot?: string
  contactMobile: string
  remark?: string
  quoteAmount?: number
  payableAmount?: number
  paidAmount?: number
  inWarranty?: boolean
  createdAt: string
  updatedAt: string
  lines: RepairLine[]
  payments: Payment[]
}

export interface AdminNotice {
  id: number
  title: string
  body: string
  bizType: 'ORDER' | 'REPAIR' | 'PAYMENT'
  bizId: number
  eventType: StreamEventType
  readFlag: boolean
  createdAt: string
}

export interface Technician {
  id: number
  name: string
  mobile: string
  skills: string[]
  region: string
  rating: number
}

/** 对应 technician_schedule：一人一天一时段。 */
export interface TechnicianSchedule {
  id: number
  technicianId: number
  serviceDate: string
  timeSlot: string
  capacity: number
  used: number
  orderNo?: string
  customerName?: string
}

export interface Receivable {
  bizType: 'ORDER' | 'REPAIR'
  bizId: number
  bizNo: string
  customerName: string
  mobile: string
  paymentId: number
  title: string
  type: PaymentType
  amount: number
  status: PaymentStatus
  createdAt: string
}

export interface StaffBrief {
  id: number
  name: string
  type: 'STAFF'
  role: 'ROLE_STAFF'
}

export interface StaffLoginResponse {
  token: string
  expiresIn: number
  staff: StaffBrief
}

export interface AdjustPriceLine {
  orderItemId: number
  unitPrice?: number
  installFee?: number
}

export interface AdjustPriceRequest {
  adjustAmount?: number
  lines?: AdjustPriceLine[]
  reason: string
}

export interface FreightRequest {
  freightAmount: number
}

export interface PaymentDraft {
  type: PaymentType
  title: string
  amount: number
  channel?: PaymentChannel
}

export interface CreatePaymentsRequest {
  mode: 'SPLIT' | 'APPEND'
  payments: PaymentDraft[]
}

export interface PatchPaymentRequest {
  amount?: number
  title?: string
}

export interface OfflineConfirmRequest {
  voucherUrls?: string[]
  remark?: string
  paidAt?: string
}

export interface CreateShipmentRequest {
  orderItemId: number
  method?: DeliveryMethod
  carrier?: string
  trackingNo?: string
  logisticsCompany?: string
  freightNo?: string
  vehicleNo?: string
  courierName?: string
  courierMobile?: string
  appointAt?: string
  pickupCode?: string
  pickupStoreId?: number
  remark?: string
}

export interface AssignOrderRequest {
  orderItemId: number
  technicianId: number
  installDate: string
  installSlot: string
  reason?: string
}

export interface CancelOrderRequest {
  reason: string
}

export interface RepairQuoteLine {
  lineId: number
  lineQuoteAmount: number
}

export interface RepairQuoteRequest {
  inWarranty: boolean
  lines: RepairQuoteLine[]
  remark?: string
}

export interface RepairAssignRequest {
  lineId: number
  technicianId: number
  appointAt: string
}

export interface OrderQuery {
  status?: string
  keyword?: string
  page?: number
  size?: number
}

export interface RepairQuery {
  status?: string
  keyword?: string
  page?: number
  size?: number
}

export interface NoticeQuery {
  unread?: number | boolean
  page?: number
  size?: number
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface ScheduleQuery {
  from?: string
  to?: string
}
