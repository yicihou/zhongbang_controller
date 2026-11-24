import { defineStore } from 'pinia'
import { staffLogin } from '@/api/admin'
import { readToken, writeToken } from '@/api/http'
import { decodeJwtPayload, isStaffPayload } from '@/domain/jwt'

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: readToken(),
  }),
  getters: {
    payload(state) {
      return decodeJwtPayload(state.token)
    },
    isStaff(state): boolean {
      return isStaffPayload(decodeJwtPayload(state.token))
    },
    displayName(): string {
      return this.payload?.name || '员工'
    },
  },
  actions: {
    adopt(token: string) {
      if (!isStaffPayload(decodeJwtPayload(token))) {
        throw new Error('需要 STAFF 角色的 JWT，客户或师傅令牌不能进入后台')
      }
      this.token = token
      writeToken(token)
    },
    async login(username: string, password: string) {
      const data = await staffLogin(username, password)
      this.adopt(data.token)
      return data.staff
    },
    logout() {
      this.token = ''
      writeToken('')
    },
  },
})
