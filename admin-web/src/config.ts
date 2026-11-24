/** 与小程序 config.js 的 USE_MOCK 相同：true 走本地 mock，false 走后端。 */
export const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false'

/** 为空时请求相对路径，开发服务器把 /api 代理到 Spring Boot。 */
export const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')
