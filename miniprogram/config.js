// 全局配置：本地开发用 Mock，联调时把 USE_MOCK 改成 false 并指向 Mock 服务/后端
module.exports = {
  USE_MOCK: true,
  baseUrl: 'http://127.0.0.1:8080',
  version: '1.0.0'
};
