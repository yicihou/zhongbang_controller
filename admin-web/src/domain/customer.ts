/**
 * 客户模块的规则。Mock 和单测都走这里，页面只负责展示。
 * 金额是分。手机号在分析摘要里打码，老板自己的列表仍是完整号码。
 */
import type { AdminOrder, PageResult, RepairTicket } from '@/api/types'
import { ApiError } from '@/domain/errors'
import { paginate } from '@/domain/page'

export type CustomerType = 'PERSONAL' | 'FACTORY' | 'WORKSHOP' | 'SCHOOL' | 'DEALER'
export const CUSTOMER_TYPE_LABEL: Record<string, string> = {
  PERSONAL: '个人',
  FACTORY: '服装厂',
  WORKSHOP: '加工作坊',
  SCHOOL: '学校/培训班',
  DEALER: '同行经销商',
}
export const SOURCE_LABEL: Record<string, string> = {
  PHONE: '电话下单',
  STORE: '门店散客',
  MINIAPP: '小程序',
  REFERRAL: '转介绍',
  OTHER: '其他',
  DEMO: '演示数据',
}

export interface CustomerFile {
  id: number
  name: string
  mobile: string
  altMobile: string
  wechat: string
  customerType: string
  shopName: string
  machineCount: number | null
  mainProducts: string
  source: string
  referrer: string
  birthday: string
  remark: string
  tags: string
  region: string
  levelCode: string
  levelManual: boolean
  createdAt: string
}

export interface CustomerAddress {
  id: number
  customerId: number
  region: string
  detail: string
  contactName: string
  contactMobile: string
  defaultAddress: boolean
  source: string
}

export interface CustomerFollow {
  id: number
  customerId: number
  followDate: string
  content: string
  nextDate: string
  operatorName: string
  createdAt: string
}

export interface CustomerAssetFile {
  id: number
  customerId: number
  brand: string
  model: string
  sn: string
  source: 'INTERNAL' | 'EXTERNAL'
  buyDate: string
  warrantyEnd: string
}

export interface LevelRule {
  id: number
  code: string
  name: string
  minPaid: number
  rankNo: number
  enabled: boolean
}

export interface AgreementMaterial {
  kind: string
  url?: string
  name?: string
  contentType?: string
  text?: string
}

export interface AgreementFile {
  id: number
  customerId: number
  scope: string
  orderId?: number
  paymentId?: number
  dueDate: string
  agreedAmount: number
  status: string
  note: string
  materials: AgreementMaterial[]
  operatorName: string
  createdAt: string
  updatedAt: string
  logs: { id: number; action: string; operatorName: string; createdAt: string }[]
}

export interface PreferenceFile {
  customerId: number
  manualNote: string
  manualBrand: string
  manualCategory: string
  manualPriceBand: string
  manualPayMethod: string
  manualNeedInstall: boolean | null
}

export interface AnalysisFile {
  id: number
  customerId: number | null
  kind: string
  promptText: string
  resultText: string
  operatorName: string
  createdAt: string
}

export interface CustomerBundle {
  customers: CustomerFile[]
  addresses: CustomerAddress[]
  follows: CustomerFollow[]
  assets: CustomerAssetFile[]
  agreements: AgreementFile[]
  preferences: PreferenceFile[]
  levelRules: LevelRule[]
  analyses: AnalysisFile[]
  extraOrders: AdminOrder[]
  extraRepairs: RepairTicket[]
}

export function shanghaiDate(offsetDays = 0): string {
  const now = new Date(Date.now() + offsetDays * 86400000)
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(now)
}

export function maskMobile(mobile: string | null | undefined): string {
  const text = (mobile ?? '').trim()
  if (text.length < 7) return text
  return `${text.slice(0, 3)}****${text.slice(-4)}`
}

export function defaultLevelRules(): LevelRule[] {
  return [
    { id: 1, code: 'NORMAL', name: '普通', minPaid: 0, rankNo: 1, enabled: true },
    { id: 2, code: 'SILVER', name: '银卡', minPaid: 500000, rankNo: 2, enabled: true },
    { id: 3, code: 'GOLD', name: '金卡', minPaid: 2000000, rankNo: 3, enabled: true },
    { id: 4, code: 'VIP', name: 'VIP', minPaid: 5000000, rankNo: 4, enabled: true },
  ]
}

