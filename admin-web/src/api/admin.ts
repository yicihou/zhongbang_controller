/**
 * 管理端每个按钮对应的函数。它们只拼路径和请求体，规则在 Mock 的 domain 或 Java 的 Service 里。
 * 金额字段单位是分。关掉 Mock 之后，这些路径打 Spring Boot。未带令牌是 401。
 */
import type {
  AdminNotice,
  AdminOrder,
  AdjustPriceRequest,
  AssignOrderRequest,
  CancelOrderRequest,
  CreatePaymentsRequest,
  CreateShipmentRequest,
  FreightRequest,
  NoticeQuery,
  OfflineConfirmRequest,
  OrderQuery,
  PageQuery,
  PageResult,
  PatchPaymentRequest,
  Payment,
  Receivable,
  RepairAssignRequest,
  RepairQuery,
  RepairQuoteRequest,
  RepairTicket,
  ScheduleQuery,
  StaffLoginResponse,
  Technician,
  TechnicianSchedule,
  PendingInstall,
  StockLogRow,
  AdminProduct,
  ProductOptions,
  ChangeProductPriceRequest,
  SaveProductRequest,
  UploadedImage,
} from './types'
import type { BatchRequest, PurchaseBatch, PurchaseBatchRow, PurchaseDetail, PurchaseDraft, PurchaseSupplier } from './purchase'
import type { StaffMember, StaffSave } from '@/domain/staff'
import type { CustomerListItem, LevelRule } from '@/domain/customer'
import { API_BASE, USE_MOCK } from '@/config'
import { ApiError } from '@/domain/errors'
import { paths } from './paths'
import type { MarginHistory, MarginProduct, MarginQuery, MarginReport, MarginScope } from './margin'
import { readToken, request, requestForm } from './http'

export function staffLogin(username: string, password: string): Promise<StaffLoginResponse> {
  return request<StaffLoginResponse>('POST', paths.staffLogin, {
    auth: false,
    body: { username, password },
  })
}

export function listOrders(query: OrderQuery = {}): Promise<PageResult<AdminOrder>> {
  return request('GET', paths.orders, { query })
}

export function getOrder(id: number): Promise<AdminOrder> {
  return request('GET', paths.order(id))
}

export function adjustOrderPrice(id: number, body: AdjustPriceRequest): Promise<AdminOrder> {
  return request('POST', paths.adjustPrice(id), { body })
}

export function setOrderFreight(id: number, body: FreightRequest): Promise<AdminOrder> {
  return request('POST', paths.freight(id), { body })
}

export function createOrderPayments(id: number, body: CreatePaymentsRequest): Promise<AdminOrder> {
  return request('POST', paths.orderPayments(id), { body })
}

export function updatePayment(id: number, body: PatchPaymentRequest): Promise<Payment> {
  return request('PATCH', paths.payment(id), { body })
}

export function offlineConfirm(id: number, body: OfflineConfirmRequest): Promise<Payment> {
  return request('POST', paths.offlineConfirm(id), { body })
}

export function createShipment(id: number, body: CreateShipmentRequest): Promise<AdminOrder> {
  return request('POST', paths.shipments(id), { body })
}

export function assignOrder(id: number, body: AssignOrderRequest): Promise<AdminOrder> {
  return request('POST', paths.assignOrder(id), { body })
}

export function cancelOrder(id: number, body: CancelOrderRequest): Promise<AdminOrder> {
  return request('POST', paths.cancelOrder(id), { body })
}

export function listRepairs(query: RepairQuery = {}): Promise<PageResult<RepairTicket>> {
  return request('GET', paths.repairs, { query })
}

export function getRepair(id: number): Promise<RepairTicket> {
  return request('GET', paths.repair(id))
}

export function quoteRepair(id: number, body: RepairQuoteRequest): Promise<RepairTicket> {
  return request('POST', paths.repairQuote(id), { body })
}

export function assignRepair(id: number, body: RepairAssignRequest): Promise<RepairTicket> {
  return request('POST', paths.repairAssign(id), { body })
}

export function completeRepair(id: number): Promise<RepairTicket> {
  return request('POST', paths.repairComplete(id))
}

export function cancelRepair(id: number, body: CancelOrderRequest): Promise<RepairTicket> {
  return request('POST', paths.repairCancel(id), { body })
}

export function listNotices(query: NoticeQuery = {}): Promise<PageResult<AdminNotice>> {
  return request('GET', paths.notices, { query })
}

