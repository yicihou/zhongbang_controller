/**
 * 管理端路径常量，避免页面里手写字符串。
 * staffLogin、cancelOrder、technicians、schedules 是页面需要、后端骨架尚未提供的路径。
 * 其余路径对齐 docs/04 的设计，同样还没有 Java 控制器。
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
  notices: '/api/admin/notices',
  stream: '/api/admin/stream',
  receivables: '/api/admin/receivables',
  technicians: '/api/admin/technicians',
  schedules: '/api/admin/technicians/schedules',
}
