// 全局配置：本地开发用 Mock，联调时把 USE_MOCK 改成 false 并指向 Mock 服务或 Java 后端
module.exports = {
  USE_MOCK: true,
  baseUrl: 'http://127.0.0.1:8080',
  version: '1.0.0',
  servicePhone: '4000000000',
  serviceWechat: 'sewing-dealer',
  // 订阅消息模板 ID。公众平台申请通过后填入；留空时只展示引导，不调用 wx.requestSubscribeMessage
  subscribeTmplIds: []
};
