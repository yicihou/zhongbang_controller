const config = require('../config');
const mock = require('../mock/index');

let refreshing = null;

/**
 * 真正发起请求，返回 {statusCode, body}。
 * config.js 里 USE_MOCK 为 true 时不访问后端，改走 mock/index.js。
 * 改成 false 后才会请求 config.baseUrl（默认 http://127.0.0.1:8080）。
 */
function raw(path, method, data, auth) {
  if (config.USE_MOCK) {
    return mock.request(path, method, data);
  }
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' };
    const token = wx.getStorageSync('token');
    if (auth !== false && token) header.Authorization = 'Bearer ' + token;
    wx.request({
      url: config.baseUrl + path,
      method,
      data,
      header,
      timeout: 15000,
      success: (res) => resolve({ statusCode: res.statusCode, body: res.data || {} }),
      fail: (err) => reject({ code: -1, msg: '网络异常，请检查网络后重试', raw: err })
    });
  });
}

/**
 * 统一请求：自动解包 {code,msg,data}，token 过期自动重新登录并重放一次。
 * @param {string} path 形如 /api/orders
 * @param {object} opts {method, data, auth, retried}
 */
async function request(path, opts) {
  opts = opts || {};
  const method = opts.method || 'GET';
  const data = opts.data || {};
  const auth = opts.auth !== false;

  let res;
  try {
    res = await raw(path, method, data, auth);
  } catch (e) {
    wx.showToast({ title: (e && e.msg) || '网络异常', icon: 'none' });
    throw e;
  }

  const body = (res && res.body) || {};

  if (body.code === 0) return body.data;

  // 2001: token 过期 —— 静默重新登录后重放一次
  if ((body.code === 2001 || res.statusCode === 401) && !opts.retried) {
    try {
      const app = getApp();
      refreshing = refreshing || app.login().then(() => { refreshing = null; },
        (e) => { refreshing = null; throw e; });
      await refreshing;
      return request(path, Object.assign({}, opts, { retried: true }));
    } catch (e) {
      // 登录也失败，落到下面的统一提示
    }
  }

  wx.showToast({ title: body.msg || '请求失败', icon: 'none' });
  throw body;
}

module.exports = {
  request,
  get: (path, data, opts) => request(path, Object.assign({ method: 'GET', data }, opts)),
  post: (path, data, opts) => request(path, Object.assign({ method: 'POST', data }, opts)),
  put: (path, data, opts) => request(path, Object.assign({ method: 'PUT', data }, opts)),
  del: (path, data, opts) => request(path, Object.assign({ method: 'DELETE', data }, opts))
};
