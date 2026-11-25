/**
 * Mock 里的客户接口。形状对齐 Java 的 /api/admin/customers。
 * 不是老板会抛 2003。导出在 Mock 里返回文本，页面再触发下载。
 */
import type { PageResult } from '@/api/types'
import {
  agreementBucket,
  customersToCsv,
  CUSTOMER_TYPE_LABEL,
  insightPrompt,
  levelName,
  listCustomerItems,
  shanghaiDate,
  SOURCE_LABEL,
  type AgreementFile,
  type CustomerFile,
  type CustomerListItem,
  type ListQuery,
} from '@/domain/customer'
import { customerStats } from '@/domain/customer'
import { ApiError } from '@/domain/errors'
import { paginate } from '@/domain/page'
import { db } from './db'

function bundle() {
  return {
    customers: db.customers,
    addresses: db.addresses,
    follows: db.follows,
    assets: db.assets,
    agreements: db.agreements,
    preferences: db.preferences,
    levelRules: db.levelRules,
    analyses: db.analyses,
    extraOrders: [],
    extraRepairs: [],
  }
}

function requireCustomer(id: number): CustomerFile {
  const customer = db.customers.find((item) => item.id === id)
  if (!customer) throw new ApiError(4040, '客户不存在')
  return customer
}

function detail(id: number) {
  const customer = requireCustomer(id)
  const stats = customerStats(id, db.orders, db.repairs)
  const preference = db.preferences.find((item) => item.customerId === id)
  const agreements = db.agreements
    .filter((item) => item.customerId === id)
    .map((item) => ({ ...item, bucket: agreementBucket(item.dueDate, item.status) }))
  return {
    profile: {
      ...customer,
      customerTypeLabel: CUSTOMER_TYPE_LABEL[customer.customerType] ?? customer.customerType,
      sourceLabel: SOURCE_LABEL[customer.source] ?? customer.source,
      levelName: levelName(customer.levelCode, db.levelRules),
    },
    stats,
    addresses: db.addresses.filter((item) => item.customerId === id),
    orders: db.orders
      .filter((order) => order.customerId === id)
      .map((order) => ({
        id: order.id,
        orderNo: order.orderNo,
        status: order.status,
        payableAmount: order.payableAmount,
        paidAmount: order.paidAmount,
        unpaidAmount: Math.max(0, order.payableAmount - order.paidAmount),
        createdAt: order.createdAt,
      })),
    models: modelRows(id),
    assets: db.assets
      .filter((item) => item.customerId === id)
      .map((item) => ({
        ...item,
        warrantyExpired: item.warrantyEnd < shanghaiDate(),
        repairCount: db.repairs.filter((ticket) => ticket.customerId === id && ticket.lines.some((line) => line.model === item.model)).length,
      })),
    repairs: db.repairs
      .filter((ticket) => ticket.customerId === id)
      .map((ticket) => ({
        id: ticket.id,
        ticketNo: ticket.ticketNo,
        status: ticket.status,
        payableAmount: ticket.payableAmount ?? 0,
        paidAmount: ticket.paidAmount ?? 0,
        unpaidAmount: Math.max(0, (ticket.payableAmount ?? 0) - (ticket.paidAmount ?? 0)),
        createdAt: ticket.createdAt,
      })),
    preference: {
      autoBrand: '标准牌',
      autoCategory: '',
      autoPriceBand: '',
      autoPayMethod: '',
      autoNeedInstall: null,
      manualNote: preference?.manualNote ?? '',
      manualBrand: preference?.manualBrand ?? '',
      manualCategory: preference?.manualCategory ?? '',
      manualPriceBand: preference?.manualPriceBand ?? '',
      manualPayMethod: preference?.manualPayMethod ?? '',
      manualNeedInstall: preference?.manualNeedInstall ?? null,
    },
    follows: db.follows.filter((item) => item.customerId === id),
    agreements,
    overdue: agreements.some((item) => item.bucket === 'OVERDUE'),
  }
}