export function listReceivables(query: PageQuery = {}): Promise<PageResult<Receivable>> {
  return request('GET', paths.receivables, { query })
}

export function listTechnicians(): Promise<Technician[]> {
  return request('GET', paths.technicians)
}

export function listStaffMembers(query: { keyword?: string; role?: string; status?: string; page?: number; size?: number } = {}): Promise<PageResult<StaffMember>> {
  return request('GET', paths.staff, { query })
}

export function createStaff(body: StaffSave): Promise<StaffMember> {
  return request('POST', paths.staff, { body })
}

export function updateStaff(ref: string, body: StaffSave): Promise<StaffMember> {
  return request('PUT', paths.staffOne(ref), { body })
}

export function changeStaffStatus(ref: string, status: 'ENABLED' | 'DISABLED'): Promise<StaffMember> {
  return request('POST', paths.staffStatus(ref), { body: { status } })
}

export function resetStaffPassword(ref: string, password: string): Promise<void> {
  return request('POST', paths.staffPassword(ref), { body: { password } })
}

/** 一次可传多张。Mock 用 data URL，真实后端用 multipart，字段名 file。 */
export async function uploadImages(files: File[], biz = 'payment'): Promise<UploadedImage[]> {
  if (USE_MOCK) {
    const payload = await Promise.all(
      files.map(
        (file) =>
          new Promise<{ name: string; type: string; size: number; dataUrl: string }>((resolve, reject) => {
            const reader = new FileReader()
            reader.onload = () =>
              resolve({ name: file.name, type: file.type, size: file.size, dataUrl: String(reader.result ?? '') })
            reader.onerror = () => reject(new Error('读取图片失败'))
            reader.readAsDataURL(file)
          }),
      ),
    )
    return request('POST', paths.uploads, { query: { biz }, body: { files: payload } })
  }
  const form = new FormData()
  for (const file of files) form.append('file', file)
  return requestForm(paths.uploads, form, { biz })
}

export function listSchedules(query: ScheduleQuery = {}): Promise<TechnicianSchedule[]> {
  return request('GET', paths.schedules, { query })
}

export function productOptions(): Promise<ProductOptions> {
  return request('GET', paths.productOptions)
}

export function listProducts(query: { keyword?: string; status?: string; categoryId?: number; brand?: string; page?: number; size?: number } = {}): Promise<PageResult<AdminProduct>> {
  return request('GET', paths.products, { query })
}

export function createProduct(body: SaveProductRequest): Promise<AdminProduct> {
  return request('POST', paths.products, { body })
}

export function updateProduct(id: number, body: SaveProductRequest): Promise<AdminProduct> {
  return request('PUT', paths.product(id), { body })
}

export function deleteProduct(id: number): Promise<void> {
  return request('DELETE', paths.product(id))
}

export function shelveProduct(id: number, on: boolean): Promise<AdminProduct> {
  return request('POST', on ? paths.productOn(id) : paths.productOff(id))
}

export function changeProductPrice(id: number, body: ChangeProductPriceRequest): Promise<AdminProduct> {
  return request('POST', paths.productPrice(id), { body })
}

