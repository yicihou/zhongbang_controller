/**
 * Mock 的进销存接口。规则和后端同一套：非老板看不到进价、应付和毛利。
 */
import type { AdminOrder, AdminProduct, PageResult, Payment } from '@/api/types'
import type { MarginPeriod, MarginTotals, MarginTrendPoint, MarginTrendValues } from '@/api/margin'
import { addDays, formatDateTime, todayShanghai } from '@/domain/clock'
import { ApiError } from '@/domain/errors'
import { paginate } from '@/domain/page'
import {
  IMPORT_FIELDS,
  marginText,
  movingAverage,
  rowError,
  saleFlags,
  splitTax,
  suggestMapping,
  suggestQty,
  turnoverDays,
  yuanTextToFen,
  type InvoiceScope,
} from '@/domain/trade'
import { db } from './db'
import type { PurchaseLine, PurchaseRow, SupplierRow } from './tradeSeed'

const previews = new Map<string, { kind: string; headers: string[]; rows: Record<string, string>[] }>()

function requireBoss(boss: boolean, message: string): void {
  if (!boss) throw new ApiError(2003, message)
}

function supplierView(row: SupplierRow, boss: boolean): SupplierRow {
  if (boss) return row
  const { payable, paid, balance, ...rest } = row
  void payable
  void paid
  void balance
  return rest
}

function purchaseView(row: PurchaseRow, boss: boolean): PurchaseRow {
  const lines = row.lines.map((line) => {
    if (boss) return line
    const { unitCost, lineAmount, ...rest } = line
    void unitCost
    void lineAmount
    return rest
  })
  if (boss) return { ...row, lines }
  const { freightAmount, goodsAmount, totalAmount, ...rest } = row
  void freightAmount
  void goodsAmount
  void totalAmount
  return { ...rest, lines }
}

function presentOrder(order: AdminOrder, boss: boolean): AdminOrder {
  if (boss) return order
  const { goodsCost, grossProfit, ...rest } = order
  void goodsCost
  void grossProfit
  return {
    ...rest,
    items: order.items.map((item) => {
      const { costPrice, costAmount, costKnown, ...line } = item
      void costPrice
      void costAmount
      void costKnown
      return line
    }),
  }
}

export function presentOrders(orders: AdminOrder[], boss: boolean): AdminOrder[] {
  return orders.map((order) => presentOrder(order, boss))
}

export function presentProduct(product: AdminProduct, boss: boolean): AdminProduct {
  return {
    ...product,
    skus: product.skus.map((sku) => {
      if (boss) return sku
      const { costPrice, ...rest } = sku
      void costPrice
      return rest
    }),
  }
}

function skuName(skuId: number): { productName: string; skuName: string; stock: number; costPrice?: number; minStock?: number } {
  for (const product of db.products) {
    const sku = product.skus.find((item) => item.id === skuId)
    if (sku) {
      return { productName: product.name, skuName: sku.title, stock: sku.stock, costPrice: sku.costPrice, minStock: sku.minStock }
    }
  }
  throw new ApiError(4040, '商品规格不存在')
}

function inTransit(skuId: number): number {
  return db.purchases
    .filter((item) => item.status === 'DRAFT')
    .flatMap((item) => item.lines)
    .filter((line) => line.skuId === skuId)
    .reduce((sum, line) => sum + line.quantity, 0)
}

function alerts() {
  const rows = []
  for (const product of db.products) {
    for (const sku of product.skus) {
      const min = sku.minStock ?? db.finance.defaultMinStock
      const transit = inTransit(sku.id)
      const suggest = suggestQty(min, db.finance.suggestMultiplier, sku.stock, transit)
      const low = sku.stock < min
      if (!low && suggest === 0) continue
      rows.push({
        skuId: sku.id,
        productId: product.id,
        productName: product.name,
        model: product.model,
        skuName: sku.title,
        stock: sku.stock,
        minStock: min,
        usingDefaultMin: sku.minStock == null,
        inTransit: transit,
        suggestQty: suggest,
        alerting: low,
      })
    }
  }
  return {
    defaultMinStock: db.finance.defaultMinStock,
    suggestMultiplier: db.finance.suggestMultiplier,
    list: rows,
  }
}

