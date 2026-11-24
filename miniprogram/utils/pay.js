const api = require('./api');
const config = require('../config');

/**
 * 支付渠道抽象（docs/03 D9、docs/04 §2.7）。
 * 一期：OFFLINE —— 客户联系管理员转账，可选标记「我将线下转账」。
 * 二期：WXPAY —— prepay + wx.requestPayment；代码保留，默认 enableWxPay=false。
 */
function payOfflineIntent(bizType, bizId, paymentId, remark) {
  return api.offlinePayIntent({
    bizType: bizType || 'ORDER',
    bizId: bizId,
    paymentId: paymentId,
    remark: remark || '客户将线下转账'
  });
}

/**
 * 微信渠道（二期）。未开启时直接拒绝，避免误调起。
 * @param {number} bizId 订单或报修单 id
 * @param {string} type FULL|DEPOSIT|BALANCE|REPAIR
 * @param {number} [paymentId] 指定某一笔 WAIT
 */
function payWx(bizId, type, paymentId) {
  if (!config.enableWxPay || config.paymentChannel === 'OFFLINE') {
    return Promise.reject({ code: 5001, msg: '微信支付为二期能力，一期请线下转账' });
  }
  return api.prepay({ orderId: bizId, type: type, paymentId: paymentId, channel: 'WXPAY' }).then((p) => {
    if (config.USE_MOCK) {
      return api.mockPaySuccess({ orderId: bizId, type: type, paymentId: paymentId }).then(() => p);
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

/** 兼容旧调用名：一期走线下意图 */
function pay(orderId, type) {
  if (config.enableWxPay && config.paymentChannel === 'WXPAY') {
    return payWx(orderId, type);
  }
  return payOfflineIntent('ORDER', orderId, null, '支付类型 ' + (type || 'FULL'));
}

module.exports = { pay, payOfflineIntent, payWx };
