/**
 * 和页面、Mock 约定的数据结构。金额字段单位是分。
 * 成功响应外形对齐后端 ApiResponse（code=0）。
 * 订单行、运费、报修这些类型描述的是管理端 Mock 和 docs/04，不是 V1 表的列。
 * V1 的 orders 表还是总额/定金/尾款，没有这里的 goodsAmount、items。
 */
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
/** 线下收款方式。不是支付渠道；渠道只有 OFFLINE 和预留的 WXPAY。 */
export type OfflinePayMethod = 'BANK_TRANSFER' | 'WECHAT_TRANSFER' | 'ALIPAY' | 'CASH' | 'OTHER'
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
export type StreamEventType =
  | 'ORDER_CREATED'
  | 'REPAIR_CREATED'
  | 'PAYMENT_OFFLINE_PENDING'
  | 'REPAY_SOON'
  | 'REPAY_TODAY'
  | 'REPAY_OVERDUE'
  | 'STOCK_LOW'

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
  /** 只有老板能看到。未知成本时不出现。 */
  costPrice?: number
  costAmount?: number
  costKnown?: boolean
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
  /** 线下收款方式，channel=OFFLINE 且已收时有值 */
  offlineMethod?: OfflinePayMethod
  paidAt?: string
  registeredAt?: string
  operatorId?: number
  operatorName?: string
  remark?: string
  /** 部分收款时由哪笔应付拆出的剩余待付 */
  splitFromId?: number
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
  invoiced?: boolean
  invoiceType?: string
  taxRate?: number
  taxIncluded?: boolean
  goodsCost?: number
  grossProfit?: number
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
  invoiced?: boolean
  invoiceType?: string
  taxRate?: number
  taxIncluded?: boolean
  createdAt: string
  updatedAt: string
  lines: RepairLine[]
  payments: Payment[]
}

export interface AdminNotice {
  id: number
  title: string
  body: string
  bizType: 'ORDER' | 'REPAIR' | 'PAYMENT' | 'CUSTOMER' | 'STOCK'
  bizId: number
  eventType: StreamEventType
  readFlag: boolean
  createdAt: string
  /** ALL 所有员工可见。BOSS 只有老板能在通知里看到，例如协议还款。 */
  audience?: 'ALL' | 'BOSS'
}

export interface Technician {
  id: number
  name: string
  mobile: string
  skills: string[]
  region: string
  rating: number
  /** 停用后不能再派工。没写时当成启用，方便旧的测试数据。 */
  status?: 'ENABLED' | 'DISABLED'
  remark?: string
  userId?: number
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
  /** ROLE_ADMIN、ROLE_SALES、ROLE_TECHNICIAN，或旧令牌里的 ROLE_STAFF。 */
  role: string
}

export interface UploadedImage {
  url: string
  name: string
  size: number
  contentType?: string
  kind?: 'IMAGE' | 'AUDIO' | 'DOCUMENT' | string
}

export type ProductStatus = 'ON' | 'OFF'

/** 管理端商品。price、sku.price 的单位是分。coverImage 是图片地址。 */
export interface AdminProductSku {
  id: number
  title: string
  price: number
  stock: number
  /** 进价。非老板的接口里没有这个字段。 */
  costPrice?: number
  minStock?: number
}

export interface AdminProduct {
  id: number
  name: string
  brand?: string
  category?: string
  categoryId?: number
  model?: string
  subtitle?: string
  coverImage?: string
  tags?: string
  status: ProductStatus
  price: number
  stock: number
  createdAt: string
  updatedAt: string
  skus: AdminProductSku[]
}

export interface ProductSkuDraft {
  id?: number
  title: string
  price: number
  stock: number
}

export interface SaveProductRequest {
  name: string
  brand?: string
  category?: string
  categoryId?: number
  model?: string
  subtitle?: string
  coverImage?: string
  tags?: string
  status?: ProductStatus
  skus?: ProductSkuDraft[]
}

export interface ChangeProductPriceRequest {
  skuId?: number
  price: number
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
  /** 本次收款金额（分）；不传按该应付单全额。小于全额时剩余部分拆成一笔新的 WAIT。 */
  amount?: number
  method: OfflinePayMethod
  /** yyyy-MM-dd HH:mm:ss，不能晚于当前时间；不传取服务器时间 */
  paidAt?: string
  voucherUrls?: string[]
  remark?: string
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

/** 一条库存流水。数量正数是增加（进货、取消回滚），负数是减少（销售、入库单作废）。 */
export interface StockLogRow {
  id: number
  skuId: number
  productName: string
  model: string
  skuName: string
  bizType: 'INIT' | 'PURCHASE_IN' | 'PURCHASE_VOID' | 'SALE_OUT' | 'SALE_CANCEL' | 'ADJUST'
  bizId: number | null
  quantity: number
  stockAfter: number
  unitCost: number | null
  operatorName: string
  createdAt: string
}

/** 还没排师傅的上门安装行。orderItemId 是派工接口要的 id。 */
export interface PendingInstall {
  orderItemId: number
  orderId: number
  orderNo: string
  customerName: string
  productName: string
  skuName: string
  quantity: number
  addressText: string
}