/** 约 30 个演示客户。前 6 个对上订单种子里的 customerId，后面的自带订单。 */
export function buildCustomerBundle(): CustomerBundle {
  const today = shanghaiDate(0)
  const rules = defaultLevelRules()
  const known: CustomerFile[] = [
    file(1001, '张建国', '13812348000', 'FACTORY', '杭州市西湖区', '建国制衣', 'GOLD', true, '月结,老客户'),
    file(1002, '李淑芬', '13900001111', 'WORKSHOP', '杭州市滨江区', '淑芬加工', 'SILVER', false, '加急'),
    file(1003, '王磊', '13711112222', 'DEALER', '宁波市鄞州区', '磊达机电', 'VIP', false, '同行'),
    file(1004, '赵敏', '13622223333', 'SCHOOL', '温州市鹿城区', '敏敏培训', 'NORMAL', false, ''),
    file(1005, '陈浩', '13533334444', 'FACTORY', '嘉兴市南湖区', '浩顺工装', 'GOLD', false, '招标'),
    file(1006, '周倩', '13666667777', 'PERSONAL', '绍兴市越城区', '', 'NORMAL', false, ''),
  ]
  const generated: CustomerFile[] = []
  const names = ['吴海波', '郑美玲', '孙志远', '钱芳', '冯磊', '褚雪', '卫东', '蒋婷', '韩冰', '杨帆', '秦朗', '许静', '何勇', '吕倩', '施杰', '孔霞', '曹敏', '严浩', '华莉', '金鑫', '魏娜', '陶军', '姜丽', '邹凯']
  const regions = ['杭州市余杭区', '宁波市海曙区', '温州市瓯海区', '嘉兴市桐乡市', '绍兴市柯桥区', '金华市义乌市', '苏州市吴中区', '南京市江宁区']
  const types: CustomerType[] = ['FACTORY', 'WORKSHOP', 'PERSONAL', 'SCHOOL', 'DEALER']
  for (let i = 0; i < names.length; i++) {
    generated.push(
      file(
        1201 + i,
        names[i],
        `13866${String(100001 + i).slice(0, 6)}`,
        types[i % types.length],
        regions[i % regions.length],
        i % 5 === 2 ? '' : `${names[i]}车间`,
        ['NORMAL', 'SILVER', 'GOLD', 'VIP'][i % 4],
        i % 7 === 0,
        i % 3 === 0 ? '复购' : '',
      ),
    )
  }
  const customers = [...known, ...generated]
  const addresses: CustomerAddress[] = customers.map((customer, index) => ({
    id: 3000 + index,
    customerId: customer.id,
    region: customer.region,
    detail: `${customer.region}示范路 ${index + 8} 号`,
    contactName: customer.name,
    contactMobile: customer.mobile,
    defaultAddress: true,
    source: 'MANUAL',
  }))
  const follows: CustomerFollow[] = customers.slice(0, 8).map((customer, index) => ({
    id: 4000 + index,
    customerId: customer.id,
    followDate: shanghaiDate(-index - 1),
    content: index % 2 === 0 ? '电话确认还要补一台平缝机。' : '本周先不采购。',
    nextDate: shanghaiDate(index + 3),
    operatorName: '老板',
    createdAt: `${shanghaiDate(-index)} 09:00:00`,
  }))
  const assets: CustomerAssetFile[] = []
  const extraOrders: AdminOrder[] = []
  const extraRepairs: RepairTicket[] = []
  generated.forEach((customer, index) => {
    const payable = [328000, 158000, 690000, 336000][index % 4]
    const paid = index % 5 === 0 ? 0 : index % 4 === 0 ? Math.floor(payable / 2) : payable
    const orderId = 930000 + index
    const createdAt = `${shanghaiDate(index > 18 ? -200 : -3 - index)} 11:00:00`
    extraOrders.push(orderOf(orderId, customer, payable, paid, createdAt, index))
    assets.push({
      id: 5000 + index,
      customerId: customer.id,
      brand: '标准牌',
      model: ['A5-5000', 'M3', 'E4-7000', 'EM-12'][index % 4],
      sn: `MOCK-SN-${index + 1}`,
      source: 'INTERNAL',
      buyDate: shanghaiDate(-30 - index),
      warrantyEnd: shanghaiDate(index % 6 === 0 ? -10 : 400),
    })
    if (index % 6 === 0) {
      assets.push({
        id: 6000 + index,
        customerId: customer.id,
        brand: '外牌',
        model: '外购拷边机',
        sn: `MOCK-EX-${index + 1}`,
        source: 'EXTERNAL',
        buyDate: shanghaiDate(-800),
        warrantyEnd: shanghaiDate(-100),
      })
      extraRepairs.push(repairOf(940000 + index, customer, createdAt))
    }
  })
  const agreements: AgreementFile[] = [
    agreement(7001, 1001, 'OVERDUE', shanghaiDate(-2), 128000),
    agreement(7002, 1002, 'TODAY', today, 50000),
    agreement(7003, 1003, 'SOON', shanghaiDate(2), 80000),
    agreement(7004, 1004, 'DONE', shanghaiDate(-15), 20000),
    agreement(7005, 1201, 'OVERDUE', shanghaiDate(-1), 15000),
  ]
  return {
    customers,
    addresses,
    follows,
    assets,
    agreements,
    preferences: [{ customerId: 1001, manualNote: '喜欢标准牌，安装要预约周末。', manualBrand: '', manualCategory: '', manualPriceBand: '', manualPayMethod: '', manualNeedInstall: true }],
    levelRules: rules,
    analyses: [],
    extraOrders,
    extraRepairs,
  }
}

