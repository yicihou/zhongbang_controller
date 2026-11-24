const { get, post, put, del } = require('./request');
const config = require('../config');

/** 报修单 id 记在本地，真后端没有「我的报修列表」时用详情接口拼出来 */
function rememberRepair(id) {
  if (!id) return;
  try {
    const raw = wx.getStorageSync('repairIds');
    const ids = Array.isArray(raw) ? raw.slice() : [];
    if (ids.indexOf(id) < 0) {
      ids.unshift(id);
      wx.setStorageSync('repairIds', ids.slice(0, 50));
    }
  } catch (e) { /* 存储失败不阻断报修 */ }
}

/**
 * 我的报修列表。
 * 契约只有 GET /api/repairs/{id}，没有列表。Mock 额外提供 GET /api/repairs 方便整表演示；
 * 关闭 Mock 时改为读取本地记下的工单号，再逐条拉详情。
 */
function listRepairs() {
  if (!config.USE_MOCK) {
    const ids = wx.getStorageSync('repairIds') || [];
    if (!ids.length) return Promise.resolve({ total: 0, page: 1, size: 20, list: [] });
    return Promise.all(ids.map((id) => get('/api/repairs/' + id).catch(() => null))).then((rows) => {
      const list = rows.filter(Boolean);
      return { total: list.length, page: 1, size: 20, list: list };
    });
  }
  return get('/api/repairs');
}

/**
 * 师傅工单详情。契约只定义了列表，详情页先请求 GET /api/tech/workorders/{id}
 * （Mock 与本地 Mock 服务都实现了，响应体仍是 WorkOrder）。若 404，退回列表里的那一条。
 */
function getTechWorkorder(id) {
  return get('/api/tech/workorders/' + id).catch(() => {
    return get('/api/tech/workorders', { page: 1, size: 50 }).then((d) => {
      const hit = ((d && d.list) || []).filter((w) => String(w.id) === String(id))[0];
      if (!hit) {
        const err = { code: 1001, msg: '工单不存在' };
        wx.showToast({ title: err.msg, icon: 'none' });
        throw err;
      }
      return hit;
    });
  });
}

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
  createRepair: (data) => post('/api/repairs', data).then((res) => {
    rememberRepair(res && res.workOrderId);
    return res;
  }),
  getRepair: (id) => get('/api/repairs/' + id),
  listRepairs,
  confirmQuote: (id, accept, remark) => post('/api/repairs/' + id + '/quote/confirm', { accept, remark }),
  createEvaluation: (data) => post('/api/evaluations', data),

  // 师傅端（拒单接口契约未定义，仅 Mock 可演示，见 rejectWorkorder）
  getTechWorkorders: (params) => get('/api/tech/workorders', params),
  getTechWorkorder,
  acceptWorkorder: (id, data) => post('/api/tech/workorders/' + id + '/accept', data || {}),
  rejectWorkorder: (id, reason) => {
    if (!config.USE_MOCK) {
      wx.showToast({ title: '拒单接口未在契约中', icon: 'none' });
      return Promise.reject({ code: 404, msg: '拒单接口未在契约中' });
    }
    return post('/api/tech/workorders/' + id + '/reject', { reason });
  },
  checkinWorkorder: (id, data) => post('/api/tech/workorders/' + id + '/checkin', data),
  finishWorkorder: (id, data) => post('/api/tech/workorders/' + id + '/finish', data),

  // 文件
  getUploadPolicy: (bizType) => get('/api/upload/policy', { bizType })
};
