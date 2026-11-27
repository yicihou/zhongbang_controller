export type PurchaseStatus = 'DRAFT' | 'PARTIAL' | 'RECEIVED' | 'VOID'

export interface PurchaseSupplier {
  id: number
  name: string
  brands?: string
}

export interface PurchaseSelection {
  skuId: number
  quantity: number
  supplierId?: number
}

export interface BatchRequest {
  requestId: string
  lines: PurchaseSelection[]
}

export interface PurchaseDraft {
  supplierId: number
  orderDate: string
  revision: number
  invoiced: boolean
  invoiceType?: string
  taxRate?: number
  taxIncluded?: boolean
  freightAmount?: number
  attachmentUrls?: string[]
  remark?: string
  lines: (Pick<PurchaseSelection, 'skuId' | 'quantity'> & { unitCost?: number })[]
}

export interface PurchaseLine {
  id: number
  skuId: number
  productName: string
  skuName: string
  quantity: number
  unitCost?: number
}

export interface PurchaseDetail {
  id: number
  purchaseNo: string
  supplierId: number
  supplierName: string
  orderDate: string
  status: PurchaseStatus
  invoiced: boolean
  invoiceType?: string
  taxRate?: number
  taxIncluded?: boolean
  attachmentUrls?: string[]
  remark?: string
  operatorName?: string
  createdAt: string
  freightAmount?: number
  totalAmount?: number
  lines: PurchaseLine[]
}

export interface PurchaseCorrection {
  id: number
  reason: string
  operatorName: string
  createdAt: string
  before: PurchaseDetail
  after: PurchaseDetail
}

export interface SupplierOrder {
  purchase: PurchaseDetail
  contact?: string
  mobile?: string
  revision: number
  corrections: PurchaseCorrection[]
}

export interface PurchaseBatchRow {
  id: number
  purchaseNo: string
  orderDate: string
  status: PurchaseStatus
  suppliers: string[]
  totalAmount?: number
}

export interface PurchaseBatch {
  summary: PurchaseBatchRow
  orders: SupplierOrder[]
}
