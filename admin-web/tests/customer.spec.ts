/** 客户模块：业务员进不去，老板能看到种子客户，分析摘要不带完整手机号。 */
import { beforeEach, describe, expect, it } from 'vitest'
import { paths } from '../src/api/paths'
import type { PageResult, StaffLoginResponse } from '../src/api/types'
import { insightPrompt, maskMobile, type CustomerListItem } from '../src/domain/customer'
import { isBossPayload, decodeJwtPayload } from '../src/domain/jwt'
import { handle } from '../src/mock/handle'
import { resetDb } from '../src/mock/db'

describe('customer module', () => {
  beforeEach(() => resetDb())

  it('hides the customer list from clerks and shows it to the boss', () => {
    const clerk = (handle('POST', paths.staffLogin, {}, { username: 'clerk', password: 'clerk123' }, '').body.data as StaffLoginResponse).token
    expect(handle('GET', paths.customers, {}, null, clerk).body.code).toBe(2003)

    const bossLogin = handle('POST', paths.staffLogin, {}, { username: 'boss', password: 'boss123' }, '')
    const boss = bossLogin.body.data as StaffLoginResponse
    expect(isBossPayload(decodeJwtPayload(boss.token))).toBe(true)
    const named = handle('GET', paths.customers, { name: '张建国' }, null, boss.token).body.data as PageResult<CustomerListItem>
    expect(named.list[0]?.name).toBe('张建国')
    expect(named.list[0]?.mobile).toBe('13812348000')
    const page = handle('GET', paths.customers, { size: '100' }, null, boss.token).body.data as PageResult<CustomerListItem>
    expect(page.total).toBeGreaterThanOrEqual(30)

    const overdue = handle('GET', paths.customers, { unpaidOnly: '1' }, null, boss.token).body.data as PageResult<CustomerListItem>
    expect(overdue.list.some((row) => row.overdue)).toBe(true)
  })

  it('masks phone numbers in the analysis summary', () => {
    expect(maskMobile('13812348000')).toBe('138****8000')
    const prompt = insightPrompt(
      {
        id: 1,
        name: '张',
        mobile: '13812348000',
        altMobile: '13900001111',
        wechat: 'wx',
        customerType: 'FACTORY',
        shopName: '厂',
        machineCount: 1,
        mainProducts: '',
        source: 'PHONE',
        referrer: '',
        birthday: '',
        remark: '',
        tags: '',
        region: '杭州市西湖区',
        levelCode: 'NORMAL',
        levelManual: false,
        createdAt: '',
      },
      {
        orderAmount: 1,
        receivedAmount: 1,
        unpaidAmount: 0,
        orderCount: 1,
        avgAmount: 1,
        firstOrderAt: '',
        lastOrderAt: '',
        daysSinceLast: 1,
      },
      [{ id: 1, customerId: 1, region: '杭州市西湖区', detail: '文三路 188 号', contactName: '', contactMobile: '', defaultAddress: true, source: 'MANUAL' }],
    )
    expect(prompt).not.toContain('13812348000')
    expect(prompt).not.toContain('文三路')
    expect(prompt).toContain('****')
  })
})