interface ReportRange {
  key: string
  from: string
  through: string
}

function report(period: MarginPeriod, key: string, scope: InvoiceScope, comparePeriod?: string, compareKey?: string) {
  let selected = reportRange(period, key)
  const kind = compareKey ? periodOf(comparePeriod ?? period) : period
  const previousKey = compareKey || (period === 'YEAR' ? String(Number(selected.key) - 1) : shiftMonth(selected.key, -1))
  const previous = reportRange(kind, previousKey)
  const sameType = period === kind
  const aligned = sameType ? alignRange(period, selected, previous) : previous
  if (sameType && aligned.through === todayShanghai()) selected = alignRange(period, aligned, selected)
  const currentTotals = totals(selected, scope)
  const prevTotals = totals(aligned, scope)
  const differences: Record<string, number> = {}
  if (sameType && currentTotals.available && prevTotals.available) {
    for (const [field, value] of Object.entries(currentTotals)) {
      if (field === 'unknownLineCount') continue
      const previousValue = prevTotals[field as keyof MarginTotals]
      if (typeof value === 'number' && typeof previousValue === 'number') differences[field] = value - previousValue
    }
    if (currentTotals.grossMargin != null && prevTotals.grossMargin != null) {
      differences.grossMargin = Number((Number(currentTotals.grossMargin) - Number(prevTotals.grossMargin)).toFixed(2))
    }
  }
  return {
    periodType: period,
    periodKey: selected.key,
    comparePeriodType: kind,
    scope,
    current: currentTotals,
    previous: prevTotals,
    differences,
    yearAgo: period === 'MONTH' ? totals(alignRange(period, selected, reportRange(period, shiftMonth(selected.key, -12))), scope) : undefined,
  }
}

function periodOf(value?: string): MarginPeriod {
  return value === 'YEAR' ? 'YEAR' : 'MONTH'
}

function shiftMonth(key: string, delta: number): string {
  const [year, month] = key.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1 + delta, 1)).toISOString().slice(0, 7)
}

function reportRange(period: MarginPeriod, key: string): ReportRange {
  const today = todayShanghai()
  const selected = key || today.slice(0, period === 'YEAR' ? 4 : 7)
  const from = period === 'YEAR' ? selected + '-01-01' : selected + '-01'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || from > today) throw new ApiError(1001, '请选择有效的报表期间')
  const end = period === 'YEAR' ? String(Number(selected) + 1) + '-01-01' : shiftMonth(selected, 1) + '-01'
  const last = addDays(end, -1)
  return { key: selected, from, through: last < today ? last : today }
}

function alignRange(period: MarginPeriod, current: ReportRange, previous: ReportRange): ReportRange {
  const last = period === 'YEAR' ? current.key + '-12-31' : addDays(shiftMonth(current.key, 1) + '-01', -1)
  if (current.through === last && current.through !== todayShanghai()) return previous
  const month = period === 'YEAR' ? previous.key + current.through.slice(4, 7) : previous.key
  const monthEnd = addDays(shiftMonth(month, 1) + '-01', -1)
  const sameDay = month + current.through.slice(7)
  const through = sameDay > monthEnd ? monthEnd : sameDay
  return { ...previous, through: through < previous.through ? through : previous.through }
}

function inRange(createdAt: string, updatedAt: string, status: string, range: ReportRange) {
  const date = createdAt.slice(0, 10)
  return date >= range.from && date <= range.through
    && (status !== 'CANCELLED' || updatedAt.slice(0, 10) > range.through)
}

function paidThrough(payments: Payment[], through: string) {
  const now = formatDateTime()
  return payments.reduce((total, row) => row.status === 'SUCCESS' && row.paidAt && row.paidAt <= now
    && row.paidAt.slice(0, 10) <= through ? total + row.amount : total, 0)
}

