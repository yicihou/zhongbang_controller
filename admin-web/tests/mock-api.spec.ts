import { beforeEach, describe, expect, it } from 'vitest'
import { paths } from '../src/api/paths'
import type { AdminOrder, PageResult, Payment, Receivable, StaffLoginResponse } from '../src/api/types'
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

  it('records a partial offline payment and lists the split rest as a receivable', () => {
    const login = handle('POST', paths.staffLogin, {}, { username: 'admin', password: 'admin123' }, '')
    const token = (login.body.data as StaffLoginResponse).token
    const before = handle('GET', paths.order(910233001), {}, null, token).body.data as AdminOrder
    const wait = before.payments.find((item) => item.status === 'WAIT')!

    const tooMuch = handle('POST', paths.offlineConfirm(wait.id), {}, { amount: wait.amount + 1, method: 'CASH' }, token)
    expect(tooMuch.body.code).toBe(1001)

    const res = handle('POST', paths.offlineConfirm(wait.id), {}, { amount: 100000, method: 'BANK_TRANSFER' }, token)
    expect(res.body.code).toBe(0)
    expect(res.body.data as Payment).toMatchObject({ status: 'SUCCESS', amount: 100000, offlineMethod: 'BANK_TRANSFER' })

    const after = handle('GET', paths.order(910233001), {}, null, token).body.data as AdminOrder
    expect(after.status).toBe('PARTIALLY_PAID')
    expect(after.paidAmount).toBe(100000)
    const rest = after.payments.find((item) => item.status === 'WAIT')!
    expect(rest.amount).toBe(wait.amount - 100000)

    const receivables = handle('GET', paths.receivables, { page: '1', size: '50' }, null, token).body
      .data as PageResult<Receivable>
    const row = receivables.list.find((item) => item.paymentId === rest.id)!
    expect(row.amount).toBe(rest.amount)

    handle('POST', paths.offlineConfirm(rest.id), {}, { method: 'WECHAT_TRANSFER' }, token)
    const paid = handle('GET', paths.order(910233001), {}, null, token).body.data as AdminOrder
    expect(paid.status).toBe('PAID')
  })
})
