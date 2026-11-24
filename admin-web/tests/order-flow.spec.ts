/** 测 domain/order.ts：改价、运费、发货、派工、取消等规则，不经过 HTTP。 */
import { describe, expect, it } from 'vitest'
import type { AdminOrder, Technician, TechnicianSchedule } from '../src/api/types'
import { ApiError } from '../src/domain/errors'
import {
  addShipment,
  adjustPrice,
  assignInstall,
  cancelOrder,
  createPayments,
  installFeeState,
  offlineConfirmOrder,
  setFreight,
  type IdSource,
} from '../src/domain/order'

function ids(start = 100): IdSource {
  let n = start
  return { next: () => ++n }
}

function order(): AdminOrder {
  return {
    id: 1,
    orderNo: 'SO1',
    customerId: 1,
    customerName: '张建国',
    customerMobile: '13800000000',
    status: 'PENDING_PAYMENT',
    goodsAmount: 337000,
    installAmount: 0,
    freightAmount: 0,
    adjustAmount: 0,
    payableAmount: 337000,
    paidAmount: 0,
    priceAdjusted: false,
    createdAt: '2026-11-24 09:00:00',
    updatedAt: '2026-11-24 09:00:00',
    items: [
      {
        id: 11,
        orderId: 1,
        skuId: 501,
        productName: '电脑平缝机 A5',
        skuName: '标准款',
        quantity: 1,
        unitPrice: 328000,
        lineAmount: 328000,
        deliveryMethod: 'ON_SITE_INSTALL',
        installFee: null,
      },
      {
        id: 12,
        orderId: 1,
        skuId: 502,
        productName: '旋梭组件',
        skuName: '通用',
        quantity: 2,
        unitPrice: 4500,
        lineAmount: 9000,
        deliveryMethod: 'EXPRESS',
        installFee: 0,
      },
    ],
    payments: [
      {
        id: 21,
        bizType: 'ORDER',
        bizId: 1,
        paymentNo: 'SO1-F-1',
        channel: 'OFFLINE',
        type: 'FULL',
        title: '全款',
        amount: 337000,
        status: 'WAIT',
        sortNo: 1,
        createdAt: '2026-11-24 09:00:00',
        updatedAt: '2026-11-24 09:00:00',
      },
    ],
    priceLogs: [],
  }
}

const technicians: Technician[] = [
  { id: 88, name: '王建国', mobile: '13800001001', skills: ['平缝机'], region: '城东', rating: 4.9 },
]

