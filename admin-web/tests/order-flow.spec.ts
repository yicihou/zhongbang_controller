/** 测 domain/order.ts：未付款可发货、部分收款拆成余款、派工、取消。不经过 HTTP。 */
import { describe, expect, it } from 'vitest'
import type { AdminOrder, Technician, TechnicianSchedule } from '../src/api/types'
import { ApiError } from '../src/domain/errors'
import {
  addShipment,
  adjustPrice,
  assignInstall,
  canShipItem,
  cancelOrder,
  createPayments,
  fulfillmentProgress,
  installFeeState,
  offlineAmountError,
  offlineConfirmOrder,
  setFreight,
  syncOrderStatus,
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
      { method: 'BANK_TRANSFER', voucherUrls: ['对公 8891'] },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    expect(depositPaid.status).toBe('PARTIALLY_PAID')
    expect(depositPaid.paidAmount).toBe(100000)
    const paid = offlineConfirmOrder(
      depositPaid,
      depositPaid.payments.find((item) => item.type === 'BALANCE')!.id,
      { method: 'BANK_TRANSFER', remark: '尾款已到' },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    expect(paid.status).toBe('PAID')
    expect(paid.paidAmount).toBe(339000)
  })

  it('ships before payment, keeps the payment status, and moves to FULFILLING once paid', () => {
    const unpaid = order()
    expect(canShipItem(unpaid, unpaid.items[1]!)).toBe(true)
    let current = addShipment(unpaid, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF1' }, now, ids())
    expect(current.status).toBe('PENDING_PAYMENT')
    expect(current.items[1]?.shipment?.trackingNo).toBe('SF1')
    expect(fulfillmentProgress(current)).toEqual({ shipped: 1, done: 0, total: 2 })
    expect(canShipItem(current, current.items[1]!)).toBe(false)
    expect(() => addShipment(current, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF2' }, now, ids())).toThrow(
      /已有发货/,
    )
    expect(() => cancelOrder(current, { reason: '不要了' }, now)).toThrow(/已有发货/)

    current = offlineConfirmOrder(
      current,
      21,
      { method: 'BANK_TRANSFER', voucherUrls: ['现金'] },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    expect(current.status).toBe('FULFILLING')
  })

  it('keeps PARTIALLY_PAID after shipping and completes only when paid and every line is done', () => {
    let current = createPayments(
      order(),
      {
        mode: 'SPLIT',
        payments: [
          { type: 'DEPOSIT', title: '定金', amount: 100000 },
          { type: 'BALANCE', title: '尾款', amount: 237000 },
        ],
      },
      now,
      ids(),
    )
    current = offlineConfirmOrder(
      current,
      current.payments.find((p) => p.type === 'DEPOSIT')!.id,
      { method: 'BANK_TRANSFER', remark: '定金' },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    current = addShipment(current, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF9' }, now, ids())
    const assigned = assignInstall(
      current,
      [],
      technicians,
      { orderItemId: 11, technicianId: 88, installDate: '2026-11-28', installSlot: '上午' },
      now,
      ids(),
    )
    current = assigned.order
    expect(current.status).toBe('PARTIALLY_PAID')
    expect(fulfillmentProgress(current).shipped).toBe(2)

    for (const item of current.items) item.shipment!.status = 'DONE'
    syncOrderStatus(current)
    expect(current.status).toBe('PARTIALLY_PAID')

    current = offlineConfirmOrder(
      current,
      current.payments.find((p) => p.type === 'BALANCE')!.id,
      { method: 'BANK_TRANSFER', remark: '尾款' },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    expect(current.status).toBe('COMPLETED')
  })

  it('still refuses shipping and dispatch on a cancelled order', () => {
    const cancelled = cancelOrder(order(), { reason: '客户不要了' }, now)
    expect(canShipItem(cancelled, cancelled.items[1]!)).toBe(false)
    expect(() => addShipment(cancelled, { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF1' }, now, ids())).toThrow(
      /已取消/,
    )
    expect(() =>
      assignInstall(
        cancelled,
        [],
        technicians,
        { orderItemId: 11, technicianId: 88, installDate: '2026-11-28', installSlot: '上午' },
        now,
        ids(),
      ),
    ).toThrow(/已取消/)
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
    expect(assigned.order.items[0]?.shipment?.method).toBe('ON_SITE_INSTALL')
    expect(assigned.order.status).toBe('PENDING_PAYMENT')
    expect(assigned.schedules[0]?.used).toBe(1)
  })

  it('cancels an unpaid order and refuses cancel after a successful payment', () => {
    const cancelled = cancelOrder(order(), { reason: '客户不要了' }, now)
    expect(cancelled.status).toBe('CANCELLED')
    expect(cancelled.payments[0]?.status).toBe('CLOSED')
    const paid = offlineConfirmOrder(
      order(),
      21,
      { method: 'BANK_TRANSFER', voucherUrls: ['转账'] },
      { name: '李敏', id: 1 },
      now,
      ids(500),
    )
    expect(() => cancelOrder(paid, { reason: '想退' }, now)).toThrow(/退款/)
  })
})

describe('offline confirm with amount', () => {
  const now = '2026-11-24 10:00:00'
  const staff = { name: '李敏', id: 1 }

  it('validates the amount against the remaining balance', () => {
    expect(offlineAmountError(337000, 337000)).toBeNull()
    expect(offlineAmountError(1, 337000)).toBeNull()
    expect(offlineAmountError(0, 337000)).toMatch(/大于 0/)
    expect(offlineAmountError(-100, 337000)).toMatch(/大于 0/)
    expect(offlineAmountError(337001, 337000)).toMatch(/不能超过剩余 ¥3,370.00/)
    expect(offlineAmountError(100.5, 337000)).toMatch(/精确到分/)
    expect(offlineAmountError(null, 337000)).toMatch(/请填写/)
  })

  it('records a partial payment, splits the rest into a new WAIT, then reaches PAID', () => {
    const first = offlineConfirmOrder(
      order(),
      21,
      {
        amount: 100000,
        method: 'WECHAT_TRANSFER',
        paidAt: '2026-11-24 09:30:00',
        remark: '先付一部分',
      },
      staff,
      now,
      ids(500),
    )
    expect(first.status).toBe('PARTIALLY_PAID')
    expect(first.paidAmount).toBe(100000)
    const received = first.payments.find((p) => p.id === 21)!
    expect(received).toMatchObject({
      status: 'SUCCESS',
      amount: 100000,
      offlineMethod: 'WECHAT_TRANSFER',
      paidAt: '2026-11-24 09:30:00',
      operatorName: '李敏',
    })
    const rest = first.payments.find((p) => p.status === 'WAIT')!
    expect(rest).toMatchObject({
      amount: 237000,
      type: 'FULL',
      title: '全款（余款）',
      splitFromId: 21,
      sortNo: 2,
    })
    expect(first.payments.filter((p) => p.status !== 'CLOSED').reduce((sum, p) => sum + p.amount, 0)).toBe(
      first.payableAmount,
    )

    const second = offlineConfirmOrder(first, rest.id, { amount: 37000, method: 'CASH' }, staff, now, ids(600))
    expect(second.status).toBe('PARTIALLY_PAID')
    expect(second.payments.find((p) => p.status === 'WAIT')).toMatchObject({
      amount: 200000,
      title: '全款（余款）',
    })

    const last = second.payments.find((p) => p.status === 'WAIT')!
    const paid = offlineConfirmOrder(second, last.id, { method: 'BANK_TRANSFER' }, staff, now, ids(700))
    expect(paid.status).toBe('PAID')
    expect(paid.paidAmount).toBe(337000)
    expect(paid.payments.some((p) => p.status === 'WAIT')).toBe(false)
    expect(paid.payments.filter((p) => p.status === 'SUCCESS')).toHaveLength(3)
  })

  it('rejects bad amount, missing method and a future paidAt without changing anything', () => {
    const base = order()
    const call = (req: Parameters<typeof offlineConfirmOrder>[2]) => () =>
      offlineConfirmOrder(base, 21, req, staff, now, ids(500))
    expect(call({ amount: 0, method: 'CASH' })).toThrow(/大于 0/)
    expect(call({ amount: 400000, method: 'CASH' })).toThrow(/不能超过剩余/)
    expect(call({ amount: 1000 } as Parameters<typeof offlineConfirmOrder>[2])).toThrow(/收款方式/)
    expect(call({ amount: 1000, method: 'CASH', paidAt: '2026-11-25 08:00:00' })).toThrow(/晚于当前/)
    expect(call({ amount: 1000, method: 'CASH', paidAt: '2026/11/24' })).toThrow(/格式/)
    expect(base.payments).toHaveLength(1)
    expect(base.payments[0]?.status).toBe('WAIT')
  })

  it('keeps PARTIALLY_PAID after shipping and moves to FULFILLING once the split rest is paid', () => {
    let current = addShipment(order(), { orderItemId: 12, carrier: '顺丰', trackingNo: 'SF1' }, now, ids())
    current = offlineConfirmOrder(current, 21, { amount: 300000, method: 'ALIPAY' }, staff, now, ids(500))
    expect(current.status).toBe('PARTIALLY_PAID')
    const rest = current.payments.find((p) => p.status === 'WAIT')!
    current = offlineConfirmOrder(current, rest.id, { method: 'OTHER', remark: '刷卡' }, staff, now, ids(600))
    expect(current.status).toBe('FULFILLING')
  })
})
