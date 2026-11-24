const api = require('./api');
const config = require('../config');

/**
 * 统一支付。
 * Mock：跳过 wx.requestPayment，调 /api/pay/mock-success 推进订单（生产环境没有这个接口）。
 * 真后端：只用 prepay 返回的参数调起微信支付，前端不计算金额。
 * @param {number} orderId 订单号；维修费沿用契约字段名 orderId，传工单 id，type=REPAIR
 * @param {'DEPOSIT'|'BALANCE'|'REPAIR'} type
 */
function pay(orderId, type) {
  return api.prepay(orderId, type).then((p) => {
    if (config.USE_MOCK) {
      return api.mockPaySuccess(orderId, type).then(() => p);
    }
    const params = p.payParams || {};
    return new Promise((resolve, reject) => {
      wx.requestPayment({
        timeStamp: params.timeStamp,
        nonceStr: params.nonceStr,
        package: params.package,
        signType: params.signType || 'RSA',
        paySign: params.paySign,
        success: () => resolve(p),
        fail: (err) => {
          const msg = (err && err.errMsg && err.errMsg.indexOf('cancel') >= 0) ? '已取消支付' : '支付未完成';
          reject({ msg: msg, raw: err });
        }
      });
    });
  });
}

module.exports = { pay };
