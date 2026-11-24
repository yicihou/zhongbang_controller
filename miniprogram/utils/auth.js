const api = require('./api');
const config = require('../config');

/**
 * 手机号快速验证组件回调。
 * 开发者工具里经常拿不到 code，Mock 模式用占位 code 把绑定流程走完。
 */
function bindPhone(e) {
  const detail = (e && e.detail) || {};
  const errMsg = detail.errMsg || '';
  if (errMsg.indexOf('deny') >= 0 || errMsg.indexOf('fail') >= 0) {
    wx.showToast({ title: '未授权手机号', icon: 'none' });
    return Promise.reject(detail);
  }
  const code = detail.code || (config.USE_MOCK ? 'mock-phone-code' : '');
  if (!code) {
    wx.showToast({ title: '请在真机上授权手机号', icon: 'none' });
    return Promise.reject(detail);
  }
  return api.bindPhone(code);
}

module.exports = { bindPhone };
