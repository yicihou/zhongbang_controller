/**
 * 员工和师傅的规则。管理端 Mock 和单测都调用这里，不直接改页面。
 * 真实后端的同样规则在 StaffService。密码在 Mock 里仍是明文，只为了和种子账号对上；Java 里会做 BCrypt。
 */
import type { PageResult, Technician } from '@/api/types'
import { ApiError } from '@/domain/errors'
import { paginate } from '@/domain/page'

export type StaffRole = 'ADMIN' | 'SALES' | 'TECHNICIAN' | 'STAFF' | 'BOSS'
export type StaffStatus = 'ENABLED' | 'DISABLED'

/** Mock 里能登录的账号。password 是明文。 */
export interface LoginAccount {
  id: number
  username: string
  password: string
  name: string
  mobile: string
  role: StaffRole
  status: StaffStatus
  remark: string
  technicianId?: number
}

export interface StaffMember {
  ref: string
  userId?: number
  technicianId?: number
  name: string
  mobile: string
  role: StaffRole
  status: StaffStatus
  remark: string
  username?: string
  loginAccount: boolean
  skills: string[]
  region: string
}

export interface StaffSave {
  name?: string
  mobile?: string
  role?: string
  status?: string
  remark?: string
  username?: string
  password?: string
  skills?: string[]
  region?: string
}

export interface StaffQuery {
  keyword?: string
  role?: string
  status?: string
  page?: number
  size?: number
}

interface Store {
  users: LoginAccount[]
  technicians: Technician[]
  nextId: () => number
}

export function isEnabledTechnician(tech: Pick<Technician, 'status'> | undefined): boolean {
  return !!tech && tech.status !== 'DISABLED'
}

export function listStaff(users: LoginAccount[], technicians: Technician[], query: StaffQuery = {}): PageResult<StaffMember> {
  const key = (query.keyword ?? '').trim().toLowerCase()
  const rows = merged(users, technicians).filter((row) => {
    if (query.role && row.role !== query.role) return false
    if (query.status && row.status !== query.status) return false
    if (!key) return true
    return [row.name, row.mobile, row.username ?? '', row.remark].some((field) => field.toLowerCase().includes(key))
  })
  return paginate(rows, query.page, query.size)
}

export function createStaff(store: Store, body: StaffSave): StaffMember {
  const parsed = parse(body, true, undefined)
  if (parsed.role === 'TECHNICIAN') {
    const tech = pushTech(store, parsed, undefined)
    if (parsed.username) {
      const user = pushUser(store, parsed, tech.id)
      tech.userId = user.id
      return toMember(user, tech)
    }
    return toMember(undefined, tech)
  }
  const user = pushUser(store, parsed, undefined)
  return toMember(user, undefined)
}

export function updateStaff(store: Store, ref: string, body: StaffSave, operatorId = 0): StaffMember {
  const located = locate(store, ref)
  const parsed = parse(body, false, located.user)
  if (located.user) return updateUser(store, located, parsed, operatorId)
  return updateTechOnly(store, located.tech!, parsed)
}

export function changeStaffStatus(store: Store, ref: string, status: string, operatorId: number): StaffMember {
  const next = normalizeStatus(status)
  const located = locate(store, ref)
  if (located.user) {
    // 老板停用自己后，就没有人能再进员工管理。
    if (next === 'DISABLED' && located.user.id === operatorId) {
      throw new ApiError(3003, '不能停用自己的账号')
    }
    assertKeepsAdmin(store.users, located.user, located.user.role, next)
    located.user.status = next
    const tech = linkedTech(store, located.user, located.tech)
    if (tech && located.user.role === 'TECHNICIAN') tech.status = next
    return toMember(located.user, located.user.role === 'TECHNICIAN' ? tech : undefined)
  }
  located.tech!.status = next
  return toMember(undefined, located.tech)
}

export function resetStaffPassword(store: Store, ref: string, password: string): void {
  const raw = checkPassword(password)
  const located = locate(store, ref)
  if (!located.user) throw new ApiError(1001, '这个员工还没有登录账号，请先填写用户名和密码')
  located.user.password = raw
}

function updateUser(store: Store, located: Located, parsed: Parsed, operatorId: number): StaffMember {
  const user = located.user!
  if (parsed.status === 'DISABLED' && user.id === operatorId) {
    throw new ApiError(3003, '不能停用自己的账号')
  }
  assertKeepsAdmin(store.users, user, parsed.role, parsed.status)
  assertKeepsOwnBoss(user, parsed.role, operatorId)
  if (parsed.username && parsed.username !== user.username) {
    assertUsernameFree(store.users, parsed.username)
    user.username = parsed.username
  }
  if (parsed.password) user.password = parsed.password
  user.name = parsed.name
  user.mobile = parsed.mobile
  user.remark = parsed.remark
  user.status = parsed.status
  user.role = parsed.role
  let tech = linkedTech(store, user, located.tech)
  if (parsed.role === 'TECHNICIAN') {
    if (!tech) {
      tech = pushTech(store, parsed, user.id)
      user.technicianId = tech.id
    } else {
      applyTech(tech, parsed)
      tech.userId = user.id
      tech.status = parsed.status
      user.technicianId = tech.id
    }
  } else if (tech) {
    tech.status = 'DISABLED'
  }
  return toMember(user, parsed.role === 'TECHNICIAN' ? tech : undefined)
}

