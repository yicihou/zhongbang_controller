// 小程序数据开关。和管理端的 VITE_USE_MOCK 互不影响。
// USE_MOCK 为 true：走 miniprogram/mock，不访问后端，开发者工具里也能单独看页面。
// USE_MOCK 为 false：请求 baseUrl 上的 Spring Boot（默认 http://127.0.0.1:8080）。
// 联调时在微信开发者工具勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」。
// 支付渠道见 docs/03 D9：一期 OFFLINE，WXPAY 二期预留。
module.exports = {
  USE_MOCK: false,
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
