/**
 * 把接口里的英文枚举显示成中文。未知状态不要假设有文案：函数直接取表，缺 key 会得到 undefined。
 * 订单状态是 docs/04 的新枚举，不是 V1 表或旧 OpenAPI 示例里的 PENDING_BALANCE。
 */
import type {
  DeliveryMethod,
  OfflinePayMethod,
  OrderStatus,
  PaymentStatus,
  PaymentType,
  RepairStatus,
  ShipmentStatus,
} from '@/api/types'

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款',
  PARTIALLY_PAID: '部分付款',
  PAID: '已付款',
  FULFILLING: '履约中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export const REPAIR_STATUS_LABEL: Record<RepairStatus, string> = {
  SUBMITTED: '待报价',
  QUOTED: '已报价',
  PENDING_PAYMENT: '待付款',
  PAID: '已付款',
  CONFIRMED: '可派工',
  IN_SERVICE: '维修中',
  DONE: '已完成',
  CANCELLED: '已取消',
}

export const DELIVERY_LABEL: Record<DeliveryMethod, string> = {
  EXPRESS: '快递',
  FREIGHT: '物流货运',
  HOME_DELIVERY: '上门送货',
  ON_SITE_INSTALL: '上门安装',
  STORE_PICKUP: '到店自提',
}

export const PAYMENT_TYPE_LABEL: Record<PaymentType, string> = {
  FULL: '全款',
  DEPOSIT: '定金',
  BALANCE: '尾款',
  REPAIR: '维修费',
  FREIGHT: '运费',
}

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  WAIT: '待付',
  SUCCESS: '已收',
  CLOSED: '已关闭',
  REFUNDED: '已退款',
}

/** 线下到账方式。和支付渠道不是一回事：渠道仍是 OFFLINE，微信支付还没接。 */
export const OFFLINE_METHOD_LABEL: Record<OfflinePayMethod, string> = {
  BANK_TRANSFER: '银行转账',
  WECHAT_TRANSFER: '微信转账',
  ALIPAY: '支付宝',
  CASH: '现金',
  OTHER: '其他',
}

export const OFFLINE_METHOD_OPTIONS = Object.keys(OFFLINE_METHOD_LABEL) as OfflinePayMethod[]

export function offlineMethodText(method: OfflinePayMethod | undefined): string {
  return method ? OFFLINE_METHOD_LABEL[method] : ''
}

export const SHIPMENT_STATUS_LABEL: Record<ShipmentStatus, string> = {
  PENDING: '待发出',
  DISPATCHED: '已发出',
  DONE: '已完成',
  CANCELLED: '已取消',
}

export const PRICE_FIELD_LABEL: Record<string, string> = {
  unitPrice: '单价',
  installFee: '安装费',
  adjustAmount: '整单调整',
  freightAmount: '运费',
}

export const ORDER_STATUS_OPTIONS: OrderStatus[] = [
  'PENDING_PAYMENT',
  'PARTIALLY_PAID',
  'PAID',
  'FULFILLING',
  'COMPLETED',
  'CANCELLED',
]

export const REPAIR_STATUS_OPTIONS: RepairStatus[] = [
  'SUBMITTED',
  'QUOTED',
  'PENDING_PAYMENT',
  'PAID',
  'CONFIRMED',
  'IN_SERVICE',
  'DONE',
  'CANCELLED',
]

export const STAFF_ROLE_LABEL: Record<string, string> = {
  ADMIN: '管理员',
  SALES: '业务员',
  TECHNICIAN: '师傅',
  STAFF: '员工',
}

export const STAFF_ROLE_OPTIONS = ['ADMIN', 'SALES', 'TECHNICIAN'] as const

export const STAFF_STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

export function orderStatusText(status: OrderStatus): string {
  return ORDER_STATUS_LABEL[status]
}

export function repairStatusText(status: RepairStatus): string {
  return REPAIR_STATUS_LABEL[status]
}

export function deliveryText(method: DeliveryMethod): string {
  return DELIVERY_LABEL[method]
}

export function paymentTypeText(type: PaymentType): string {
  return PAYMENT_TYPE_LABEL[type]
}

export function paymentStatusText(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABEL[status]
}

export function orderTagType(status: OrderStatus): 'primary' | 'success' | 'warning' | 'info' {
  if (status === 'PENDING_PAYMENT' || status === 'PARTIALLY_PAID') return 'warning'
  if (status === 'PAID' || status === 'COMPLETED') return 'success'
  if (status === 'FULFILLING') return 'primary'
  return 'info'
}

export function repairTagType(status: RepairStatus): 'primary' | 'success' | 'warning' | 'info' {
  if (status === 'SUBMITTED' || status === 'QUOTED' || status === 'PENDING_PAYMENT') return 'warning'
  if (status === 'IN_SERVICE') return 'primary'
  if (status === 'PAID' || status === 'CONFIRMED' || status === 'DONE') return 'success'
  return 'info'
}
