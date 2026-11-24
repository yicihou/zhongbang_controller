import { describe, expect, it } from 'vitest'
import type { RepairTicket, Technician } from '../src/api/types'
import { ApiError } from '../src/domain/errors'
import type { IdSource } from '../src/domain/order'
import { assignRepair, offlineConfirmRepair, quoteRepair } from '../src/domain/repair'

function ids(): IdSource {
  let n = 50
  return { next: () => ++n }
}

function ticket(): RepairTicket {
  return {
    id: 9,
    ticketNo: 'RT1',
    customerId: 2,
    customerName: '李淑芬',
    status: 'SUBMITTED',
    contactMobile: '13900001111',
    createdAt: '2026-11-24 08:00:00',
    updatedAt: '2026-11-24 08:00:00',
    lines: [
      {
        id: 1,
        ticketId: 9,
        lineNo: 1,
        brand: '杰克',
        model: '电脑平缝机 A5',
        sn: 'A5-1',
        source: 'INTERNAL',
        faultType: '跳针',
        faultDesc: '跳针',
        images: ['a.jpg'],
      },
      {
        id: 2,
        ticketId: 9,
        lineNo: 2,
        brand: '兄弟',
        model: '包缝机 757',
        source: 'EXTERNAL',
        faultType: '不切线',
        faultDesc: '外购',
        images: ['b.jpg'],
        purchaseChannel: '淘宝',
      },
    ],
    payments: [],
  }
}

const technicians: Technician[] = [
  { id: 90, name: '陈静', mobile: '13800001003', skills: ['电控维修'], region: '全市', rating: 4.8 },
]

describe('repair admin flow', () => {
  const now = '2026-11-24 12:00:00'

  it('quotes an out-of-warranty ticket into a repair payment', () => {
    const quoted = quoteRepair(
      ticket(),
      {
        inWarranty: false,
        lines: [
          { lineId: 1, lineQuoteAmount: 8000 },
          { lineId: 2, lineQuoteAmount: 12000 },
        ],
      },
      now,
      ids(),
    )
    expect(quoted.status).toBe('PENDING_PAYMENT')
    expect(quoted.payableAmount).toBe(20000)
    expect(quoted.payments[0]?.type).toBe('REPAIR')
    expect(quoted.payments[0]?.amount).toBe(20000)
    expect(quoted.lines[1]?.source).toBe('EXTERNAL')
  })

  it('confirms warranty work without a payment and then assigns a technician', () => {
    const confirmed = quoteRepair(
      ticket(),
      {
        inWarranty: true,
        lines: [
          { lineId: 1, lineQuoteAmount: 0 },
          { lineId: 2, lineQuoteAmount: 0 },
        ],
      },
      now,
      ids(),
    )
    expect(confirmed.status).toBe('CONFIRMED')
    expect(confirmed.payments.filter((item) => item.status === 'WAIT')).toHaveLength(0)
    const assigned = assignRepair(
      confirmed,
      [],
      technicians,
      { lineId: 1, technicianId: 90, appointAt: '2026-11-26 09:30:00' },
      now,
      ids(),
    )
    expect(assigned.ticket.status).toBe('IN_SERVICE')
    expect(assigned.ticket.lines[0]?.technicianName).toBe('陈静')
    expect(assigned.schedules[0]?.timeSlot).toBe('上午')
    expect(assigned.schedules[0]?.used).toBe(1)
  })

  it('collects the repair fee offline before dispatch', () => {
    const quoted = quoteRepair(
      ticket(),
      { inWarranty: false, lines: [{ lineId: 1, lineQuoteAmount: 5000 }, { lineId: 2, lineQuoteAmount: 0 }] },
      now,
      ids(),
    )
    expect(() =>
      assignRepair(quoted, [], technicians, { lineId: 1, technicianId: 90, appointAt: '2026-11-26 15:00:00' }, now, ids()),
    ).toThrow(ApiError)
    const paid = offlineConfirmRepair(quoted, quoted.payments[0]!.id, { voucherUrls: ['维修费转账'] }, '周晓', 2, now)
    expect(paid.status).toBe('PAID')
    const assigned = assignRepair(
      paid,
      [],
      technicians,
      { lineId: 2, technicianId: 90, appointAt: '2026-11-26 15:00:00' },
      now,
      ids(),
    )
    expect(assigned.ticket.status).toBe('IN_SERVICE')
    expect(assigned.schedules[0]?.timeSlot).toBe('下午')
  })
})