function totals(range: ReportRange, scope: InvoiceScope) {
  const activeOrders = db.orders.filter((row) => inRange(row.createdAt, row.updatedAt, row.status, range))
  const activeRepairs = db.repairs.filter((row) => inRange(row.createdAt, row.updatedAt, row.status, range))
  const orders = activeOrders.filter((order) => matchScope(scope, !!order.invoiced))
  let sales = 0
  let salesIn = 0
  let salesEx = 0
  let unknownSales = 0
  let unknownLines = 0
  let knownNet = 0
  let cost = 0
  let known = false
  let received = 0
  let receivedIn = 0
  let receivedEx = 0
  let unpaid = 0
  let install = 0
  let installIn = 0
  let installEx = 0
  for (const order of orders) {
    const paid = paidThrough(order.payments, range.through)
    const paidParts = splitTax(paid, !!order.invoiced, order.taxIncluded, order.taxRate)
    received += paid
    receivedIn += paidParts.included
    receivedEx += paidParts.excluded
    unpaid += Math.max(0, order.payableAmount - paid)
    const installParts = splitTax(order.installAmount, !!order.invoiced, order.taxIncluded, order.taxRate)
    install += order.installAmount
    installIn += installParts.included
    installEx += installParts.excluded
    for (const line of order.items) {
      const parts = splitTax(line.lineAmount, !!order.invoiced, order.taxIncluded, order.taxRate)
      sales += line.lineAmount
      salesIn += parts.included
      salesEx += parts.excluded
      if (line.costKnown && line.costAmount != null) {
        known = true
        knownNet += parts.excluded
        cost += line.costAmount
      } else {
        unknownSales += line.lineAmount
        unknownLines += 1
      }
    }
  }
  let repair = 0
  let repairIn = 0
  let repairEx = 0
  for (const ticket of activeRepairs) {
    if (!matchScope(scope, !!ticket.invoiced)) continue
    const paid = paidThrough(ticket.payments, range.through)
    const parts = splitTax(paid, !!ticket.invoiced, ticket.taxIncluded, ticket.taxRate)
    repair += paid
    repairIn += parts.included
    repairEx += parts.excluded
  }
  const profit = known ? knownNet - cost : undefined
  return {
    periodKey: range.key,
    from: range.from,
    through: range.through,
    available: range.through === todayShanghai() || activeOrders.length > 0 || activeRepairs.length > 0,
    salesAmount: sales,
    salesTaxIncluded: salesIn,
    salesTaxExcluded: salesEx,
    receivedAmount: received,
    receivedTaxIncluded: receivedIn,
    receivedTaxExcluded: receivedEx,
    unpaidAmount: unpaid,
    goodsCost: known ? cost : undefined,
    grossProfit: profit,
    grossMargin: profit == null ? undefined : marginText(profit, knownNet),
    unknownSalesAmount: unknownSales,
    unknownLineCount: unknownLines,
    installIncome: install,
    installTaxIncluded: installIn,
    installTaxExcluded: installEx,
    repairIncome: repair,
    repairTaxIncluded: repairIn,
    repairTaxExcluded: repairEx,
  }
}

function trendValues(row: MarginTotals): MarginTrendValues {
  return { salesAmount: row.salesAmount, receivedAmount: row.receivedAmount, unpaidAmount: row.unpaidAmount, grossProfit: row.grossProfit ?? null }
}

function average(rows: MarginTrendValues[]): MarginTrendValues | null {
  if (!rows.length) return null
  const mean = (key: keyof MarginTrendValues) => {
    const values = rows.flatMap((row) => row[key] == null ? [] : [row[key]])
    return values.length ? Math.round(values.reduce((sum, value) => sum + value, 0) / values.length) : null
  }
  return { salesAmount: mean('salesAmount'), receivedAmount: mean('receivedAmount'), unpaidAmount: mean('unpaidAmount'), grossProfit: mean('grossProfit') }
}

