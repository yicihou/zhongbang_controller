/**
 * 造一笔「客户刚提交」的待付款订单，写进内存库，并返回一条 ORDER_CREATED 通知。
 * 不负责推送；订单列表按钮和顶栏的定时器各自决定要不要 publishMockStream。
 * 单号形如 SO + 上海日期 + 6 位序号。安装行的 installFee 为 null，表示管理员还没定价。
 */
import type { AdminNotice, AdminOrder } from '@/api/types'
import { formatDateTime, todayShanghai } from '@/domain/clock'
import { db } from './db'

const CUSTOMERS = [
  { id: 1101, name: '孙丽', mobile: '13800007721' },
  { id: 1102, name: '马强', mobile: '13800007722' },
  { id: 1103, name: '吴婷', mobile: '13800007723' },
  { id: 1104, name: '郑凯', mobile: '13800007724' },
]

/** 模拟客户刚提交的新订单，并写入 admin_notice。不负责推送。 */
export function simulateIncomingOrder(): AdminNotice {
  const customer = CUSTOMERS[db.simCursor % CUSTOMERS.length] ?? CUSTOMERS[0]
  db.simCursor += 1
  db.dailySeq += 1
  const now = formatDateTime()
  const day = todayShanghai().replace(/-/g, '')
  const orderNo = `SO${day}${String(db.dailySeq).padStart(6, '0')}`
  const orderId = ++db.seq
  const itemId = ++db.seq
  const paymentId = ++db.seq
  const install = db.simCursor % 2 === 0
  const order: AdminOrder = {
    id: orderId,
    orderNo,
    customerId: customer.id,
    customerName: customer.name,
    customerMobile: customer.mobile,
    status: 'PENDING_PAYMENT',
    goodsAmount: install ? 328000 : 498000,
    installAmount: 0,
    freightAmount: 0,
    adjustAmount: 0,
    payableAmount: install ? 328000 : 498000,
    paidAmount: 0,
    priceAdjusted: false,
    contactRemark: '小程序刚刚提交',
    addressText: '浙江省杭州市西湖区学院路 77 号',
    createdAt: now,
    updatedAt: now,
    items: [
      {
        id: itemId,
        orderId,
        skuId: install ? 501 : 503,
        productName: install ? '电脑平缝机 A5' : '包缝机 757',
        skuName: install ? '标准款' : '三线',
        quantity: 1,
        unitPrice: install ? 328000 : 498000,
        lineAmount: install ? 328000 : 498000,
        deliveryMethod: install ? 'ON_SITE_INSTALL' : 'EXPRESS',
        installFee: install ? null : 0,
        installDate: install ? todayShanghai() : undefined,
        installSlot: install ? '下午' : undefined,
      },
    ],
    payments: [
      {
        id: paymentId,
        bizType: 'ORDER',
        bizId: orderId,
        paymentNo: `${orderNo}-F-1`,
        outTradeNo: `${orderNo}-F-1`,
        channel: 'OFFLINE',
        type: 'FULL',
        title: '全款',
        amount: install ? 328000 : 498000,
        status: 'WAIT',
        sortNo: 1,
        createdAt: now,
        updatedAt: now,
      },
    ],
    priceLogs: [],
  }
  db.orders.unshift(order)
  const notice: AdminNotice = {
    id: ++db.seq,
    title: '新订单',
    body: `${order.orderNo} ${order.customerName} 提交了订单`,
    bizType: 'ORDER',
    bizId: order.id,
    eventType: 'ORDER_CREATED',
    readFlag: false,
    createdAt: now,
  }
  db.notices.unshift(notice)
  return notice
}
