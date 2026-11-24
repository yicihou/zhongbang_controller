import { beforeEach, describe, expect, it } from 'vitest'
import { paths } from '../src/api/paths'
import type { AdminOrder, PageResult, StaffLoginResponse } from '../src/api/types'
import { handle } from '../src/mock/handle'
import { resetDb } from '../src/mock/db'

describe('mock admin api', () => {
  beforeEach(() => resetDb())

  it('logs in a staff user and lists orders with the page envelope', () => {
    const denied = handle('GET', paths.orders, {}, null, '')
    expect(denied.body.code).toBe(2001)

    const login = handle('POST', paths.staffLogin, {}, { username: 'admin', password: 'admin123' }, '')
    expect(login.body.code).toBe(0)
    const token = (login.body.data as StaffLoginResponse).token
    const page = handle('GET', paths.orders, { status: 'PENDING_PAYMENT', page: '1', size: '10' }, null, token)
    expect(page.body.code).toBe(0)
    const data = page.body.data as PageResult<AdminOrder>
    expect(data.page).toBe(1)
    expect(data.list.length).toBeGreaterThan(0)
    expect(data.list.every((item) => item.status === 'PENDING_PAYMENT')).toBe(true)
    expect(page.body.traceId).toBeTruthy()
  })

  it('rejects a non-staff token', () => {
    const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url')
    const payload = Buffer.from(JSON.stringify({ type: 'CUSTOMER', name: '张', exp: 4_000_000_000 })).toString('base64url')
    const res = handle('GET', paths.repairs, {}, null, `${header}.${payload}.x`)
    expect(res.body.code).toBe(2003)
  })
})
