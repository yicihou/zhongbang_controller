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
