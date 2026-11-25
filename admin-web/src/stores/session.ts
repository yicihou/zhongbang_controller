/**
 * 登录态。令牌同时放在 Pinia 和 localStorage 键 zb_admin_token。
 * login 调用 POST /api/auth/staff/login（Mock 或后端），成功后只接受 type/role 为 STAFF 的 JWT。
 * 客户小程序拿到的 CUSTOMER 令牌会被 adopt 拒绝，进不了后台。
 * 刷新页面会从 localStorage 读回令牌，再由路由守卫检查是否过期、是否员工。
 */
import { defineStore } from 'pinia'
import { staffLogin } from '@/api/admin'
import { readToken, writeToken } from '@/api/http'
import { decodeJwtPayload, isAdminPayload, isBossPayload, isStaffPayload } from '@/domain/jwt'

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
    isAdmin(state): boolean {
      return isAdminPayload(decodeJwtPayload(state.token))
    },
    isBoss(state): boolean {
      return isBossPayload(decodeJwtPayload(state.token))
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
