// 全局配置：本地开发用 Mock；联调时 USE_MOCK=false 并指向 Mock 服务或 Java 后端
// 支付渠道见 docs/03 D9：一期 OFFLINE，WXPAY 二期预留
module.exports = {
  USE_MOCK: true,
  baseUrl: 'http://127.0.0.1:8080',
  version: '1.1.0',
  servicePhone: '4000000000',
  serviceWechat: 'sewing-dealer',
  /** 一期支付渠道：OFFLINE | WXPAY（WXPAY 未开时不会调起微信） */
  paymentChannel: 'OFFLINE',
  /** true 时开放微信预下单入口（二期）；一期保持 false */
  enableWxPay: false,
  subscribeTmplIds: []
};