function history(query: Record<string, string>) {
  const today = todayShanghai()
  const from = query.from || today.slice(0, 4) + '-01-01'
  const through = query.through || today
  const scope = scopeOf(query.scope)
  if (from > through || through > today) throw new ApiError(1001, '请选择有效的日期范围')
  const selected = reportRange('MONTH', query.month ?? '')
  const baseline: ReportRange[] = []
  const months: MarginTrendPoint[] = []
  const observations: MarginTrendValues[] = []
  for (let key = from.slice(0, 7); key <= through.slice(0, 7); key = shiftMonth(key, 1)) {
    const whole = reportRange('MONTH', key)
    const start = whole.from < from ? from : whole.from
    const end = whole.through > through ? through : whole.through
    const row = totals({ key, from: start, through: end }, scope)
    const actual = row.available ? trendValues(row) : null
    months.push({ date: key, actual })
    if (actual && start === whole.from && end === whole.through && end < today) {
      observations.push(actual)
      if (key !== selected.key) baseline.push(whole)
    }
  }

  const days: MarginTrendPoint[] = []
  const available = totals(selected, scope).available
  for (let date = selected.from; date <= selected.through; date = addDays(date, 1)) {
    const samples = baseline.flatMap((range) => {
      const aligned = range.key + date.slice(7)
      return aligned <= range.through ? [trendValues(totals({ ...range, through: aligned }, scope))] : []
    })
    days.push({
      date,
      actual: available ? trendValues(totals({ ...selected, through: date }, scope)) : null,
      average: average(samples),
    })
  }
  return { months, days, monthlyAverage: average(observations) }
}

function matchScope(scope: InvoiceScope, invoiced: boolean): boolean {
  if (scope === 'INVOICED') return invoiced
  if (scope === 'UNINVOICED') return !invoiced
  return true
}

function scopeOf(raw: string | undefined): InvoiceScope {
  if (raw === 'INVOICED' || raw === 'UNINVOICED') return raw
  return 'ALL'
}

function parseCsv(text: string): { headers: string[]; rows: Record<string, string>[] } {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((line) => line.trim())
  if (lines.length === 0) throw new ApiError(1001, '文件是空的')
  const headers = lines[0]!.split(',').map((cell) => cell.trim())
  const rows = lines.slice(1).map((line) => {
    const cells = line.split(',')
    const row: Record<string, string> = {}
    headers.forEach((header, index) => {
      row[header] = (cells[index] ?? '').trim()
    })
    return row
  })
  return { headers, rows }
}