export interface CustomerStats {
  orderAmount: number
  receivedAmount: number
  unpaidAmount: number
  orderCount: number
  avgAmount: number | null
  firstOrderAt: string
  lastOrderAt: string
  daysSinceLast: number | null
}

export function customerStats(customerId: number, orders: AdminOrder[], repairs: RepairTicket[], today = shanghaiDate()): CustomerStats {
  const mine = orders.filter((order) => order.customerId === customerId && order.status !== 'CANCELLED')
  const fixes = repairs.filter((ticket) => ticket.customerId === customerId && ticket.status !== 'CANCELLED')
  const orderAmount = mine.reduce((sum, order) => sum + order.payableAmount, 0)
  const received = mine.reduce((sum, order) => sum + order.paidAmount, 0) + fixes.reduce((sum, ticket) => sum + (ticket.paidAmount ?? 0), 0)
  const unpaid =
    mine.reduce((sum, order) => sum + Math.max(0, order.payableAmount - order.paidAmount), 0) +
    fixes.reduce((sum, ticket) => sum + Math.max(0, (ticket.payableAmount ?? 0) - (ticket.paidAmount ?? 0)), 0)
  const times = mine.map((order) => order.createdAt).sort()
  const last = times.at(-1) ?? ''
  const days = last ? daysBetween(last.slice(0, 10), today) : null
  return {
    orderAmount,
    receivedAmount: received,
    unpaidAmount: unpaid,
    orderCount: mine.length,
    avgAmount: mine.length ? Math.floor(orderAmount / mine.length) : null,
    firstOrderAt: times[0] ?? '',
    lastOrderAt: last,
    daysSinceLast: days,
  }
}

export function applyAutoLevel(customer: CustomerFile, received: number, rules: LevelRule[]): void {
  if (customer.levelManual) return
  const enabled = rules.filter((rule) => rule.enabled).sort((a, b) => b.rankNo - a.rankNo)
  const match = enabled.find((rule) => received >= rule.minPaid) ?? enabled[enabled.length - 1]
  if (match) customer.levelCode = match.code
}

export function levelName(code: string, rules: LevelRule[]): string {
  return rules.find((rule) => rule.code === code)?.name ?? code
}

export function agreementBucket(dueDate: string, status: string, today = shanghaiDate(), days = 3): string {
  if (status !== 'ACTIVE') return 'NONE'
  if (dueDate < today) return 'OVERDUE'
  if (dueDate === today) return 'TODAY'
  if (dueDate <= shanghaiDate(days) && dueDate > today) return 'SOON'
  return 'NONE'
}

/** 发给模型的摘要。不包含完整手机号，地址只留到区。 */
export function insightPrompt(customer: CustomerFile, stats: CustomerStats, addresses: CustomerAddress[]): string {
  const region = customer.region || addresses[0]?.region || ''
  return JSON.stringify({
    姓名: customer.name,
    客户类型: CUSTOMER_TYPE_LABEL[customer.customerType] ?? customer.customerType,
    地区: region,
    手机号: maskMobile(customer.mobile),
    备用电话: maskMobile(customer.altMobile),
    地址: region ? `${region}***` : '',
    消费: stats,
  })
}