function modelRows(customerId: number) {
  const map = new Map<string, { model: string; brand: string; category: string; times: number; quantity: number; amount: number; lastAt: string }>()
  for (const order of db.orders) {
    if (order.customerId !== customerId || order.status === 'CANCELLED') continue
    for (const item of order.items) {
      const key = item.skuName || item.productName
      const row = map.get(key) ?? { model: key, brand: '标准牌', category: '', times: 0, quantity: 0, amount: 0, lastAt: '' }
      row.times += 1
      row.quantity += item.quantity
      row.amount += item.lineAmount
      if (!row.lastAt || order.createdAt > row.lastAt) row.lastAt = order.createdAt
      map.set(key, row)
    }
  }
  return [...map.values()]
}

function aggregateModels() {
  const map = new Map<string, { name: string; id: null; quantity: number; amount: number }>()
  for (const customer of db.customers) {
    for (const row of modelRows(customer.id)) {
      const cell = map.get(row.model) ?? { name: row.model, id: null, quantity: 0, amount: 0 }
      cell.quantity += row.quantity
      cell.amount += row.amount
      map.set(row.model, cell)
    }
  }
  return [...map.values()].sort((a, b) => b.amount - a.amount).slice(0, 10)
}

function overview() {
  const items = listCustomerItems(bundle(), db.orders, db.repairs, { page: 1, size: 100 }).list
  const count = (pick: (row: CustomerListItem) => string) => {
    const map = new Map<string, number>()
    for (const row of items) map.set(pick(row), (map.get(pick(row)) ?? 0) + 1)
    return [...map.entries()].map(([name, count]) => ({ name, count }))
  }
  return {
    levels: count((row) => row.levelName),
    regions: count((row) => row.region || '未填地区'),
    models: aggregateModels(),
    unpaidTop: items
      .filter((row) => row.unpaidAmount > 0)
      .sort((a, b) => b.unpaidAmount - a.unpaidAmount)
      .slice(0, 10)
      .map((row) => ({ name: row.name, id: row.id, quantity: 0, amount: row.unpaidAmount })),
    customerCount: db.customers.length,
    unpaidCustomerCount: items.filter((row) => row.unpaidAmount > 0).length,
    overdueCount: items.filter((row) => row.overdue).length,
    sleepingCount: items.filter((row) => !row.lastOrderAt || row.lastOrderAt.slice(0, 10) < shanghaiDate(-180)).length,
    unpaidAmount: items.reduce((sum, row) => sum + row.unpaidAmount, 0),
  }
}

function asRecord(body: unknown): Record<string, unknown> {
  return body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
}

function createCustomer(body: unknown, operator: string) {
  const record = asRecord(body)
  const name = String(record.name ?? '').trim()
  const mobile = String(record.mobile ?? '').trim()
  if (!name) throw new ApiError(1001, '请填写姓名')
  if (!/^1\d{10}$/.test(mobile)) throw new ApiError(1001, '手机号应为 11 位数字')
  if (db.customers.some((item) => item.mobile === mobile)) throw new ApiError(4090, '这个手机号已经有客户档案')
  const type = normalizeType(String(record.customerType ?? 'PERSONAL'))
  const customer: CustomerFile = {
    id: ++db.seq,
    name,
    mobile,
    altMobile: String(record.altMobile ?? ''),
    wechat: String(record.wechat ?? ''),
    customerType: type,
    shopName: String(record.shopName ?? ''),
    machineCount: record.machineCount == null || record.machineCount === '' ? null : Number(record.machineCount),
    mainProducts: String(record.mainProducts ?? ''),
    source: normalizeSource(String(record.source ?? 'PHONE')),
    referrer: String(record.referrer ?? ''),
    birthday: String(record.birthday ?? ''),
    remark: String(record.remark ?? ''),
    tags: String(record.tags ?? ''),
    region: String(record.region ?? ''),
    levelCode: 'NORMAL',
    levelManual: false,
    createdAt: `${shanghaiDate()} 12:00:00`,
  }
  db.customers.push(customer)
  void operator
  return detail(customer.id)
}

