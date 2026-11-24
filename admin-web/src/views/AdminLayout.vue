<template>
  <el-container class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <b>众邦缝纫机</b>
        <span>商家管理后台</span>
      </div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon>
          <span>订单</span>
          <el-badge v-if="inbox.orderFlashes" :value="inbox.orderFlashes" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/repairs">
          <el-icon><Tools /></el-icon>
          <span>报修</span>
          <el-badge v-if="inbox.repairFlashes" :value="inbox.repairFlashes" class="menu-badge" />
        </el-menu-item>
        <el-menu-item index="/technicians">
          <el-icon><Calendar /></el-icon>
          <span>师傅排期</span>
        </el-menu-item>
        <el-menu-item index="/receivables">
          <el-icon><Wallet /></el-icon>
          <span>待收款</span>
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
          <span class="who">{{ session.displayName }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Calendar, List, Odometer, Tools, Wallet } from '@element-plus/icons-vue'
import { USE_MOCK } from '@/config'
import { useAdminStream } from '@/composables/useAdminStream'
import type { AdminNotice } from '@/api/types'
import { useInboxStore } from '@/stores/inbox'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const inbox = useInboxStore()
const { status, reconnect, simulateDrop, pollNow } = useAdminStream()

const mockMode = USE_MOCK
const mockLabel = USE_MOCK ? '数据：Mock' : '数据：真实 API'
const active = computed(() => {
  if (route.path.startsWith('/orders')) return '/orders'
  if (route.path.startsWith('/repairs')) return '/repairs'
  return route.path
})
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
