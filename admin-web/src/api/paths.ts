/**
 * 管理端路径常量，避免页面里手写字符串。
 * 路径对齐 docs/04。Mock 开关打开时由 mock/handle.ts 回答，关掉则打 Spring Boot。
 */
export const paths = {
  staffLogin: '/api/auth/staff/login',
  orders: '/api/admin/orders',
  order: (id: number | string) => `/api/admin/orders/${id}`,
  adjustPrice: (id: number | string) => `/api/admin/orders/${id}/adjust-price`,
  freight: (id: number | string) => `/api/admin/orders/${id}/freight`,
  orderPayments: (id: number | string) => `/api/admin/orders/${id}/payments`,
  payment: (id: number | string) => `/api/admin/payments/${id}`,
  offlineConfirm: (id: number | string) => `/api/admin/payments/${id}/offline-confirm`,
  shipments: (id: number | string) => `/api/admin/orders/${id}/shipments`,
  assignOrder: (id: number | string) => `/api/admin/orders/${id}/assign`,
  cancelOrder: (id: number | string) => `/api/admin/orders/${id}/cancel`,
  repairs: '/api/admin/repairs',
  repair: (id: number | string) => `/api/admin/repairs/${id}`,
  repairQuote: (id: number | string) => `/api/admin/repairs/${id}/quote`,
  repairAssign: (id: number | string) => `/api/admin/repairs/${id}/assign`,
  repairComplete: (id: number | string) => `/api/admin/repairs/${id}/complete`,
  repairCancel: (id: number | string) => `/api/admin/repairs/${id}/cancel`,
  notices: '/api/admin/notices',
  stream: '/api/admin/stream',
  receivables: '/api/admin/receivables',
  technicians: '/api/admin/technicians',
  schedules: '/api/admin/technicians/schedules',
  products: '/api/admin/products',
  product: (id: number | string) => `/api/admin/products/${id}`,
  productOn: (id: number | string) => `/api/admin/products/${id}/on-shelf`,
  productOff: (id: number | string) => `/api/admin/products/${id}/off-shelf`,
  productPrice: (id: number | string) => `/api/admin/products/${id}/price`,
}