function normalizeType(raw: string): string {
  const text = raw.trim()
  const found = Object.entries(CUSTOMER_TYPE_LABEL).find(([code, label]) => code === text.toUpperCase() || label === text)
  if (!text) return 'PERSONAL'
  if (!found) throw new ApiError(1001, '客户类型不正确')
  return found[0]
}

function normalizeSource(raw: string): string {
  const text = raw.trim()
  const found = Object.entries(SOURCE_LABEL).find(([code, label]) => code === text.toUpperCase() || label === text)
  if (!text) return 'PHONE'
  if (!found || found[0] === 'DEMO') throw new ApiError(1001, '来源不正确')
  return found[0]
}

function saveAgreement(customerId: number, body: unknown, operator: string) {
  const record = asRecord(body)
  const amount = Number(record.agreedAmount)
  const dueDate = String(record.dueDate ?? '')
  if (!amount || amount <= 0) throw new ApiError(1001, '约定金额要大于 0')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dueDate)) throw new ApiError(1001, '约定还款日请写成 yyyy-MM-dd')
  const row: AgreementFile = {
    id: ++db.seq,
    customerId,
    scope: String(record.scope ?? 'CUSTOMER'),
    orderId: record.orderId == null ? undefined : Number(record.orderId),
    paymentId: record.paymentId == null ? undefined : Number(record.paymentId),
    dueDate,
    agreedAmount: amount,
    status: 'ACTIVE',
    note: String(record.note ?? ''),
    materials: Array.isArray(record.materials) ? (record.materials as AgreementFile['materials']) : [],
    operatorName: operator,
    createdAt: `${shanghaiDate()} 12:00:00`,
    updatedAt: `${shanghaiDate()} 12:00:00`,
    logs: [],
  }
  row.logs.push({ id: ++db.seq, action: 'CREATE', operatorName: operator, createdAt: row.createdAt })
  db.agreements.push(row)
  return detail(customerId)
}

function parseImport(text: string) {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((line) => line.trim())
  const header = lines[0]?.split(',') ?? []
  const nameAt = header.indexOf('姓名')
  const mobileAt = header.indexOf('手机')
  if (nameAt < 0 || mobileAt < 0) throw new ApiError(1001, '表头需要包含「姓名」和「手机」')
  let created = 0
  let skipped = 0
  const errors: string[] = []
  for (let i = 1; i < lines.length; i++) {
    const cells = lines[i].split(',')
    try {
      createCustomer(
        {
          name: cells[nameAt],
          mobile: cells[mobileAt],
          customerType: cells[header.indexOf('客户类型')] ?? '',
          region: cells[header.indexOf('地区')] ?? '',
        },
        '老板',
      )
      const level = header.indexOf('等级') >= 0 ? cells[header.indexOf('等级')] : ''
      if (level) {
        const rule = db.levelRules.find((item) => item.name === level || item.code === level)
        if (!rule) throw new ApiError(1001, `等级「${level}」不存在`)
        const customer = db.customers.find((item) => item.mobile === cells[mobileAt])
        if (customer) {
          customer.levelCode = rule.code
          customer.levelManual = true
        }
      }
      created++
    } catch (error) {
      skipped++
      if (errors.length < 20) errors.push(`第 ${i + 1} 行：${error instanceof Error ? error.message : '失败'}`)
    }
  }
  return { created, skipped, errors }
}

