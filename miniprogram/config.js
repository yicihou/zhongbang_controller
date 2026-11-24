// 小程序数据开关。
// USE_MOCK 为 true：走 miniprogram/mock，不访问后端，开发者工具里也能单独看页面。
// USE_MOCK 为 false：商品、下单、我的订单请求 baseUrl 上的 Spring Boot（默认 http://127.0.0.1:8080）。
// 联调时改成 false，并在微信开发者工具勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」。
// 这个开关和管理端的 VITE_USE_MOCK 互不影响。
module.exports = {
  USE_MOCK: false,
  baseUrl: 'http://127.0.0.1:8080',
  version: '1.0.0'
};