function updateTechOnly(store: Store, tech: Technician, parsed: Parsed): StaffMember {
  if (parsed.role !== 'TECHNICIAN') {
    if (!parsed.username || !parsed.password) {
      throw new ApiError(1001, '改成老板、管理员或业务员时，请填写用户名和密码')
    }
    assertUsernameFree(store.users, parsed.username)
    tech.status = 'DISABLED'
    tech.name = parsed.name
    tech.mobile = parsed.mobile
    tech.remark = parsed.remark
    const user = pushUser(store, parsed, tech.id)
    tech.userId = user.id
    return toMember(user, tech)
  }
  applyTech(tech, parsed)
  tech.status = parsed.status
  if (parsed.username) {
    if (!parsed.password) throw new ApiError(1001, '开通登录请设置密码')
    assertUsernameFree(store.users, parsed.username)
    const user = pushUser(store, parsed, tech.id)
    tech.userId = user.id
    return toMember(user, tech)
  }
  return toMember(undefined, tech)
}

function merged(users: LoginAccount[], technicians: Technician[]): StaffMember[] {
  const linked = new Set(users.map((user) => user.technicianId).filter((id): id is number => id != null))
  const rows = users.map((user) => {
    const tech = user.technicianId ? technicians.find((item) => item.id === user.technicianId) : undefined
    return toMember(user, tech)
  })
  for (const tech of technicians) {
    if (tech.userId || linked.has(tech.id)) continue
    rows.push(toMember(undefined, tech))
  }
  return rows
}

function toMember(user: LoginAccount | undefined, tech: Technician | undefined): StaffMember {
  if (user) {
    const shown = user.role === 'TECHNICIAN' ? tech : undefined
    return {
      ref: `user-${user.id}`,
      userId: user.id,
      technicianId: shown?.id,
      name: user.name,
      mobile: user.mobile,
      role: user.role,
      status: user.status,
      remark: user.remark,
      username: user.username,
      loginAccount: true,
      skills: shown?.skills ?? [],
      region: shown?.region ?? '',
    }
  }
  return {
    ref: `tech-${tech!.id}`,
    technicianId: tech!.id,
    name: tech!.name,
    mobile: tech!.mobile,
    role: 'TECHNICIAN',
    status: tech!.status === 'DISABLED' ? 'DISABLED' : 'ENABLED',
    remark: tech!.remark ?? '',
    loginAccount: false,
    skills: tech!.skills ?? [],
    region: tech!.region ?? '',
  }
}

interface Located {
  user?: LoginAccount
  tech?: Technician
}

function locate(store: Store, ref: string): Located {
  const match = /^(user|tech)-(\d+)$/.exec(ref)
  if (!match) throw new ApiError(4040, '员工不存在')
  const id = Number(match[2])
  if (match[1] === 'user') {
    const user = store.users.find((item) => item.id === id)
    if (!user) throw new ApiError(4040, '员工不存在')
    const tech = user.technicianId ? store.technicians.find((item) => item.id === user.technicianId) : undefined
    return { user, tech }
  }
  const tech = store.technicians.find((item) => item.id === id)
  if (!tech) throw new ApiError(4040, '员工不存在')
  if (tech.userId) {
    const user = store.users.find((item) => item.id === tech.userId)
    if (user) return { user, tech }
  }
  return { tech }
}

interface Parsed {
  name: string
  mobile: string
  role: StaffRole
  status: StaffStatus
  remark: string
  username?: string
  password?: string
  skills: string[]
  region: string
}

