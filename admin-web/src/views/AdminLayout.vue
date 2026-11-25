<template>
  <el-container class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <b>众邦缝纫机</b>
        <span>商家管理后台</span>
      </div>
      <el-menu :default-active="activeMenuPath" router class="menu">
        <el-menu-item v-for="item in navItems" :key="item.key" :index="item.path">
          <el-icon><component :is="iconMap[item.icon]" /></el-icon>
          <span>{{ item.label }}</span>
          <el-badge v-if="badgeCount(item)" :value="badgeCount(item)" class="menu-badge" />
        </el-menu-item>
      </el-menu>
      <div class="side-foot">{{ mockLabel }}</div>
    </el-aside>
    <el-container>
      <el-header class="top" height="60px">
        <div class="link" :class="status">
          <i />
          <span>{{ linkText }}</span>
          <el-button v-if="mockMode && status === 'live'" link type="primary" @click="simulateDrop">模拟断线</el-button>
          <el-button v-if="status !== 'live'" link type="primary" @click="reconnect">重新连接</el-button>
          <el-button v-if="status === 'polling'" link type="primary" @click="pollNow">立即拉取</el-button>
        </div>
        <div class="top-right">
          <span class="sound-label">提示音</span>
          <el-switch :model-value="inbox.soundOn" @change="onSound" />
          <el-popover placement="bottom" :width="360" trigger="click" @show="markRead">
            <template #reference>
              <el-badge :value="inbox.unreadCount" :hidden="inbox.unreadCount === 0" class="bell">
                <el-button circle>
                  <el-icon><Bell /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <div class="notice-pop">
              <div class="notice-title">通知</div>
              <div v-if="inbox.notices.length === 0" class="empty">暂无通知</div>
              <button v-for="item in inbox.notices.slice(0, 8)" :key="item.id" class="notice-item" @click="openNotice(item)">
                <b>{{ item.title }}</b>
                <span>{{ item.body }}</span>
                <em>{{ item.createdAt }}</em>
              </button>
            </div>
          </el-popover>
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="who user-trigger">
              {{ session.displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="session.isBoss" command="staff">员工管理</el-dropdown-item>
                <el-dropdown-item command="logout" :divided="session.isBoss">退出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <PageTabs />
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
/**
 * 登录后的外壳：侧栏菜单、顶栏通知和实时连接状态。
 * 子页面由路由填进 main。角标数字来自 inbox，连接由 useAdminStream 维护。
 * 退出只清前端令牌，没有调用后端注销接口（后端也没有会话可注销）。
 */
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Avatar, Bell, Box, Goods, Odometer, Tools, TrendCharts, Wallet } from '@element-plus/icons-vue'
import { USE_MOCK } from '@/config'
import PageTabs from '@/components/PageTabs.vue'
import { useAdminStream } from '@/composables/useAdminStream'
import type { AdminNotice } from '@/api/types'
import { navItemForPath, visibleNavItems, type NavIcon, type NavItem } from '@/domain/navigation'
import { useInboxStore } from '@/stores/inbox'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const inbox = useInboxStore()
const { status, reconnect, simulateDrop, pollNow } = useAdminStream()

const mockMode = USE_MOCK
const mockLabel = USE_MOCK ? '数据：Mock' : '数据：真实 API'

/** 侧栏配置里的图标名换成真正的组件。 */
const iconMap: Record<NavIcon, Component> = {
  Odometer,
  Goods,
  Wallet,
  Box,
  Tools,
  Avatar,
  TrendCharts,
}

/** 侧栏只显示当前角色能看的组。老板 7 项，管理员 5 项，员工管理在右上角菜单里。 */
const navItems = computed(() => visibleNavItems(session.isBoss))

/**
 * el-menu 的 index 是分组的第一个标签地址。
 * 当前在 /stock-alerts 这类第二个标签时，也要高亮「商品库存」这一项。
 */
const activeMenuPath = computed(() => {
  const item = navItemForPath(route.path)
  if (!item) return route.path
  return navItems.value.find((candidate) => candidate.key === item.key)?.path ?? route.path
})

/** 订单和报修的未读角标，其他分组没有角标。 */
function badgeCount(item: NavItem): number {
  if (item.badge === 'order') return inbox.orderFlashes
  if (item.badge === 'repair') return inbox.repairFlashes
  return 0
}
const linkText = computed(() => {
  if (status.value === 'live') return '实时连接'
  if (status.value === 'polling') return '轮询中 · 30 秒'
  return '正在重连'
})

function onSound(value: string | number | boolean) {
  inbox.setSound(Boolean(value))
}

function markRead() {
  inbox.markSeen(inbox.notices.map((item) => item.id))
}

function openNotice(item: AdminNotice) {
  if (item.bizType === 'ORDER') void router.push(`/orders/${item.bizId}`)
  else if (item.bizType === 'REPAIR') void router.push(`/repairs/${item.bizId}`)
  else if (item.bizType === 'CUSTOMER' && session.isBoss) void router.push(`/customers/${item.bizId}`)
  else if (item.bizType === 'STOCK') void router.push('/stock-alerts')
}

/** 右上角用户名菜单：员工管理只给老板看，退出所有人都有。 */
function onUserCommand(command: string | number | object) {
  const action = String(command)
  if (action === 'staff') void router.push('/staff')
  else if (action === 'logout') logout()
}

function logout() {
  session.logout()
  void router.push('/login')
}
</script>

<style scoped>
.shell {
  height: 100%;
}
.aside {
  background: #fff;
  border-right: 1px solid var(--zb-line);
  display: flex;
  flex-direction: column;
}
.brand {
  height: 72px;
  background: var(--zb-red);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 18px;
}
.brand b {
  font-size: 16px;
  letter-spacing: 0.4px;
}
.brand span {
  margin-top: 2px;
  font-size: 12px;
  opacity: 0.88;
}
.menu {
  border-right: none;
  flex: 1;
}
.menu :deep(.el-menu-item.is-active) {
  background: var(--zb-tint);
  color: var(--zb-red);
}
.menu-badge {
  margin-left: 8px;
}
.side-foot {
  padding: 12px 16px 16px;
  color: var(--zb-muted);
  font-size: 12px;
}
.top {
  background: #fff;
  border-bottom: 1px solid var(--zb-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.link {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--zb-muted);
  font-size: 13px;
}
.link i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c2c7cf;
}
.link.live i {
  background: #0f7b57;
}
.link.reconnecting i {
  background: #b45309;
}
.link.polling i {
  background: #6c7480;
}
.top-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sound-label,
.who {
  color: var(--zb-muted);
  font-size: 13px;
}
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
.user-trigger:hover {
  color: var(--zb-red);
}
.bell {
  margin-right: 4px;
}
.main {
  background: var(--zb-bg);
}
.notice-pop {
  max-height: 360px;
  overflow: auto;
}
.notice-title {
  font-weight: 650;
  margin-bottom: 8px;
}
.notice-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 0;
  border-top: 1px solid var(--zb-line);
  cursor: pointer;
}
.notice-item b {
  font-size: 13px;
}
.notice-item span,
.notice-item em {
  color: var(--zb-muted);
  font-size: 12px;
  font-style: normal;
}
.empty {
  color: var(--zb-muted);
  font-size: 13px;
}
</style>
