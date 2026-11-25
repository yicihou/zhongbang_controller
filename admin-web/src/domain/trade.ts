/**
 * 进价、税额和库存建议。金额是分，税率是百分数乘 100（1300 = 13%）。
 * 页面和 Mock 共用这些算法，和后端 TradeMath 一致。未知成本不要传 0。
 */

export type InvoiceScope = 'ALL' | 'INVOICED' | 'UNINVOICED'
export type CostMethod = 'MOVING_AVERAGE' | 'LATEST'

export interface TaxParts {
  included: number
  excluded: number
}

/** 不开票或没填税率时，含税和不含税都等于原金额。 */
export function splitTax(amount: number, invoiced: boolean, taxIncluded: boolean | null | undefined, taxRate: number | null | undefined): TaxParts {
  const value = Math.trunc(amount)
  if (!invoiced || taxRate == null || taxRate <= 0) return { included: value, excluded: value }
  if (taxIncluded === false) {
    return { included: Math.trunc((value * (10000 + taxRate)) / 10000), excluded: value }
  }
  return { included: value, excluded: Math.trunc((value * 10000) / (10000 + taxRate)) }
}

/** 旧进价未知时不把旧库存当成 0 元，直接用本批进价。 */
export function movingAverage(oldCost: number | null | undefined, oldStock: number, unitCost: number, qty: number): number {
  if (qty <= 0) return unitCost
  if (oldCost == null || oldStock <= 0) return unitCost
  return Math.trunc((oldCost * oldStock + unitCost * qty) / (oldStock + qty))
}

export function suggestQty(minStock: number, multiplier: number, stock: number, inTransit: number): number {
  const factor = multiplier > 0 ? multiplier : 2
  return Math.max(0, minStock * factor - Math.max(0, stock) - Math.max(0, inTransit))
}

/** 毛利率，百分数两位小数。销售额为 0 时返回空，而不是 0。 */
export function marginText(profit: number, netSales: number): string | null {
  if (netSales <= 0) return null
  const negative = profit < 0
  const abs = Math.abs(profit)
  const basis = Math.trunc((abs * 10000 + Math.trunc(netSales / 2)) / netSales)
  const text = `${Math.trunc(basis / 100)}.${String(basis % 100).padStart(2, '0')}`
  return negative ? `-${text}` : text
}

export function turnoverDays(stock: number, soldQty: number, periodDays: number): number | null {
  if (soldQty <= 0) return null
  const days = periodDays > 0 ? periodDays : 1
  return Math.round(stock / (soldQty / days))
}

export function saleFlags(stock: number, soldQty: number, turnover: number | null, hotDays = 30, slowDays = 90): { hot: boolean; slow: boolean } {
  const hot = soldQty > 0 && turnover != null && turnover <= hotDays
  const slow = stock > 0 && (soldQty === 0 || (turnover != null && turnover > slowDays))
  return { hot, slow }
}

export interface ImportField {
  key: string
  label: string
  required: boolean
}

export const IMPORT_FIELDS: Record<string, ImportField[]> = {
  PRODUCT: [
    { key: 'productName', label: '商品名称', required: true },
    { key: 'model', label: '型号', required: false },
    { key: 'brand', label: '品牌', required: false },
    { key: 'skuName', label: '规格', required: true },
    { key: 'salePrice', label: '售价', required: false },
    { key: 'costPrice', label: '进价', required: false },
    { key: 'stock', label: '库存', required: false },
    { key: 'minStock', label: '最低库存', required: false },
  ],
  SUPPLIER: [
    { key: 'name', label: '名称', required: true },
    { key: 'contact', label: '联系人', required: false },
    { key: 'mobile', label: '电话', required: false },
    { key: 'brands', label: '主营品牌', required: false },
    { key: 'accountDays', label: '账期', required: false },
    { key: 'remark', label: '备注', required: false },
  ],
  PURCHASE: [
    { key: 'supplierName', label: '供应商', required: true },
    { key: 'purchaseNo', label: '单号', required: false },
    { key: 'skuName', label: '规格', required: true },
    { key: 'quantity', label: '数量', required: true },
    { key: 'unitCost', label: '进价', required: false },
  ],
  SALE: [
    { key: 'orderNo', label: '单号', required: false },
    { key: 'skuName', label: '规格', required: true },
    { key: 'quantity', label: '数量', required: true },
    { key: 'unitPrice', label: '单价', required: false },
    { key: 'costPrice', label: '成本单价', required: false },
  ],
}

/** 列名和系统字段同名，或包含字段中文名，就先建议这一对。用户可以再改。 */
export function suggestMapping(headers: string[], fields: ImportField[]): Record<string, string> {
  const mapping: Record<string, string> = {}
  for (const field of fields) {
    const hit = headers.find((header) => header === field.label || header.includes(field.label))
    if (hit) mapping[field.key] = hit
  }
  return mapping
}

export function rowError(cells: Record<string, string>, mapping: Record<string, string>, fields: ImportField[]): string | null {
  for (const field of fields) {
    if (!field.required) continue
    const header = mapping[field.key]
    const value = header ? (cells[header] ?? '').trim() : ''
    if (!value) return `缺少「${field.label}」`
  }
  return null
}

export function yuanTextToFen(raw: string): number | null {
  const text = raw.trim().replace(/[,¥￥元]/g, '')
  if (!text) return null
  const value = Number(text)
  if (!Number.isFinite(value)) return null
  return Math.round(value * 100)
}
