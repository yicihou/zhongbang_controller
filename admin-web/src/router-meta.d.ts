export {}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 只有老板能打开的页面，例如客户档案和员工管理。管理员直接输入网址也会被送回工作台。 */
    boss?: boolean
  }
}
