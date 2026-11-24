/**
 * 数据从哪来。
 * 没写 VITE_USE_MOCK，或写成除 false 以外的任何值，都走浏览器里的 Mock，不访问 Java。
 * 只有 VITE_USE_MOCK=false 才请求后端。改完需要重启 Vite。
 * 小程序的开关在 miniprogram/config.js，两边互不影响。
 */
export const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false'

/** 为空时请求相对路径，开发服务器把 /api 代理到 Spring Boot。 */
export const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '')