export function customersToCsv(rows: { name: string; mobile: string; customerTypeLabel: string; region: string; tags: string; levelName: string }[]): string {
  const header = '姓名,手机,备用电话,微信号,客户类型,地区,厂名或店名,机器数量,主营产品,来源,介绍人,生日,备注,标签,等级'
  const lines = rows.map((row) => [row.name, row.mobile, '', '', row.customerTypeLabel, row.region, '', '', '', '', '', '', '', row.tags, row.levelName].join(','))
  return `\uFEFF${header}\n${lines.join('\n')}\n`
}

function file(
  id: number,
  name: string,
  mobile: string,
  customerType: string,
  region: string,
  shopName: string,
  levelCode: string,
  levelManual: boolean,
  tags: string,
): CustomerFile {
  return {
    id,
    name,
    mobile,
    altMobile: '',
    wechat: '',
    customerType,
    shopName,
    machineCount: customerType === 'PERSONAL' ? null : 8,
    mainProducts: customerType === 'PERSONAL' ? '' : '服装',
    source: 'DEMO',
    referrer: '',
    birthday: '',
    remark: '',
    tags,
    region,
    levelCode,
    levelManual,
    createdAt: `${shanghaiDate(-40)} 08:00:00`,
  }
}

function agreement(id: number, customerId: number, kind: string, dueDate: string, amount: number): AgreementFile {
  return {
    id,
    customerId,
    scope: 'CUSTOMER',
    dueDate,
    agreedAmount: amount,
    status: kind === 'DONE' ? 'FULFILLED' : 'ACTIVE',
    note: kind === 'DONE' ? '已经付清' : '演示协议',
    materials: [],
    operatorName: '老板',
    createdAt: `${shanghaiDate(-5)} 09:00:00`,
    updatedAt: `${shanghaiDate(-1)} 09:00:00`,
    logs: [{ id: id + 100, action: 'CREATE', operatorName: '老板', createdAt: `${shanghaiDate(-5)} 09:00:00` }],
  }
}

function orderOf(id: number, customer: CustomerFile, payable: number, paid: number, createdAt: string, index: number): AdminOrder {
  const names = ['电脑平缝机 A5', '家用多功能机 M3', '四线包缝机 E4', '电脑绣花机 12 头']
  const models = ['A5-5000', 'M3', 'E4-7000', 'EM-12']
  const productName = names[index % names.length]
  return {
    id,
    orderNo: `DM${id}`,
    customerId: customer.id,
    customerName: customer.name,
    customerMobile: customer.mobile,
    status: paid <= 0 ? 'PENDING_PAYMENT' : paid < payable ? 'PARTIALLY_PAID' : 'PAID',
    goodsAmount: payable,
    installAmount: 0,
    freightAmount: 0,
    adjustAmount: 0,
    payableAmount: payable,
    paidAmount: paid,
    priceAdjusted: false,
    addressText: `${customer.region}示范路 1 号`,
    createdAt,
    updatedAt: createdAt,
    items: [
      {
        id: id + 1,
        orderId: id,
        skuId: 1011,
        productName,
        skuName: models[index % models.length],
        quantity: 1,
        unitPrice: payable,
        lineAmount: payable,
        deliveryMethod: index % 2 === 0 ? 'ON_SITE_INSTALL' : 'EXPRESS',
        installFee: index % 2 === 0 ? 0 : null,
      },
    ],
    payments:
      paid > 0 && paid < payable
        ? [
            pay(id + 2, id, paid, 'SUCCESS', '定金', createdAt),
            pay(id + 3, id, payable - paid, 'WAIT', '（余款）', createdAt),
          ]
        : [pay(id + 2, id, payable, paid >= payable && payable > 0 ? 'SUCCESS' : 'WAIT', paid >= payable ? '全款' : '全款', createdAt)],
    priceLogs: [],
  }
}

function pay(id: number, orderId: number, amount: number, status: 'SUCCESS' | 'WAIT', title: string, createdAt: string) {
  return {
    id,
    bizType: 'ORDER' as const,
    bizId: orderId,
    paymentNo: `P${id}`,
    channel: 'OFFLINE' as const,
    type: title.includes('余款') ? ('BALANCE' as const) : ('FULL' as const),
    title,
    amount,
    status,
    sortNo: 1,
    offlineMethod: status === 'SUCCESS' ? ('WECHAT_TRANSFER' as const) : undefined,
    createdAt,
    updatedAt: createdAt,
  }
}