/** 路径属于客户模块时返回数据；不属于时返回 undefined，让别的 Mock 继续处理。 */
export function dispatchCustomer(method: string, path: string, query: Record<string, string>, body: unknown, operator: { name: string; boss: boolean }): unknown | undefined {
  if (!path.startsWith('/api/admin/customers')) return undefined
  if (!operator.boss) throw new ApiError(2003, '只有老板可以查看客户档案')
  const listQuery = query as ListQuery
  if (method === 'GET' && path === '/api/admin/customers') return listCustomerItems(bundle(), db.orders, db.repairs, listQuery)
  if (method === 'GET' && path === '/api/admin/customers/export') {
    const page = listCustomerItems(bundle(), db.orders, db.repairs, { ...listQuery, page: 1, size: 100 })
    return { filename: '客户.csv', text: customersToCsv(page.list) }
  }
  if (method === 'GET' && path === '/api/admin/customers/import-template') {
    return { filename: '客户导入模板.csv', text: customersToCsv([]) }
  }
  if (method === 'POST' && path === '/api/admin/customers/import') return parseImport(String(asRecord(body).csv ?? asRecord(body).text ?? ''))
  if (method === 'POST' && path === '/api/admin/customers') return createCustomer(body, operator.name)
  if (method === 'POST' && path === '/api/admin/customers/demo') return { created: false, customers: db.customers.length, message: '演示客户已经在 Mock 数据里' }
  if (method === 'DELETE' && path === '/api/admin/customers/demo') return { created: false, customers: db.customers.length, message: 'Mock 会在刷新后恢复种子数据' }
  if (method === 'GET' && path === '/api/admin/customers/overview') return overview()
  if (method === 'GET' && path === '/api/admin/customers/levels') return db.levelRules
  if (method === 'GET' && path === '/api/admin/customers/insights') return db.analyses.filter((item) => item.kind === 'OVERALL')
  if (method === 'POST' && path === '/api/admin/customers/insights') return saveInsight(null, 'OVERALL', operator.name)
  if (method === 'GET' && path === '/api/admin/customers/repayments') {
    const bucket = (query.bucket || 'OVERDUE').toUpperCase()
    const rows = db.agreements
      .filter((item) => agreementBucket(item.dueDate, item.status) === bucket)
      .map((item) => ({ ...item, customerName: db.customers.find((customer) => customer.id === item.customerId)?.name ?? '', bucket }))
    return paginate(rows, Number(query.page) || 1, Number(query.size) || 20)
  }
  if (method === 'POST' && path === '/api/admin/customers/repayments/scan') return scan(operator.name)

  const level = path.match(/^\/api\/admin\/customers\/levels\/(\d+)$/)
  if (level && method === 'PUT') return updateLevel(Number(level[1]), body)

  const agreementAction = path.match(/^\/api\/admin\/customers\/agreements\/(\d+)(?:\/(fulfill|void|history))?$/)
  if (agreementAction) return agreementRoute(method, Number(agreementAction[1]), agreementAction[2], body, operator.name)

  const one = path.match(/^\/api\/admin\/customers\/(\d+)(?:\/([a-z]+)(?:\/(\d+)(?:\/([a-z]+))?)?)?$/)
  if (!one) return undefined
  const id = Number(one[1])
  const action = one[2]
  const subId = one[3] ? Number(one[3]) : undefined
  const tail = one[4]
  if (!action && method === 'GET') return detail(id)
  if (!action && method === 'PUT') return updateCustomer(id, body)
  if (action === 'follows' && method === 'POST') return addFollow(id, body, operator.name)
  if (action === 'preference' && method === 'PUT') return savePreference(id, body)
  if (action === 'level' && method === 'PUT') return assignLevel(id, body)
  if (action === 'agreements' && method === 'POST') return saveAgreement(id, body, operator.name)
  if (action === 'insights' && method === 'POST') return saveInsight(id, 'CUSTOMER', operator.name)
  if (action === 'insights' && method === 'GET') return db.analyses.filter((item) => item.customerId === id)
  if (action === 'addresses' && method === 'POST' && subId == null) return addAddress(id, body)
  if (action === 'addresses' && subId != null && tail === 'default') return setDefaultAddress(id, subId)
  if (action === 'addresses' && subId != null && method === 'PUT') return updateAddress(id, subId, body)
  return undefined
}

function updateCustomer(id: number, body: unknown) {
  const customer = requireCustomer(id)
  const record = asRecord(body)
  if (record.name) customer.name = String(record.name)
  if (record.mobile) customer.mobile = String(record.mobile)
  if (record.customerType) customer.customerType = normalizeType(String(record.customerType))
  if (record.region != null) customer.region = String(record.region)
  if (record.shopName != null) customer.shopName = String(record.shopName)
  if (record.tags != null) customer.tags = String(record.tags)
  if (record.remark != null) customer.remark = String(record.remark)
  if (record.wechat != null) customer.wechat = String(record.wechat)
  if (record.mainProducts != null) customer.mainProducts = String(record.mainProducts)
  return detail(id)
}

