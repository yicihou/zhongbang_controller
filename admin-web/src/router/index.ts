/**
 * 页面地址。除 /login 外都套在 AdminLayout 里（侧栏 + 顶栏）。
 * 进入业务页之前看 session 里的 JWT：解出来不是未过期的 STAFF，就清掉令牌并回到登录页。
 * 这里只看令牌内容，不请求后端。真实模式下令牌仍要后端验签，前端这一步挡不住伪造后的「看起来像 STAFF」的字符串进入页面，
 * 但接口会失败。Mock 自己签的令牌没有真实签名，见 domain/jwt.ts。
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import AdminLayout from '@/views/AdminLayout.vue'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import OrderListView from '@/views/OrderListView.vue'
import OrderDetailView from '@/views/OrderDetailView.vue'
import RepairListView from '@/views/RepairListView.vue'
import RepairDetailView from '@/views/RepairDetailView.vue'
import TechnicianView from '@/views/TechnicianView.vue'
import ReceivableView from '@/views/ReceivableView.vue'
import ProductView from '@/views/ProductView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
        { path: 'products', name: 'products', component: ProductView, meta: { title: '商品' } },
        { path: 'orders', name: 'orders', component: OrderListView, meta: { title: '订单' } },
        { path: 'orders/:id', name: 'order-detail', component: OrderDetailView, meta: { title: '订单详情' } },
        { path: 'repairs', name: 'repairs', component: RepairListView, meta: { title: '报修' } },
        { path: 'repairs/:id', name: 'repair-detail', component: RepairDetailView, meta: { title: '报修详情' } },
        { path: 'technicians', name: 'technicians', component: TechnicianView, meta: { title: '师傅排期' } },
        { path: 'receivables', name: 'receivables', component: ReceivableView, meta: { title: '待收款' } },
      ],
    },
  ],
})

/** 未登录访问业务页会带上 redirect，登录成功后再跳回去。已登录再打开 /login 则直接进工作台。 */
router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.path !== '/login' && !session.isStaff) {
    session.logout()
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.path === '/login' && session.isStaff) return '/'
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '商家后台'
  document.title = `${title} · 众邦缝纫机`
})