function parse(body: StaffSave, creating: boolean, existing: LoginAccount | undefined): Parsed {
  const name = requireText(body.name, '姓名', 50)
  const mobile = requireText(body.mobile, '手机号', 20)
  if (!/^1\d{10}$/.test(mobile)) throw new ApiError(1001, '手机号应为 11 位数字')
  const role = (body.role ?? '').trim().toUpperCase()
  if (role !== 'ADMIN' && role !== 'SALES' && role !== 'TECHNICIAN' && role !== 'BOSS') {
    throw new ApiError(1001, '角色只能是老板、管理员、业务员或师傅')
  }
  const status = body.status ? normalizeStatus(body.status) : 'ENABLED'
  const remark = limit(body.remark?.trim() ?? '', '备注', 500)
  let username = body.username?.trim() || ''
  if (!username && existing && (role !== 'TECHNICIAN' || existing.username)) username = existing.username
  if (username && (username.length < 3 || username.length > 64 || /\s/.test(username))) {
    throw new ApiError(1001, '用户名需要 3 到 64 位，且不能有空格')
  }
  const password = body.password?.trim() || ''
  const needLogin = role === 'ADMIN' || role === 'SALES' || role === 'BOSS'
  if (needLogin && !username) throw new ApiError(1001, '老板、管理员和业务员必须填写用户名和密码')
  if (creating && needLogin && !password) throw new ApiError(1001, '老板、管理员和业务员必须填写用户名和密码')
  if (creating && role === 'TECHNICIAN' && username && !password) throw new ApiError(1001, '开通登录请设置密码')
  if (password) checkPassword(password)
  const skills = (body.skills ?? []).map((item) => item.trim()).filter(Boolean)
  if (skills.join(',').length > 255) throw new ApiError(1001, '技能太长')
  const region = limit(body.region?.trim() ?? '', '区域', 128)
  return {
    name,
    mobile,
    role,
    status,
    remark,
    username: username || undefined,
    password: password || undefined,
    skills,
    region,
  }
}

function pushUser(store: Store, parsed: Parsed, technicianId: number | undefined): LoginAccount {
  if (!parsed.username || !parsed.password) throw new ApiError(1001, '请填写用户名和密码')
  assertUsernameFree(store.users, parsed.username)
  const user: LoginAccount = {
    id: store.nextId(),
    username: parsed.username,
    password: parsed.password,
    name: parsed.name,
    mobile: parsed.mobile,
    role: parsed.role,
    status: parsed.status,
    remark: parsed.remark,
    technicianId,
  }
  store.users.push(user)
  return user
}

function pushTech(store: Store, parsed: Parsed, userId: number | undefined): Technician {
  const tech: Technician = {
    id: store.nextId(),
    name: parsed.name,
    mobile: parsed.mobile,
    skills: parsed.skills,
    region: parsed.region,
    rating: 5,
    status: parsed.status,
    remark: parsed.remark,
    userId,
  }
  store.technicians.push(tech)
  return tech
}

function applyTech(tech: Technician, parsed: Parsed): void {
  tech.name = parsed.name
  tech.mobile = parsed.mobile
  tech.skills = parsed.skills
  tech.region = parsed.region
  tech.remark = parsed.remark
}

function linkedTech(store: Store, user: LoginAccount, already: Technician | undefined): Technician | undefined {
  if (already) return already
  if (!user.technicianId) return undefined
  return store.technicians.find((item) => item.id === user.technicianId)
}

function assertUsernameFree(users: LoginAccount[], username: string): void {
  if (users.some((item) => item.username === username)) throw new ApiError(4090, '用户名已存在')
}

function assertKeepsAdmin(users: LoginAccount[], user: LoginAccount, nextRole: StaffRole, nextStatus: StaffStatus): void {
  const was = user.role === 'ADMIN' && user.status !== 'DISABLED'
  const still = nextRole === 'ADMIN' && nextStatus !== 'DISABLED'
  if (!was || still) return
  const others = users.filter((item) => item.id !== user.id && item.role === 'ADMIN' && item.status === 'ENABLED')
  if (others.length === 0) throw new ApiError(3003, '不能停用或改掉最后一位管理员')
}

/** 当前登录的老板不能把自己改成别的角色，否则没有人能再管员工。停用自己在调用方已经拒绝。 */
function assertKeepsOwnBoss(user: LoginAccount, nextRole: StaffRole, operatorId: number): void {
  if (user.id !== operatorId || user.role !== 'BOSS' || nextRole === 'BOSS') return
  throw new ApiError(3003, '不能改掉自己的老板角色')
}

function normalizeStatus(status: string): StaffStatus {
  const text = status.trim().toUpperCase()
  if (text !== 'ENABLED' && text !== 'DISABLED') throw new ApiError(1001, '状态只能是启用或停用')
  return text
}

function checkPassword(password: string): string {
  const raw = password.trim()
  if (raw.length < 6) throw new ApiError(1001, '密码至少 6 位')
  if (raw.length > 64) throw new ApiError(1001, '密码不能超过 64 位')
  return raw
}

function requireText(value: string | undefined, label: string, max: number): string {
  const text = value?.trim() ?? ''
  if (!text) throw new ApiError(1001, `请填写${label}`)
  if (text.length > max) throw new ApiError(1001, `${label}不能超过 ${max} 个字`)
  return text
}

function limit(value: string, label: string, max: number): string {
  if (value.length > max) throw new ApiError(1001, `${label}不能超过 ${max} 个字`)
  return value
}
