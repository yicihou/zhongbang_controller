/** 员工管理的 Mock 规则：老板能增改停用，管理员和业务员不能，停用师傅不能派工。管理员仍能拿启用中的师傅名单。 */
import { beforeEach, describe, expect, it } from 'vitest'
import { paths } from '../src/api/paths'
import type { PageResult, RepairTicket, StaffLoginResponse, Technician } from '../src/api/types'
import { isBossPayload, decodeJwtPayload } from '../src/domain/jwt'
import { imageFileError, isImageUrl } from '../src/domain/image'
import type { StaffMember } from '../src/domain/staff'
import { assignRepair } from '../src/domain/repair'
import { handle } from '../src/mock/handle'
import { resetDb } from '../src/mock/db'

describe('staff and voucher images', () => {
  beforeEach(() => resetDb())

  it('lets a boss manage staff and still lets an admin list technicians for dispatch', () => {
    const bossLogin = handle('POST', paths.staffLogin, {}, { username: 'boss', password: 'boss123' }, '')
    expect(bossLogin.body.code).toBe(0)
    const boss = bossLogin.body.data as StaffLoginResponse
    expect(boss.staff.role).toBe('ROLE_BOSS')
    expect(isBossPayload(decodeJwtPayload(boss.token))).toBe(true)

    const adminLogin = handle('POST', paths.staffLogin, {}, { username: 'admin', password: 'admin123' }, '')
    const admin = adminLogin.body.data as StaffLoginResponse
    expect(handle('GET', paths.staff, {}, null, admin.token).body.code).toBe(2003)
    expect(
      handle('POST', paths.staff, {}, { name: '不该建成', mobile: '13900001111', role: 'SALES', username: 'nope1', password: 'sales123' }, admin.token).body.code,
    ).toBe(2003)

    const clerkLogin = handle('POST', paths.staffLogin, {}, { username: 'clerk', password: 'clerk123' }, '')
    const clerk = (clerkLogin.body.data as StaffLoginResponse).token
    expect(handle('GET', paths.staff, {}, null, clerk).body.code).toBe(2003)

    const created = handle(
      'POST',
      paths.staff,
      {},
      { name: '赵师傅', mobile: '13922223333', role: 'TECHNICIAN', skills: ['平缝机'], region: '城南' },
      boss.token,
    )
    expect(created.body.code).toBe(0)
    const member = created.body.data as StaffMember
    expect(member.loginAccount).toBe(false)
    expect(member.ref.startsWith('tech-')).toBe(true)

    const listed = handle('GET', paths.technicians, {}, null, admin.token).body.data as Technician[]
    expect(listed.some((item) => item.id === member.technicianId && item.name === '赵师傅' && item.mobile === '13922223333')).toBe(true)

    const disabled = handle('POST', paths.staffStatus(member.ref), {}, { status: 'DISABLED' }, boss.token)
    expect(disabled.body.code).toBe(0)
    const after = handle('GET', paths.technicians, {}, null, admin.token).body.data as Technician[]
    expect(after.some((item) => item.id === member.technicianId)).toBe(false)

    const techs: Technician[] = [
      { id: member.technicianId!, name: '赵师傅', mobile: '13922223333', skills: ['平缝机'], region: '城南', rating: 5, status: 'DISABLED' },
    ]
    const ticket: RepairTicket = {
      id: 1,
      ticketNo: 'RT1',
      customerId: 1,
      customerName: '张',
      contactMobile: '13800000000',
      status: 'CONFIRMED',
      createdAt: '2026-11-25 10:00:00',
      updatedAt: '2026-11-25 10:00:00',
      lines: [
        {
          id: 1,
          ticketId: 1,
          lineNo: 1,
          brand: '外牌',
          model: 'M8',
          source: 'EXTERNAL',
          faultType: 'NOISE',
          faultDesc: '响',
          images: [],
        },
      ],
      payments: [],
    }
    expect(() =>
      assignRepair(ticket, [], techs, { lineId: 1, technicianId: member.technicianId!, appointAt: '2026-12-08 09:30:00' }, '2026-11-25 10:00:00', {
        next: () => 1,
      }),
    ).toThrow(/停用/)

    const self = handle('POST', paths.staffStatus('user-3'), {}, { status: 'DISABLED' }, boss.token)
    expect(self.body.code).toBe(3003)
    const demote = handle(
      'PUT',
      paths.staffOne('user-3'),
      {},
      { name: '老板', mobile: '13900000000', role: 'ADMIN', status: 'ENABLED' },
      boss.token,
    )
    expect(demote.body.code).toBe(3003)

    const page = handle('GET', paths.staff, { keyword: '赵师傅' }, null, boss.token).body.data as PageResult<StaffMember>
    expect(page.list[0]?.status).toBe('DISABLED')
  })

  it('stores uploaded image urls and leaves plain text vouchers as text', () => {
    expect(imageFileError('a.png', 'image/png', 100)).toBe('')
    expect(imageFileError('a.gif', 'image/gif', 100)).toBe('只支持 jpg、png、webp')
    expect(imageFileError('a.png', 'image/png', 11 * 1024 * 1024)).toBe('图片不能超过 10MB')
    expect(isImageUrl('/uploads/payment/a.jpg')).toBe(true)
    expect(isImageUrl('对公 8891')).toBe(false)

    const token = (handle('POST', paths.staffLogin, {}, { username: 'admin', password: 'admin123' }, '').body.data as StaffLoginResponse).token
    const denied = handle('POST', paths.uploads, {}, { files: [] }, '')
    expect(denied.body.code).toBe(2001)
    const uploaded = handle(
      'POST',
      paths.uploads,
      { biz: 'payment' },
      { files: [{ name: 'shot.png', type: 'image/png', size: 120, dataUrl: 'data:image/png;base64,xx' }] },
      token,
    )
    expect(uploaded.body.code).toBe(0)
    const url = (uploaded.body.data as { url: string }[])[0]?.url
    expect(url?.startsWith('data:image/png')).toBe(true)

    const bad = handle('POST', paths.uploads, {}, { files: [{ name: 'note.txt', type: 'text/plain', size: 4, dataUrl: 'data:text/plain,hi' }] }, token)
    expect(bad.body.code).toBe(1001)
  })
})
