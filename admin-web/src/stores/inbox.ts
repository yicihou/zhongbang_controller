/**
 * 顶栏通知和侧栏角标。数据来自 SSE（或 Mock 的内存广播），不是单独的未读接口轮询结果才有。
 * 断线降级后，useAdminStream 会把轮询到的通知也送进 ingest。
 * soundOn 记在 localStorage 键 zb_admin_mute，1 表示静音。
 * linkMode 只反映连接状态文案，不自己去连服务器。
 */
import { defineStore } from 'pinia'
import type { AdminNotice } from '@/api/types'

const MUTE_KEY = 'zb_admin_mute'

export const useInboxStore = defineStore('inbox', {
  state: () => ({
    notices: [] as AdminNotice[],
    seenIds: [] as number[],
    orderFlashes: 0,
    repairFlashes: 0,
    freshOrderIds: [] as number[],
    revision: 0,
    soundOn: localStorage.getItem(MUTE_KEY) !== '1',
    linkMode: 'reconnecting' as 'live' | 'reconnecting' | 'polling',
  }),
  getters: {
    unreadCount(state): number {
      return state.notices.filter((notice) => !notice.readFlag && !state.seenIds.includes(notice.id)).length
    },
  },
  actions: {
    /**
     * 收进一条通知。live 为 false 时只入列表（例如刚进页面拉历史），不增加角标。
     * 返回 true 表示这是一条还没提示过的实时通知，调用方可以响铃和弹窗。
     */
    ingest(notice: AdminNotice, live: boolean) {
      const known = this.notices.some((item) => item.id === notice.id)
      if (!known) this.notices.unshift(notice)
      this.revision += 1
      if (!live || this.seenIds.includes(notice.id)) return false
      if (notice.eventType === 'ORDER_CREATED') {
        this.orderFlashes += 1
        if (!this.freshOrderIds.includes(notice.bizId)) this.freshOrderIds.push(notice.bizId)
      }
      if (notice.eventType === 'REPAIR_CREATED') this.repairFlashes += 1
      return true
    },
    markSeen(ids: number[]) {
      for (const id of ids) {
        if (!this.seenIds.includes(id)) this.seenIds.push(id)
      }
    },
    clearOrderFlash() {
      this.orderFlashes = 0
    },
    clearRepairFlash() {
      this.repairFlashes = 0
    },
    setSound(on: boolean) {
      this.soundOn = on
      localStorage.setItem(MUTE_KEY, on ? '0' : '1')
    },
  },
})
