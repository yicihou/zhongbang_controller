/**
 * 数据从哪来。
 * 默认请求真实后端。只有把 VITE_USE_MOCK 写成 true，才走浏览器里的 Mock。
 * 改完需要重启 Vite。小程序的开关在 miniprogram/config.js，两边互不影响。
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/**
 * 接口根地址。没写时是 http://127.0.0.1:8080。
 * 写成空字符串则请求相对路径，开发服务器把 /api 代理到 8080，可以避开跨域。
 */
const rawBase = import.meta.env.VITE_API_BASE
export const API_BASE = (rawBase == null ? 'http://127.0.0.1:8080' : rawBase).replace(/\/$/, '')