export function dispatchTrade(method: string, path: string, query: Record<string, string>, body: unknown, boss: boolean): unknown | undefined {
  const record = body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
  if (path === '/api/admin/costs/settings' && method === 'GET') {
    requireBoss(boss, '只有老板可以查看进价设置')
    return db.finance
  }
  if (path === '/api/admin/costs/settings' && method === 'PUT') {
    requireBoss(boss, '只有老板可以修改进价设置')
    if (record.costMethod === 'LATEST' || record.costMethod === 'MOVING_AVERAGE') db.finance.costMethod = record.costMethod
    if (typeof record.suggestMultiplier === 'number') db.finance.suggestMultiplier = record.suggestMultiplier
    if (typeof record.defaultMinStock === 'number') db.finance.defaultMinStock = record.defaultMinStock
    return db.finance
  }
  const costMatch = path.match(/^\/api\/admin\/costs\/skus\/(\d+)(?:\/logs)?$/)
  if (costMatch && (method === 'PUT' || method === 'GET')) {
    requireBoss(boss, '只有老板可以查看进价')
    const found = skuName(Number(costMatch[1]))
    if (method === 'PUT') {
      const next = Number(record.costPrice)
      if (!Number.isFinite(next) || next < 0) throw new ApiError(1001, '请填写进价')
      for (const product of db.products) {
        const sku = product.skus.find((item) => item.id === Number(costMatch[1]))
        if (sku) sku.costPrice = next
      }
      return { skuId: Number(costMatch[1]), skuName: found.skuName, costPrice: next, logs: [{ beforePrice: found.costPrice ?? null, afterPrice: next, operatorName: '老板', createdAt: formatDateTime() }] }
    }
    return { skuId: Number(costMatch[1]), skuName: found.skuName, costPrice: found.costPrice, logs: [] }
  }

  if (path === '/api/admin/suppliers' && method === 'GET') {
    const keyword = query.keyword?.trim()
    const rows = db.suppliers.filter((item) => !keyword || item.name.includes(keyword) || item.brands.includes(keyword))
    return paginate(rows.map((item) => supplierView(item, boss)), 1, 50)
  }
  if (path === '/api/admin/suppliers' && method === 'POST') {
    const row: SupplierRow = {
      id: ++db.seq,
      name: String(record.name ?? ''),
      contact: String(record.contact ?? ''),
      mobile: String(record.mobile ?? ''),
      brands: String(record.brands ?? ''),
      accountDays: Number(record.accountDays ?? 0),
      remark: String(record.remark ?? ''),
      payable: 0,
      paid: 0,
      balance: 0,
    }
    if (!row.name) throw new ApiError(1001, '请填写供应商名称')
    db.suppliers.unshift(row)
    return supplierView(row, boss)
  }
  const supplierMatch = path.match(/^\/api\/admin\/suppliers\/(\d+)(?:\/(ledger|payments))?$/)
  if (supplierMatch) {
    const supplier = db.suppliers.find((item) => item.id === Number(supplierMatch[1]))
    if (!supplier) throw new ApiError(4040, '供应商不存在')
    if (supplierMatch[2] === 'ledger' && method === 'GET') {
      requireBoss(boss, '只有老板可以查看供应商应付')
      const rows = db.ledgers.filter((item) => item.supplierId === supplier.id)
      return { supplierId: supplier.id, supplierName: supplier.name, payable: supplier.payable ?? 0, paid: supplier.paid ?? 0, balance: supplier.balance ?? 0, rows }
    }
    if (supplierMatch[2] === 'payments' && method === 'POST') {
      requireBoss(boss, '只有老板可以登记供应商付款')
      const amount = Number(record.amount)
      if (!amount || amount <= 0) throw new ApiError(1001, '请填写付款金额')
      db.ledgers.unshift({ id: ++db.seq, supplierId: supplier.id, entryType: 'PAID', amount, method: String(record.method ?? ''), operatorName: '老板', createdAt: formatDateTime() })
      supplier.paid = (supplier.paid ?? 0) + amount
      supplier.balance = (supplier.payable ?? 0) - (supplier.paid ?? 0)
      return { supplierId: supplier.id, supplierName: supplier.name, payable: supplier.payable ?? 0, paid: supplier.paid ?? 0, balance: supplier.balance ?? 0, rows: db.ledgers.filter((item) => item.supplierId === supplier.id) }
    }
    if (!supplierMatch[2] && method === 'DELETE') {
      db.suppliers = db.suppliers.filter((item) => item.id !== supplier.id)
      return null
    }
  }

  if (path === '/api/admin/purchases' && method === 'GET') {
    const rows = db.purchases.filter((item) => !query.status || item.status === query.status)
    return paginate(rows.map((item) => purchaseView(item, boss)), 1, 50)
  }
  if (path === '/api/admin/purchases' && method === 'POST') {
    const lines = Array.isArray(record.lines) ? (record.lines as Record<string, unknown>[]) : []
    if (lines.length === 0) throw new ApiError(1001, '请至少填写一行明细')
    const supplier = db.suppliers.find((item) => item.id === Number(record.supplierId))
    if (!supplier) throw new ApiError(4040, '供应商不存在')
    const purchase: PurchaseRow = {
      id: ++db.seq,
      purchaseNo: `PO${todayShanghai().replace(/-/g, '')}${String(db.seq).slice(-4)}`,
      supplierId: supplier.id,
      supplierName: supplier.name,
      orderDate: todayShanghai(),
      status: 'DRAFT',
      invoiced: record.invoiced === true,
      invoiceType: record.invoiceType ? String(record.invoiceType) : undefined,
      taxRate: typeof record.taxRate === 'number' ? record.taxRate : undefined,
      taxIncluded: typeof record.taxIncluded === 'boolean' ? record.taxIncluded : undefined,
      attachmentUrls: Array.isArray(record.attachmentUrls) ? record.attachmentUrls.map(String) : [],
      remark: record.remark ? String(record.remark) : undefined,
      freightAmount: boss ? Number(record.freightAmount ?? 0) : 0,
      lines: lines.map((line) => {
        const info = skuName(Number(line.skuId))
        const quantity = Number(line.quantity)
        const unitCost = boss && line.unitCost != null ? Number(line.unitCost) : undefined
        const item: PurchaseLine = { id: ++db.seq, skuId: Number(line.skuId), productName: info.productName, skuName: info.skuName, quantity }
        if (unitCost != null) {
          item.unitCost = unitCost
          item.lineAmount = unitCost * quantity
        }
        return item
      }),
      createdAt: formatDateTime(),
    }
    purchase.goodsAmount = purchase.lines.reduce((sum, line) => sum + (line.lineAmount ?? 0), 0)
    purchase.totalAmount = (purchase.goodsAmount ?? 0) + (purchase.freightAmount ?? 0)
    db.purchases.unshift(purchase)
    return purchaseView(purchase, boss)
  }
  const purchaseMatch = path.match(/^\/api\/admin\/purchases\/(\d+)(?:\/(receive|void))?$/)
  if (purchaseMatch) {
    const purchase = db.purchases.find((item) => item.id === Number(purchaseMatch[1]))
    if (!purchase) throw new ApiError(4040, '采购单不存在')
    if (!purchaseMatch[2] && method === 'GET') return purchaseView(purchase, boss)
    if (purchaseMatch[2] === 'receive' && method === 'POST') {
      if (purchase.status !== 'DRAFT') throw new ApiError(3003, '只有草稿可以入库')
      for (const line of purchase.lines) {
        for (const product of db.products) {
          const sku = product.skus.find((item) => item.id === line.skuId)
          if (!sku) continue
          const oldStock = sku.stock
          sku.stock += line.quantity
          if (boss && line.unitCost != null) {
            sku.costPrice = db.finance.costMethod === 'LATEST' ? line.unitCost : movingAverage(sku.costPrice, oldStock, line.unitCost, line.quantity)
          }
        }
      }
      purchase.status = 'RECEIVED'
      if (boss && (purchase.totalAmount ?? 0) > 0) {
        const supplier = db.suppliers.find((item) => item.id === purchase.supplierId)
        if (supplier) {
          supplier.payable = (supplier.payable ?? 0) + (purchase.totalAmount ?? 0)
          supplier.balance = (supplier.payable ?? 0) - (supplier.paid ?? 0)
        }
        db.ledgers.unshift({ id: ++db.seq, supplierId: purchase.supplierId, purchaseId: purchase.id, entryType: 'PAYABLE', amount: purchase.totalAmount ?? 0, operatorName: '老板', createdAt: formatDateTime() })
      }
      return purchaseView(purchase, boss)
    }
    if (purchaseMatch[2] === 'void' && method === 'POST') {
      if (purchase.status === 'RECEIVED') {
        for (const line of purchase.lines) {
          for (const product of db.products) {
            const sku = product.skus.find((item) => item.id === line.skuId)
            if (sku) sku.stock = Math.max(0, sku.stock - line.quantity)
          }
        }
      }
      purchase.status = 'VOID'
      return purchaseView(purchase, boss)
    }
  }

  if (path === '/api/admin/stock-alerts' && method === 'GET') return alerts()
  const minMatch = path.match(/^\/api\/admin\/stock-alerts\/skus\/(\d+)$/)
  if (minMatch && method === 'PUT') {
    const skuId = Number(minMatch[1])
    for (const product of db.products) {
      const sku = product.skus.find((item) => item.id === skuId)
      if (sku) sku.minStock = record.minStock == null || record.minStock === '' ? undefined : Number(record.minStock)
    }
    return alerts()
  }

  if (path === '/api/admin/reports/margin' && method === 'GET') {
    requireBoss(boss, '只有老板可以查看销售毛利')
    return report(periodOf(query.period), query.key ?? '', scopeOf(query.scope), query.comparePeriod, query.compareKey)
  }
  if (path === '/api/admin/reports/margin/history' && method === 'GET') {
    requireBoss(boss, '只有老板可以查看经营分析')
    return history(query)
  }
  if (path === '/api/admin/reports/margin/products' && method === 'GET') {
    requireBoss(boss, '只有老板可以查看销售毛利')
    const period = periodOf(query.period)
    const scope = scopeOf(query.scope)
    const selected = report(period, query.key ?? '', scope, query.comparePeriod, query.compareKey).current
    const range = { key: selected.periodKey, from: selected.from, through: selected.through }
    const days = Math.round((Date.parse(range.through) - Date.parse(range.from)) / 86400000) + 1
    const orders = db.orders.filter((row) => inRange(row.createdAt, row.updatedAt, row.status, range) && matchScope(scope, !!row.invoiced))
    const list = db.products.flatMap((product) => product.skus.map((sku) => {
      const sold = orders.flatMap((order) => order.items.filter((item) => item.skuId === sku.id).map((item) => ({ ...item, net: splitTax(item.lineAmount, !!order.invoiced, order.taxIncluded, order.taxRate).excluded })))
      const quantity = sold.reduce((sum, item) => sum + item.quantity, 0)
      const salesAmount = sold.reduce((sum, item) => sum + item.lineAmount, 0)
      const turnover = turnoverDays(sku.stock, quantity, days)
      const flags = saleFlags(sku.stock, quantity, turnover, db.finance.hotTurnoverDays, db.finance.slowTurnoverDays)
      const known = sold.filter((item) => item.costKnown && item.costAmount != null)
      const costAmount = known.length ? known.reduce((sum, item) => sum + (item.costAmount ?? 0), 0) : undefined
      const knownNet = known.reduce((sum, item) => sum + item.net, 0)
      const grossProfit = costAmount == null ? undefined : knownNet - costAmount
      return {
        skuId: sku.id,
        productId: product.id,
        productName: product.name,
        model: product.model,
        skuName: sku.title,
        quantity,
        salesAmount,
        salesTaxExcluded: sold.reduce((sum, item) => sum + item.net, 0),
        costAmount,
        grossProfit,
        grossMargin: grossProfit == null ? undefined : marginText(grossProfit, knownNet),
        unknownLineCount: sold.length - known.length,
        stock: sku.stock,
        turnoverDays: turnover,
        hot: flags.hot,
        slow: flags.slow,
        suggestQty: suggestQty(sku.minStock ?? db.finance.defaultMinStock, db.finance.suggestMultiplier, sku.stock, inTransit(sku.id)),
      }
    }))
    return { periodType: period, periodKey: range.key, scope, periodDays: days, list }
  }
  if (path === '/api/admin/reports/margin/export' && method === 'GET') {
    requireBoss(boss, '只有老板可以导出毛利')
    const data = report(periodOf(query.period), query.key ?? '', scopeOf(query.scope), query.comparePeriod, query.compareKey)
    return { text: `销售额,${data.current.salesAmount}\n毛利,${data.current.grossProfit ?? '未知成本'}\n`, filename: '毛利报表.csv' }
  }
  if (path === '/api/admin/reports/margin/insights' && method === 'POST') {
    requireBoss(boss, '只有老板可以生成毛利解读')
    return { configured: false, saved: false, message: '还没有配置 DeepSeek。请在启动环境里设置 DEEPSEEK_API_KEY 后再试。', analysis: null }
  }
  if (path === '/api/admin/reports/margin/insights' && method === 'GET') {
    requireBoss(boss, '只有老板可以查看毛利解读')
    return db.insights
  }

  if (path === '/api/admin/imports/template' && method === 'GET') {
    requireBoss(boss, '只有老板可以下载导入模板')
    const kind = query.kind || 'PRODUCT'
    const fields = IMPORT_FIELDS[kind] ?? IMPORT_FIELDS.PRODUCT!
    return { text: fields.map((field) => field.label).join(',') + '\n', filename: `导入模板-${kind}.csv` }
  }
  if (path === '/api/admin/imports/preview' && method === 'POST') {
    requireBoss(boss, '只有老板可以导入')
    const kind = String(record.kind ?? query.kind ?? 'PRODUCT')
    const parsed = parseCsv(String(record.csv ?? record.text ?? ''))
    const fields = IMPORT_FIELDS[kind] ?? IMPORT_FIELDS.PRODUCT!
    const token = `imp${++db.seq}`
    previews.set(token, { kind, headers: parsed.headers, rows: parsed.rows })
    const mapping = suggestMapping(parsed.headers, fields)
    return {
      token,
      kind,
      fields,
      headers: parsed.headers,
      suggestedMapping: mapping,
      rows: parsed.rows.map((cells, index) => ({ rowNo: index + 2, cells, error: rowError(cells, mapping, fields) })),
    }
  }
  if (path === '/api/admin/imports/commit' && method === 'POST') {
    requireBoss(boss, '只有老板可以导入')
    const stored = previews.get(String(record.token ?? ''))
    if (!stored) throw new ApiError(1001, '预览已失效，请重新上传')
    const fields = IMPORT_FIELDS[stored.kind] ?? IMPORT_FIELDS.PRODUCT!
    const mapping = (record.mapping as Record<string, string>) ?? suggestMapping(stored.headers, fields)
    let created = 0
    let skipped = 0
    const errors: string[] = []
    stored.rows.forEach((cells, index) => {
      const error = rowError(cells, mapping, fields)
      if (error) {
        skipped += 1
        errors.push(`第 ${index + 2} 行：${error}`)
        return
      }
      if (stored.kind === 'PRODUCT') {
        const name = cells[mapping.productName ?? ''] ?? ''
        const skuTitle = cells[mapping.skuName ?? ''] ?? ''
        const price = yuanTextToFen(cells[mapping.salePrice ?? ''] ?? '') ?? 0
        const cost = yuanTextToFen(cells[mapping.costPrice ?? ''] ?? '')
        const stock = Number(cells[mapping.stock ?? ''] || 0)
        db.products.unshift({
          id: ++db.seq,
          name,
          model: cells[mapping.model ?? ''] || undefined,
          brand: cells[mapping.brand ?? ''] || undefined,
          status: 'ON',
          price,
          stock,
          createdAt: formatDateTime(),
          updatedAt: formatDateTime(),
          skus: [{ id: ++db.seq, title: skuTitle, price, stock, costPrice: cost ?? undefined }],
        })
      }
      created += 1
    })
    return { created, skipped, errors }
  }

  if (path === '/api/admin/trade/demo' && method === 'POST') {
    requireBoss(boss, '只有老板可以生成演示数据')
    const exists = db.suppliers.some((item) => item.name === '演示-华东缝配')
    return { created: !exists, suppliers: db.suppliers.length, purchases: db.purchases.length, orders: 2, message: exists ? '演示数据已经有了' : '已生成演示数据' }
  }
  if (path === '/api/admin/trade/demo' && method === 'DELETE') {
    requireBoss(boss, '只有老板可以清除演示数据')
    return { created: false, suppliers: 0, purchases: 0, orders: 0, message: '已清除演示进销存数据' }
  }

  const orderInvoice = path.match(/^\/api\/admin\/orders\/(\d+)\/invoice$/)
  if (orderInvoice && method === 'PUT') {
    const order = db.orders.find((item) => item.id === Number(orderInvoice[1]))
    if (!order) throw new ApiError(4040, '订单不存在')
    order.invoiced = record.invoiced === true
    order.invoiceType = record.invoiceType ? String(record.invoiceType) : undefined
    order.taxRate = typeof record.taxRate === 'number' ? record.taxRate : undefined
    order.taxIncluded = typeof record.taxIncluded === 'boolean' ? record.taxIncluded : undefined
    return null
  }
  return undefined
}

export function tradePage<T>(rows: T[]): PageResult<T> {
  return paginate(rows, 1, 20)
}