function addFollow(id: number, body: unknown, operator: string) {
  requireCustomer(id)
  const record = asRecord(body)
  const content = String(record.content ?? '').trim()
  if (!content) throw new ApiError(1001, '请填写跟进内容')
  db.follows.unshift({
    id: ++db.seq,
    customerId: id,
    followDate: String(record.followDate ?? shanghaiDate()),
    content,
    nextDate: String(record.nextDate ?? ''),
    operatorName: operator,
    createdAt: `${shanghaiDate()} 12:00:00`,
  })
  return detail(id)
}

function savePreference(id: number, body: unknown) {
  requireCustomer(id)
  const record = asRecord(body)
  const row = db.preferences.find((item) => item.customerId === id) ?? {
    customerId: id,
    manualNote: '',
    manualBrand: '',
    manualCategory: '',
    manualPriceBand: '',
    manualPayMethod: '',
    manualNeedInstall: null,
  }
  if (!db.preferences.includes(row)) db.preferences.push(row)
  row.manualNote = String(record.manualNote ?? '')
  row.manualBrand = String(record.manualBrand ?? '')
  row.manualCategory = String(record.manualCategory ?? '')
  row.manualPriceBand = String(record.manualPriceBand ?? '')
  row.manualPayMethod = String(record.manualPayMethod ?? '')
  row.manualNeedInstall = record.manualNeedInstall == null ? null : Boolean(record.manualNeedInstall)
  return detail(id)
}

function assignLevel(id: number, body: unknown) {
  const customer = requireCustomer(id)
  const record = asRecord(body)
  const manual = record.manual !== false
  if (!manual) {
    customer.levelManual = false
    return detail(id)
  }
  const rule = db.levelRules.find((item) => item.code === String(record.levelCode ?? '').toUpperCase())
  if (!rule) throw new ApiError(4040, '等级不存在')
  customer.levelCode = rule.code
  customer.levelManual = true
  return detail(id)
}

function addAddress(id: number, body: unknown) {
  requireCustomer(id)
  const record = asRecord(body)
  const detailText = String(record.detail ?? '').trim()
  if (!detailText) throw new ApiError(1001, '请填写详细地址')
  if (record.defaultAddress === true) db.addresses.forEach((item) => { if (item.customerId === id) item.defaultAddress = false })
  db.addresses.push({
    id: ++db.seq,
    customerId: id,
    region: String(record.region ?? ''),
    detail: detailText,
    contactName: String(record.contactName ?? ''),
    contactMobile: String(record.contactMobile ?? ''),
    defaultAddress: record.defaultAddress !== false,
    source: 'MANUAL',
  })
  return detail(id)
}

function updateAddress(id: number, addressId: number, body: unknown) {
  const row = db.addresses.find((item) => item.id === addressId && item.customerId === id)
  if (!row) throw new ApiError(4040, '地址不存在')
  const record = asRecord(body)
  if (record.detail) row.detail = String(record.detail)
  if (record.region != null) row.region = String(record.region)
  return detail(id)
}

function setDefaultAddress(id: number, addressId: number) {
  const row = db.addresses.find((item) => item.id === addressId && item.customerId === id)
  if (!row) throw new ApiError(4040, '地址不存在')
  db.addresses.forEach((item) => { if (item.customerId === id) item.defaultAddress = item.id === addressId })
  return detail(id)
}

function updateLevel(id: number, body: unknown) {
  const rule = db.levelRules.find((item) => item.id === id)
  if (!rule) throw new ApiError(4040, '等级不存在')
  const record = asRecord(body)
  if (record.name) rule.name = String(record.name)
  if (record.minPaid != null) rule.minPaid = Number(record.minPaid)
  if (record.enabled != null) rule.enabled = Boolean(record.enabled)
  const enabled = db.levelRules.filter((item) => item.enabled).sort((a, b) => a.rankNo - b.rankNo)
  for (let i = 1; i < enabled.length; i++) {
    if (enabled[i].minPaid < enabled[i - 1].minPaid) throw new ApiError(1001, '更高的等级，累计实收门槛不能比低等级还低')
  }
  return rule
}

