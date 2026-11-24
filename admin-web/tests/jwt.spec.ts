/** 测前端如何识别员工令牌：Mock 签发的 STAFF 能通过，客户令牌和过期令牌不能。不连后端。 */
import { describe, expect, it } from 'vitest'
import { decodeJwtPayload, isStaffPayload, issueMockStaffToken } from '../src/domain/jwt'

describe('jwt role', () => {
  it('issues a STAFF token the client can read', () => {
    const token = issueMockStaffToken({ id: 1, name: '李敏' }, 1_700_000_000)
    const payload = decodeJwtPayload(token)
    expect(payload?.type).toBe('STAFF')
    expect(payload?.role).toBe('ROLE_STAFF')
    expect(payload?.name).toBe('李敏')
    expect(payload?.sub).toBe('1')
    expect(isStaffPayload(payload, 1_700_000_000)).toBe(true)
  })

  it('rejects customer tokens and expired staff tokens', () => {
    const header = btoa(JSON.stringify({ alg: 'none' })).replace(/=+$/g, '')
    const customer = `${header}.${btoa(JSON.stringify({ type: 'CUSTOMER', exp: 9_999_999_999 })).replace(/=+$/g, '')}.x`
    expect(isStaffPayload(decodeJwtPayload(customer), 10)).toBe(false)
    expect(isStaffPayload({ type: 'STAFF', exp: 10 }, 11)).toBe(false)
    expect(isStaffPayload(null)).toBe(false)
  })
})
