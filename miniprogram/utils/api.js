const { get, post, put, del } = require('./request');

module.exports = {
  // 认证
  login: (code) => post('/api/auth/login', { code }, { auth: false }),
  bindPhone: (code) => post('/api/auth/phone', { code }),

  // 首页 / 商品
  getHome: () => get('/api/home', {}, { auth: false }),
  getCategories: () => get('/api/categories', {}, { auth: false }),
  getProducts: (params) => get('/api/products', params, { auth: false }),
  getProduct: (id) => get('/api/products/' + id, {}, { auth: false }),

  // 师傅
  getTechnicians: (params) => get('/api/technicians/available', params),

  // 订单
  createOrder: (data) => post('/api/orders', data),
  getOrders: (params) => get('/api/orders', params),
  getOrder: (id) => get('/api/orders/' + id),
  cancelOrder: (id, reason) => post('/api/orders/' + id + '/cancel', { reason }),
  acceptOrder: (id, pass, remark) => post('/api/orders/' + id + '/accept', { pass, remark }),

  // 支付
  prepay: (orderId, type) => post('/api/pay/prepay', { orderId, type }),
  mockPaySuccess: (orderId, type) => post('/api/pay/mock-success', { orderId, type }),

  // 客户
  getProfile: () => get('/api/customer/profile'),
  getAddresses: () => get('/api/customer/addresses'),
  addAddress: (data) => post('/api/customer/addresses', data),
  updateAddress: (id, data) => put('/api/customer/addresses/' + id, data),
  deleteAddress: (id) => del('/api/customer/addresses/' + id),

  // 设备
  getAssets: () => get('/api/assets'),
  bindAsset: (sn) => post('/api/assets/bind', { sn }),

  // 报修 / 评价
  createRepair: (data) => post('/api/repairs', data),
  getRepair: (id) => get('/api/repairs/' + id),
  confirmQuote: (id, accept) => post('/api/repairs/' + id + '/quote/confirm', { accept }),
  createEvaluation: (data) => post('/api/evaluations', data),

  // 师傅端
  getTechWorkorders: (params) => get('/api/tech/workorders', params),
  acceptWorkorder: (id) => post('/api/tech/workorders/' + id + '/accept'),

  // 文件
  getUploadPolicy: (bizType) => get('/api/upload/policy', { bizType })
};