export interface CustomerQuery {
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

export function listCustomers(query: CustomerQuery = {}): Promise<PageResult<CustomerListItem>> {
  return request('GET', paths.customers, { query })
}

export function getCustomer(id: number | string): Promise<Record<string, unknown>> {
  return request('GET', paths.customer(id))
}

export function createCustomer(body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('POST', paths.customers, { body })
}

export function updateCustomer(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('PUT', paths.customer(id), { body })
}

export function addCustomerFollow(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('POST', paths.customerFollows(id), { body })
}

export function saveCustomerPreference(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('PUT', paths.customerPreference(id), { body })
}

export function assignCustomerLevel(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('PUT', paths.customerLevelAssign(id), { body })
}

export function addCustomerAddress(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('POST', paths.customerAddresses(id), { body })
}

export function addCustomerAgreement(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('POST', paths.customerAgreements(id), { body })
}

export function updateCustomerAgreement(id: number | string, body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return request('PUT', paths.customerAgreement(id), { body })
}

export function fulfillAgreement(id: number | string): Promise<Record<string, unknown>> {
  return request('POST', `${paths.customerAgreement(id)}/fulfill`)
}

export function voidAgreement(id: number | string): Promise<Record<string, unknown>> {
  return request('POST', `${paths.customerAgreement(id)}/void`)
}

export function listRepayments(bucket: string, page = 1): Promise<PageResult<Record<string, unknown>>> {
  return request('GET', paths.customerRepayments, { query: { bucket, page, size: 50 } })
}

export function scanRepayments(): Promise<number> {
  return request('POST', paths.customerRepayScan)
}

export function customerOverview(): Promise<Record<string, unknown>> {
  return request('GET', paths.customerOverview)
}

export function analyzeCustomer(id: number | string): Promise<Record<string, unknown>> {
  return request('POST', paths.customerInsight(id))
}

export function customerInsightHistory(id: number | string): Promise<Record<string, unknown>[]> {
  return request('GET', paths.customerInsight(id))
}

export function analyzeOverall(): Promise<Record<string, unknown>> {
  return request('POST', paths.customerInsights)
}

export function listLevelRules(): Promise<LevelRule[]> {
  return request('GET', paths.customerLevels)
}

export function updateLevelRule(id: number, body: { name: string; minPaid: number; enabled: boolean }): Promise<LevelRule> {
  return request('PUT', paths.customerLevel(id), { body })
}

export function seedDemoCustomers(): Promise<{ created: boolean; customers: number; message: string }> {
  return request('POST', paths.customerDemo)
}

export async function downloadCustomers(query: CustomerQuery, format: 'csv' | 'xlsx'): Promise<void> {
  if (USE_MOCK) {
    const data = await request<{ text: string; filename: string }>('GET', paths.customerExport, { query: { ...query, format } })
    saveText(data.text, format === 'xlsx' ? '客户.csv' : data.filename)
    return
  }
  await saveRemote(`${paths.customerExport}?${toQuery({ ...query, format })}`, format === 'xlsx' ? '客户.xlsx' : '客户.csv')
}

export async function downloadCustomerTemplate(format: 'csv' | 'xlsx'): Promise<void> {
  if (USE_MOCK) {
    const data = await request<{ text: string; filename: string }>('GET', paths.customerTemplate, { query: { format } })
    saveText(data.text, data.filename)
    return
  }
  await saveRemote(`${paths.customerTemplate}?format=${format}`, format === 'xlsx' ? '客户导入模板.xlsx' : '客户导入模板.csv')
}

export function importCustomers(file: File): Promise<{ created: number; skipped: number; errors: string[] }> {
  if (USE_MOCK) {
    return file.text().then((csv) => request('POST', paths.customerImport, { body: { csv } }))
  }
  const form = new FormData()
  form.append('file', file)
  return requestForm(paths.customerImport, form)
}

export function listSuppliers(keyword?: string) {
  return request<PageResult<Record<string, unknown>>>('GET', paths.suppliers, { query: { keyword } })
}

export function saveSupplier(body: Record<string, unknown>, id?: number) {
  return request(id ? 'PUT' : 'POST', id ? paths.supplier(id) : paths.suppliers, { body })
}

export function supplierLedger(id: number) {
  return request<Record<string, unknown>>('GET', paths.supplierLedger(id))
}

export function paySupplier(id: number, body: { amount: number; method?: string; remark?: string }) {
  return request('POST', paths.supplierPay(id), { body })
}

export async function purchaseSuppliers() {
  const suppliers: PurchaseSupplier[] = []
  for (let page = 1; ; page += 1) {
    const data = await request<PageResult<PurchaseSupplier>>('GET', paths.suppliers, { query: { page, size: 100 } })
    suppliers.push(...data.list)
    if (!data.list.length || suppliers.length >= data.total) return suppliers
  }
}

export function listPurchaseBatches(query: PageQuery & { keyword?: string }) {
  return request<PageResult<PurchaseBatchRow>>('GET', paths.purchaseBatches, { query })
}

export function getPurchaseBatch(id: number) {
  return request<PurchaseBatch>('GET', paths.purchaseBatch(id))
}

export function createPurchaseBatch(body: BatchRequest) {
  return request<PurchaseBatch>('POST', paths.purchaseBatches, { body })
}

export function updatePurchase(id: number, body: PurchaseDraft) {
  return request<PurchaseDetail>('PUT', paths.purchase(id), { body })
}

export function correctPurchase(id: number, body: { reason: string; revision: number; purchase: PurchaseDraft }) {
  return request<PurchaseDetail>('POST', paths.purchaseCorrection(id), { body })
}

export function receivePurchase(id: number, revision: number) {
  return request('POST', paths.purchaseReceive(id), { query: { revision } })
}

export function voidPurchase(id: number, revision: number) {
  return request('POST', paths.purchaseVoid(id), { query: { revision } })
}

export function listStockAlerts() {
  return request<{ defaultMinStock: number; suggestMultiplier: number; list: Record<string, unknown>[] }>('GET', paths.stockAlerts)
}

export function setMinStock(skuId: number, minStock: number | null) {
  return request('PUT', paths.stockMin(skuId), { body: { minStock } })
}

/** 库存流水。所有的进出都记在这里，老板按它对库存。 */
export function listStockLogs(query: { skuId?: number; bizType?: string; from?: string; to?: string; page?: number; size?: number }) {
  return request<PageResult<StockLogRow>>('GET', paths.stockLogs, { query: query as Record<string, unknown> })
}

/** 盘点调整：填实际数量，系统算差额记一笔 ADJUST。只有老板能用。 */
export function adjustStock(body: { skuId: number; actualStock: number; reason?: string }) {
  return request<{ skuId: number; beforeStock: number; actualStock: number; delta: number; reason: string }>(
    'POST',
    paths.stockAdjust,
    { body },
  )
}

/** 还没排师傅的安装行，排期页的排单弹窗用它挑订单。 */
export function listPendingInstalls() {
  return request<PendingInstall[]>('GET', paths.pendingInstalls)
}

export function marginReport(query: MarginQuery) {
  return request<MarginReport>('GET', paths.margin, { query })
}

export function marginProducts(query: MarginQuery) {
  return request<{ list: MarginProduct[] }>('GET', paths.marginProducts, { query })
}

export function marginTrend(query: { month: string; from?: string; through?: string; scope: MarginScope }) {
  return request<MarginHistory>('GET', paths.marginHistory, { query })
}

export function askMarginInsight(body: MarginQuery) {
  return request<{ configured: boolean; saved: boolean; message: string; analysis?: { resultText: string } | null }>('POST', paths.marginInsights, { body })
}

export function marginHistory(query: { period?: string; key?: string; scope?: string }) {
  return request<Record<string, unknown>[]>('GET', paths.marginInsights, { query })
}

export function setSkuCost(skuId: number, costPrice: number) {
  return request('PUT', paths.skuCost(skuId), { body: { costPrice } })
}

export function markOrderInvoice(id: number, body: { invoiced: boolean; invoiceType?: string; taxRate?: number; taxIncluded?: boolean }) {
  return request('PUT', paths.orderInvoice(id), { body })
}

export function previewImport(kind: string, file: File) {
  if (USE_MOCK) {
    return file.text().then((csv) => request<Record<string, unknown>>('POST', paths.importsPreview, { body: { kind, csv } }))
  }
  const form = new FormData()
  form.append('file', file)
  return requestForm<Record<string, unknown>>(paths.importsPreview, form, { kind })
}

export function commitImport(token: string, mapping: Record<string, string>) {
  return request<{ created: number; skipped: number; errors: string[] }>('POST', paths.importsCommit, { body: { token, mapping } })
}

export async function downloadImportTemplate(kind: string): Promise<void> {
  if (USE_MOCK) {
    const data = await request<{ text: string; filename: string }>('GET', paths.importsTemplate, { query: { kind } })
    saveText(data.text, data.filename)
    return
  }
  await saveRemote(`${paths.importsTemplate}?kind=${kind}&format=csv`, `导入模板-${kind}.csv`)
}

export async function downloadMargin(query: MarginQuery): Promise<void> {
  if (USE_MOCK) {
    const data = await request<{ text: string; filename: string }>('GET', paths.marginExport, { query })
    saveText(data.text, data.filename)
    return
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value) params.set(key, value)
  await saveRemote(`${paths.marginExport}?${params.toString()}`, '毛利报表.xlsx')
}

export function seedTradeDemo() {
  return request<{ created: boolean; message: string }>('POST', paths.tradeDemo)
}

function toQuery(query: object): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value != null && value !== '') params.set(key, String(value))
  }
  return params.toString()
}

function saveText(text: string, filename: string): void {
  saveBlob(new Blob([text], { type: 'text/csv;charset=utf-8' }), filename)
}

async function saveRemote(pathAndQuery: string, filename: string): Promise<void> {
  const token = readToken()
  const response = await fetch(`${API_BASE}${pathAndQuery}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!response.ok) throw new ApiError(response.status === 403 ? 2003 : 1001, '下载失败')
  saveBlob(await response.blob(), filename)
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
