/// <reference types="vite/client" />

/** Vite 只把 VITE_ 开头的变量暴露给前端。两个都可选。 */

interface ImportMetaEnv {
  readonly VITE_USE_MOCK?: string
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