function agreementRoute(method: string, id: number, action: string | undefined, body: unknown, operator: string) {
  const row = db.agreements.find((item) => item.id === id)
  if (!row) throw new ApiError(4040, '协议不存在')
  if (!action && method === 'PUT') {
    if (row.status !== 'ACTIVE') throw new ApiError(3003, '这份协议已经履约或作废，不能再改')
    const record = asRecord(body)
    if (record.dueDate) row.dueDate = String(record.dueDate)
    if (record.agreedAmount) row.agreedAmount = Number(record.agreedAmount)
    if (record.note != null) row.note = String(record.note)
    row.logs.push({ id: ++db.seq, action: 'UPDATE', operatorName: operator, createdAt: `${shanghaiDate()} 12:00:00` })
    return { ...row, bucket: agreementBucket(row.dueDate, row.status) }
  }
  if (action === 'fulfill' || action === 'void') {
    row.status = action === 'fulfill' ? 'FULFILLED' : 'VOID'
    row.logs.push({ id: ++db.seq, action: action === 'fulfill' ? 'FULFILL' : 'VOID', operatorName: operator, createdAt: `${shanghaiDate()} 12:00:00` })
    return { ...row, bucket: 'NONE' }
  }
  if (action === 'history') return row.logs
  return undefined
}

function saveInsight(customerId: number | null, kind: string, operator: string) {
  const customer = customerId == null ? null : requireCustomer(customerId)
  const prompt = customer
    ? insightPrompt(customer, customerStats(customer.id, db.orders, db.repairs), db.addresses.filter((item) => item.customerId === customer.id))
    : JSON.stringify(overview())
  const result = customer
    ? '客户画像：按现有订单看，属于可跟进的老客。购买预测：结合最近下单时间安排回访。推荐机型或耗材：优先平缝机针与旋梭。风险提示：如有待付款或逾期协议，先谈还款再推新机。跟进建议：约下次上门或电话，确认车间产量。'
    : '客户结构：等级和地区已经拉开。热销与地区：优先看机型排行。欠款与逾期：先处理逾期协议。下一步：对沉睡客户做一轮回访。'
  const row = {
    id: ++db.seq,
    customerId,
    kind,
    promptText: prompt,
    resultText: result,
    operatorName: operator,
    createdAt: `${shanghaiDate()} 12:00:00`,
  }
  db.analyses.unshift(row)
  return { available: true, ok: true, message: 'ok', analysis: row }
}

function scan(operator: string): number {
  let created = 0
  for (const row of db.agreements) {
    const bucket = agreementBucket(row.dueDate, row.status)
    if (bucket === 'NONE' || bucket === 'DONE') continue
    const eventType = `REPAY_${bucket}`
    if (db.notices.some((notice) => notice.eventType === eventType && notice.bizId === row.customerId && notice.createdAt.startsWith(shanghaiDate()))) continue
    const customer = db.customers.find((item) => item.id === row.customerId)
    db.notices.unshift({
      id: ++db.seq,
      title: bucket === 'TODAY' ? '协议还款今天到期' : bucket === 'OVERDUE' ? '协议还款已逾期' : '协议还款即将到期',
      body: `${customer?.name ?? '客户'} 约定 ${row.dueDate} 还款`,
      bizType: 'CUSTOMER',
      bizId: row.customerId,
      eventType: eventType as 'REPAY_SOON' | 'REPAY_TODAY' | 'REPAY_OVERDUE',
      readFlag: false,
      createdAt: `${shanghaiDate()} 09:00:00`,
      audience: 'BOSS',
    })
    created++
    void operator
  }
  return created
}

export type CustomerPage = PageResult<CustomerListItem>
