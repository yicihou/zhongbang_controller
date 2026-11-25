export {}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 只有管理员能打开的页面，例如员工管理。 */
    admin?: boolean
  }
}
