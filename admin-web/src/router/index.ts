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

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
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
