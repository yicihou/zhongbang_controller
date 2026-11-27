import type { PurchaseStatus, PurchaseSupplier } from '@/api/purchase'
import type { AdminProduct } from '@/api/types'

export const purchaseStatus: Record<PurchaseStatus, { text: string; type: 'info' | 'warning' | 'success' }> = {
  DRAFT: { text: '待入库', type: 'warning' },
  PARTIAL: { text: '部分入库', type: 'warning' },
  RECEIVED: { text: '已入库', type: 'success' },
  VOID: { text: '已作废', type: 'info' },
}

export function matchPurchaseSupplier(product: AdminProduct, suppliers: PurchaseSupplier[]): number | undefined {
  if (product.purchaseSupplierId != null) {
    return suppliers.find((supplier) => supplier.id === product.purchaseSupplierId)?.id
  }
  const brand = product.brand?.trim().toLowerCase()
  if (!brand) return undefined
  const named = suppliers.filter((supplier) => supplier.name.trim().toLowerCase() === brand)
  if (named.length) return named.length === 1 ? named[0].id : undefined
  const carrying = suppliers.filter((supplier) =>
    supplier.brands?.split(/[,，、;；]/).some((item) => item.trim().toLowerCase() === brand),
  )
  return carrying.length === 1 ? carrying[0].id : undefined
}
