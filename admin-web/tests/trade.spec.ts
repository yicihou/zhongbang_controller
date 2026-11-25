/** 进价、毛利口径、建议采购量和导入列映射。非老板的 Mock 响应里没有进价。 */
import { beforeEach, describe, expect, it } from 'vitest'
import { paths } from '../src/api/paths'
import type { PageResult, StaffLoginResponse } from '../src/api/types'
import { marginText, movingAverage, rowError, splitTax, suggestMapping, suggestQty, IMPORT_FIELDS } from '../src/domain/trade'
import { handle } from '../src/mock/handle'
import { resetDb } from '../src/mock/db'

describe('trade math', () => {
  it('splits tax, averages cost, and suggests a purchase quantity', () => {
    expect(splitTax(11300, true, true, 1300)).toEqual({ included: 11300, excluded: 10000 })
    expect(splitTax(10000, true, false, 1300).included).toBe(11300)
    expect(splitTax(10000, false, null, null)).toEqual({ included: 10000, excluded: 10000 })
    expect(movingAverage(null, 8, 160000, 2)).toBe(160000)
    expect(movingAverage(100000, 8, 160000, 2)).toBe(112000)
    expect(suggestQty(6, 2, 5, 3)).toBe(4)
    expect(marginText(3250, 10000)).toBe('32.50')
    expect(marginText(1, 0)).toBeNull()
  })

  it('maps columns and reports a missing required field without failing the whole file', () => {
    const fields = IMPORT_FIELDS.PRODUCT!
    const mapping = suggestMapping(['商品名称', '规格名称'], fields)
    expect(mapping.productName).toBe('商品名称')
    expect(rowError({ 商品名称: '平缝机', 规格名称: '' }, { productName: '商品名称', skuName: '规格名称' }, fields)).toContain('规格')
    expect(rowError({ 商品名称: '平缝机', 规格名称: '标准' }, { productName: '商品名称', skuName: '规格名称' }, fields)).toBeNull()
  })
})

describe('trade mock permissions', () => {
  beforeEach(() => resetDb())

  it('hides cost and margin from clerks and shows suppliers to the boss', () => {
    const clerk = (handle('POST', paths.staffLogin, {}, { username: 'clerk', password: 'clerk123' }, '').body.data as StaffLoginResponse).token
    expect(handle('GET', paths.margin, {}, null, clerk).body.code).toBe(2003)
    expect(handle('GET', paths.supplierLedger(301), {}, null, clerk).body.code).toBe(2003)
    const products = handle('GET', paths.products, {}, null, clerk).body.data as PageResult<{ skus: { costPrice?: number }[] }>
    expect(products.list[0]?.skus[0]).not.toHaveProperty('costPrice')

    const boss = (handle('POST', paths.staffLogin, {}, { username: 'boss', password: 'boss123' }, '').body.data as StaffLoginResponse).token
    const report = handle('GET', paths.margin, { scope: 'INVOICED' }, null, boss).body.data as { current: { salesTaxIncluded: number; salesTaxExcluded: number } }
    expect(report.current.salesTaxIncluded).toBeGreaterThan(0)
    const all = handle('GET', paths.margin, { scope: 'ALL' }, null, boss).body.data as { current: { unknownSalesAmount: number } }
    expect(all.current.unknownSalesAmount).toBeGreaterThan(0)
    const alerts = handle('GET', paths.stockAlerts, {}, null, clerk).body.data as { list: { skuId: number; suggestQty: number }[] }
    expect(alerts.list.some((row) => row.skuId === 1012 && row.suggestQty > 0)).toBe(true)
    const preview = handle('POST', paths.importsPreview, {}, { kind: 'PRODUCT', csv: '商品名称,规格\n机,标准\n缺规格,\n' }, boss).body.data as {
      rows: { error: string | null }[]
    }
    expect(preview.rows[1]?.error).toContain('规格')
    const insight = handle('POST', paths.marginInsights, {}, { period: 'MONTH' }, boss).body.data as { message: string }
    expect(insight.message).toContain('DEEPSEEK_API_KEY')
  })
})