function repairOf(id: number, customer: CustomerFile, createdAt: string): RepairTicket {
  return {
    id,
    ticketNo: `RT${id}`,
    customerId: customer.id,
    customerName: customer.name,
    contactMobile: customer.mobile,
    status: 'PENDING_PAYMENT',
    payableAmount: 15000,
    paidAmount: 0,
    createdAt,
    updatedAt: createdAt,
    lines: [
      {
        id: id + 1,
        ticketId: id,
        lineNo: 1,
        brand: '外牌',
        model: '外购拷边机',
        source: 'EXTERNAL',
        faultType: 'NOISE',
        faultDesc: '异响',
        images: [],
      },
    ],
    payments: [],
  }
}

function daysBetween(from: string, to: string): number {
  const a = Date.parse(`${from}T00:00:00+08:00`)
  const b = Date.parse(`${to}T00:00:00+08:00`)
  return Math.round((b - a) / 86400000)
}

export interface ListQuery {
  name?: string
  mobile?: string
  region?: string
  tag?: string
  customerType?: string
  level?: string
  unpaidOnly?: string
  sort?: string
  direction?: string
  page?: number
  size?: number
}

export interface CustomerListItem {
  id: number
  name: string
  mobile: string
  region: string
  customerType: string
  customerTypeLabel: string
  levelCode: string
  levelName: string
  levelManual: boolean
  tags: string
  orderAmount: number
  unpaidAmount: number
  orderCount: number
  lastOrderAt: string
  overdue: boolean
}

export function listCustomerItems(bundle: CustomerBundle, orders: AdminOrder[], repairs: RepairTicket[], query: ListQuery): PageResult<CustomerListItem> {
  const rows = bundle.customers
    .map((customer) => {
      const stats = customerStats(customer.id, orders, repairs)
      applyAutoLevel(customer, stats.receivedAmount, bundle.levelRules)
      return { customer, stats }
    })
    .filter(({ customer, stats }) => {
      if (query.name && !customer.name.includes(query.name)) return false
      if (query.mobile && !customer.mobile.includes(query.mobile)) return false
      if (query.region && !customer.region.includes(query.region)) return false
      if (query.tag && !customer.tags.includes(query.tag)) return false
      if (query.customerType && customer.customerType !== query.customerType) return false
      if (query.level && customer.levelCode !== query.level && levelName(customer.levelCode, bundle.levelRules) !== query.level) return false
      if ((query.unpaidOnly === '1' || query.unpaidOnly === 'true') && stats.unpaidAmount <= 0) return false
      return true
    })
  const dir = query.direction === 'asc' ? 1 : -1
  const sort = query.sort || 'lastOrderAt'
  rows.sort((a, b) => {
    const av = sort === 'orderAmount' ? a.stats.orderAmount : sort === 'unpaidAmount' ? a.stats.unpaidAmount : sort === 'orderCount' ? a.stats.orderCount : a.stats.lastOrderAt
    const bv = sort === 'orderAmount' ? b.stats.orderAmount : sort === 'unpaidAmount' ? b.stats.unpaidAmount : sort === 'orderCount' ? b.stats.orderCount : b.stats.lastOrderAt
    if (av < bv) return -1 * dir
    if (av > bv) return 1 * dir
    return 0
  })
  const items = rows.map(({ customer, stats }) => ({
    id: customer.id,
    name: customer.name,
    mobile: customer.mobile,
    region: customer.region,
    customerType: customer.customerType,
    customerTypeLabel: CUSTOMER_TYPE_LABEL[customer.customerType] ?? customer.customerType,
    levelCode: customer.levelCode,
    levelName: levelName(customer.levelCode, bundle.levelRules),
    levelManual: customer.levelManual,
    tags: customer.tags,
    orderAmount: stats.orderAmount,
    unpaidAmount: stats.unpaidAmount,
    orderCount: stats.orderCount,
    lastOrderAt: stats.lastOrderAt,
    overdue: bundle.agreements.some((item) => item.customerId === customer.id && agreementBucket(item.dueDate, item.status) === 'OVERDUE'),
  }))
  return paginate(items, query.page, query.size)
}

export function isBossRole(role: string | undefined): boolean {
  return role === 'BOSS' || role === 'ROLE_BOSS'
}