describe('order admin flow', () => {
  const now = '2026-11-24 10:00:00'

  it('adjusts line price and install fee, and rejects install fee on express lines', () => {
    const adjusted = adjustPrice(
      order(),
      {
        reason: '微信沟通后优惠',
        adjustAmount: -5000,
        lines: [
          { orderItemId: 11, unitPrice: 320000, installFee: 8000 },
          { orderItemId: 12, unitPrice: 4000 },
        ],
      },
      '李敏',
      now,
      ids(),
    )
    expect(adjusted.goodsAmount).toBe(328000)
    expect(adjusted.installAmount).toBe(8000)
    expect(adjusted.payableAmount).toBe(331000)
    expect(adjusted.payments[0]?.amount).toBe(331000)
    expect(adjusted.priceAdjusted).toBe(true)
    expect(adjusted.priceLogs.length).toBeGreaterThan(0)
    expect(() =>
      adjustPrice(order(), { reason: '不行', lines: [{ orderItemId: 12, installFee: 100 }] }, '李敏', now, ids()),
    ).toThrow(ApiError)
  })

  it('tells an unset install fee apart from an explicit zero', () => {
    const pending = order()
    expect(installFeeState(pending)).toEqual({ kind: 'pending', amount: 0 })

    const zero = adjustPrice(
      pending,
      { reason: '老客户免安装费', lines: [{ orderItemId: 11, installFee: 0 }] },
      '李敏',
      now,
      ids(),
    )
    expect(zero.items[0]?.installFee).toBe(0)
    expect(installFeeState(zero)).toEqual({ kind: 'set', amount: 0 })
    expect(zero.priceLogs[0]).toMatchObject({ field: 'installFee', beforeAmount: null, afterAmount: 0 })
    expect(zero.payableAmount).toBe(pending.payableAmount)

    const untouched = adjustPrice(pending, { reason: '只改单价', lines: [{ orderItemId: 12, unitPrice: 4000 }] }, '李敏', now, ids())
    expect(untouched.items[0]?.installFee).toBeNull()
    expect(installFeeState(untouched).kind).toBe('pending')

    const noInstall = order()
    noInstall.items = noInstall.items.filter((item) => item.deliveryMethod !== 'ON_SITE_INSTALL')
    expect(installFeeState(noInstall)).toEqual({ kind: 'none' })
  })

  it('adds freight into the open full payment, then splits deposit and balance', () => {
    const withFreight = setFreight(order(), { freightAmount: 2000 }, '李敏', now, ids())
    expect(withFreight.freightAmount).toBe(2000)
    expect(withFreight.payableAmount).toBe(339000)
    expect(withFreight.payments.find((item) => item.status === 'WAIT')?.amount).toBe(339000)

    const split = createPayments(
      withFreight,
      {
        mode: 'SPLIT',
        payments: [
          { type: 'DEPOSIT', title: '定金', amount: 100000 },
          { type: 'BALANCE', title: '尾款', amount: 239000 },
        ],
      },
      now,
      ids(),
    )
    expect(split.payments.find((item) => item.type === 'FULL')?.status).toBe('CLOSED')
    expect(split.status).toBe('PENDING_PAYMENT')
    const depositPaid = offlineConfirmOrder(
      split,
      split.payments.find((item) => item.type === 'DEPOSIT')!.id,
      { voucherUrls: ['对公 8891'] },
      '李敏',
      1,
      now,
    )
    expect(depositPaid.status).toBe('PARTIALLY_PAID')
    expect(depositPaid.paidAmount).toBe(100000)
    const paid = offlineConfirmOrder(
      depositPaid,
      depositPaid.payments.find((item) => item.type === 'BALANCE')!.id,
      { remark: '尾款已到' },
      '李敏',
      1,
      now,
    )
    expect(paid.status).toBe('PAID')
    expect(paid.paidAmount).toBe(339000)
  })

  it('ships by line only after payment and blocks a second shipment', () => {
    expect(() => addShipment(order(), { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF1' }, now, ids())).toThrow(
      /付款结清/,
    )
    let current = offlineConfirmOrder(order(), 21, { voucherUrls: ['现金'] }, '李敏', 1, now)
    current = addShipment(current, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF1' }, now, ids())
    expect(current.status).toBe('FULFILLING')
    expect(current.items[1]?.shipment?.trackingNo).toBe('SF1')
    expect(() => addShipment(current, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF2' }, now, ids())).toThrow(
      /已有发货/,
    )
  })

  it('assigns a technician and refuses an overbooked slot', () => {
    const schedules: TechnicianSchedule[] = [
      { id: 1, technicianId: 88, serviceDate: '2026-11-28', timeSlot: '上午', capacity: 1, used: 1 },
    ]
    expect(() =>
      assignInstall(
        order(),
        schedules,
        technicians,
        { orderItemId: 11, technicianId: 88, installDate: '2026-11-28', installSlot: '上午' },
        now,
        ids(),
      ),
    ).toThrow(ApiError)
    const open: TechnicianSchedule[] = []
    const assigned = assignInstall(
      order(),
      open,
      technicians,
      { orderItemId: 11, technicianId: 88, installDate: '2026-11-28', installSlot: '下午' },
      now,
      ids(),
    )
    expect(assigned.order.items[0]?.technicianName).toBe('王建国')
    expect(assigned.order.status).toBe('PENDING_PAYMENT')
    expect(assigned.schedules[0]?.used).toBe(1)
  })

  it('cancels an unpaid order and refuses cancel after a successful payment', () => {
    const cancelled = cancelOrder(order(), { reason: '客户不要了' }, now)
    expect(cancelled.status).toBe('CANCELLED')
    expect(cancelled.payments[0]?.status).toBe('CLOSED')
    const paid = offlineConfirmOrder(order(), 21, { voucherUrls: ['转账'] }, '李敏', 1, now)
    expect(() => cancelOrder(paid, { reason: '想退' }, now)).toThrow(/退款/)
  })
})
