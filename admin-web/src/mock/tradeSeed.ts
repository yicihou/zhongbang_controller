/**
 * Mock 里的进销存种子：供应商、采购、进价、开票和不开票的订单、低库存规格。
 * 不读网络。金额是分。
 */
import type { AdminOrder, AdminProduct, RepairTicket } from '@/api/types'

export interface SupplierRow {
  id: number
  name: string
  contact: string
  mobile: string
  brands: string
  accountDays: number
  remark: string
  payable?: number
  paid?: number
  balance?: number
}

export interface LedgerRow {
  id: number
  supplierId: number
  purchaseId?: number
  entryType: 'PAYABLE' | 'PAID' | 'VOID'
  amount: number
  method?: string
  remark?: string
  operatorName: string
  createdAt: string
}

export interface PurchaseLine {
  id: number
  skuId: number
  productName: string
  skuName: string
  quantity: number
  unitCost?: number
  lineAmount?: number
}

export interface PurchaseRow {
  id: number
  purchaseNo: string
  supplierId: number
  supplierName: string
  orderDate: string
  status: 'DRAFT' | 'RECEIVED' | 'VOID'
  invoiced: boolean
  invoiceType?: string
  taxRate?: number
  taxIncluded?: boolean
  attachmentUrls: string[]
  remark?: string
  freightAmount?: number
  goodsAmount?: number
  totalAmount?: number
  lines: PurchaseLine[]
  createdAt: string
}

export interface FinanceSetting {
  costMethod: 'MOVING_AVERAGE' | 'LATEST'
  defaultMinStock: number
  suggestMultiplier: number
  hotTurnoverDays: number
  slowTurnoverDays: number
}

export interface InsightRow {
  id: number
  periodType: string
  periodKey: string
  scope: string
  numbersJson: string
  resultText: string
  operatorName: string
  createdAt: string
}

export interface TradeBundle {
  suppliers: SupplierRow[]
  ledgers: LedgerRow[]
  purchases: PurchaseRow[]
  setting: FinanceSetting
  insights: InsightRow[]
}

export function buildTradeBundle(products: AdminProduct[], orders: AdminOrder[], repairs: RepairTicket[]): TradeBundle {
  const a5 = products.find((item) => item.id === 101)
  if (a5) {
    const standard = a5.skus.find((sku) => sku.id === 1011)
    const thick = a5.skus.find((sku) => sku.id === 1012)
    if (standard) {
      standard.costPrice = 180000
      standard.minStock = 2
    }
    if (thick) {
      thick.costPrice = 190000
      thick.stock = 1
      thick.minStock = 4
    }
  }
  products.push({
    id: 105,
    name: '拷边机 K2',
    brand: '标准牌',
    model: 'K2',
    status: 'ON',
    price: 89000,
    stock: 1,
    createdAt: '2026-11-20 10:00:00',
    updatedAt: '2026-11-20 10:00:00',
    skus: [{ id: 1051, title: '家用拷边', price: 89000, stock: 1, costPrice: 40000, minStock: 3 }],
  })

  const first = orders[0]
  const second = orders[1]
  if (first) {
    first.invoiced = true
    first.invoiceType = '增值税普通发票'
    first.taxRate = 1300
    first.taxIncluded = true
    const line = first.items[0]
    if (line) {
      line.costKnown = true
      line.costPrice = 180000
      line.costAmount = 180000 * line.quantity
    }
    const extra = first.items[1]
    if (extra) extra.costKnown = false
  }
  if (second) {
    second.invoiced = false
    const line = second.items[0]
    if (line) {
      line.costKnown = true
      line.costPrice = 180000
      line.costAmount = 180000 * line.quantity
    }
  }
  const paidRepair = repairs[0]
  if (paidRepair) {
    if ((paidRepair.paidAmount ?? 0) <= 0) paidRepair.paidAmount = 15000
    paidRepair.invoiced = true
    paidRepair.invoiceType = '增值税普通发票'
    paidRepair.taxRate = 1300
    paidRepair.taxIncluded = true
  }
  repairs.push({
    id: 920300099,
    ticketNo: 'RT-DEMO-PLAIN',
    customerId: 1002,
    customerName: '演示维修',
    status: 'DONE',
    contactMobile: '13700001111',
    payableAmount: 8000,
    paidAmount: 8000,
    invoiced: false,
    createdAt: '2026-11-18 11:00:00',
    updatedAt: '2026-11-18 11:00:00',
    lines: [],
    payments: [],
  })

  return {
    setting: {
      costMethod: 'MOVING_AVERAGE',
      defaultMinStock: 2,
      suggestMultiplier: 2,
      hotTurnoverDays: 30,
      slowTurnoverDays: 90,
    },
    suppliers: [
      { id: 301, name: '演示-华东缝配', contact: '周明', mobile: '13800002001', brands: '标准牌', accountDays: 30, remark: '开票月结', payable: 165000, paid: 100000, balance: 65000 },
      { id: 302, name: '演示-本地现货', contact: '赵姐', mobile: '13800002002', brands: '包缝机', accountDays: 0, remark: '现款，不开票', payable: 0, paid: 0, balance: 0 },
    ],
    ledgers: [
      { id: 401, supplierId: 301, purchaseId: 501, entryType: 'PAYABLE', amount: 165000, remark: '采购入库', operatorName: '老板', createdAt: '2026-11-10 09:00:00' },
      { id: 402, supplierId: 301, purchaseId: 501, entryType: 'PAID', amount: 100000, method: 'BANK_TRANSFER', remark: '已付一部分', operatorName: '老板', createdAt: '2026-11-12 15:00:00' },
    ],
    purchases: [
      {
        id: 501,
        purchaseNo: 'PO202611100001',
        supplierId: 301,
        supplierName: '演示-华东缝配',
        orderDate: '2026-11-10',
        status: 'RECEIVED',
        invoiced: true,
        invoiceType: '增值税专用发票',
        taxRate: 1300,
        taxIncluded: true,
        attachmentUrls: [],
        remark: '开票入库',
        freightAmount: 5000,
        goodsAmount: 160000,
        totalAmount: 165000,
        createdAt: '2026-11-10 09:00:00',
        lines: [{ id: 601, skuId: 1011, productName: '电脑平缝机 A5', skuName: '标准台板 + 伺服电机', quantity: 2, unitCost: 80000, lineAmount: 160000 }],
      },
      {
        id: 502,
        purchaseNo: 'PO202611200002',
        supplierId: 302,
        supplierName: '演示-本地现货',
        orderDate: '2026-11-20',
        status: 'DRAFT',
        invoiced: false,
        attachmentUrls: [],
        remark: '不开票草稿',
        freightAmount: 0,
        goodsAmount: 40000,
        totalAmount: 40000,
        createdAt: '2026-11-20 09:00:00',
        lines: [{ id: 602, skuId: 1051, productName: '拷边机 K2', skuName: '家用拷边', quantity: 1, unitCost: 40000, lineAmount: 40000 }],
      },
    ],
    insights: [],
  }
}
