const { get, post, put, del } = require('./request');
const config = require('../config');

function rememberRepair(id) {
  if (!id) return;
  try {
    const raw = wx.getStorageSync('repairIds');
    const ids = Array.isArray(raw) ? raw.slice() : [];
    if (ids.indexOf(id) < 0) {
      ids.unshift(id);
      wx.setStorageSync('repairIds', ids.slice(0, 50));
    }
  } catch (e) { /* ignore */ }
}

function listRepairs(params) {
  if (!config.USE_MOCK) {
    return get('/api/repairs', params || {}).catch(() => {
      const ids = wx.getStorageSync('repairIds') || [];
      if (!ids.length) return { total: 0, page: 1, size: 20, list: [] };
      return Promise.all(ids.map((id) => get('/api/repairs/' + id).catch(() => null))).then((rows) => {
        const list = rows.filter(Boolean);
        return { total: list.length, page: 1, size: 20, list: list };
      });
    });
  }
  return get('/api/repairs', params || {});
}

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
  login: (code) => post('/api/auth/login', { code }, { auth: false }),
  bindPhone: (code) => post('/api/auth/phone', { code }),

  getHome: () => get('/api/home', {}, { auth: false }),
  getCategories: () => get('/api/categories', {}, { auth: false }),
  getProducts: (params) => get('/api/products', params, { auth: false }),
  getProduct: (id) => get('/api/products/' + id, {}, { auth: false }),

  getTechnicians: (params) => get('/api/technicians/available', params),

  // 购物车 D1 / 04 §4.1
  getCart: () => get('/api/cart'),
  addCartItem: (data) => post('/api/cart/items', data),
  updateCartItem: (id, data) => put('/api/cart/items/' + id, data),
  removeCartItem: (id) => del('/api/cart/items/' + id),

  // 多行下单 D1/D7
  createOrder: (data) => post('/api/orders', data),
  getOrders: (params) => get('/api/orders', params),
  getOrder: (id) => get('/api/orders/' + id),
  cancelOrder: (id, reason) => post('/api/orders/' + id + '/cancel', { reason }),
  getShipments: (id) => get('/api/orders/' + id + '/shipments'),

  // 一期线下；微信预留
  offlinePayIntent: (data) => post('/api/pay/offline/intent', data),
  prepay: (data) => post('/api/pay/prepay', typeof data === 'object' ? data : { orderId: data }),
  mockPaySuccess: (data) => post('/api/pay/mock-success', typeof data === 'object' ? data : { orderId: data }),

  getAdminContact: () => get('/api/contact/admin'),

  getProfile: () => get('/api/customer/profile'),
  getAddresses: () => get('/api/customer/addresses'),
  addAddress: (data) => post('/api/customer/addresses', data),
  updateAddress: (id, data) => put('/api/customer/addresses/' + id, data),
  deleteAddress: (id) => del('/api/customer/addresses/' + id),

  getAssets: () => get('/api/assets'),
  bindAsset: (sn) => post('/api/assets/bind', { sn }),

  createRepair: (data) => post('/api/repairs', data).then((res) => {
    rememberRepair(res && (res.ticketId || res.id || res.workOrderId));
    return res;
  }),
  getRepair: (id) => get('/api/repairs/' + id),
  listRepairs,
  confirmQuote: (id, accept, remark) => post('/api/repairs/' + id + '/quote/confirm', { accept, remark }),
  createEvaluation: (data) => post('/api/evaluations', data),

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

  getUploadPolicy: (bizType) => get('/api/upload/policy', { bizType }),

  // 兼容旧验收入口（安装工单完成后客户确认）；新状态机以 PAID/FULFILLING 为主
  acceptOrder: (id, pass, remark) => post('/api/orders/' + id + '/accept', { pass, remark })
};
