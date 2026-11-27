<template>
  <el-dialog v-model="open" :title="isCorrection ? '更正已入库子单' : '编辑采购草稿'"
    width="min(760px, 94vw)" :close-on-click-modal="false" :before-close="beforeClose">
    <el-form v-if="order" label-position="top">
      <div class="editor-grid">
        <el-form-item label="供应商">
          <el-select v-model="supplierId" :disabled="isCorrection" filterable :loading="loadingSuppliers">
            <el-option v-for="item in suppliers" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购日期">
          <el-date-picker v-model="orderDate" type="date" value-format="YYYY-MM-DD" :clearable="false" />
        </el-form-item>
        <el-form-item label="开票"><el-switch v-model="invoiced" /></el-form-item>
        <el-form-item v-if="session.isBoss" label="运费（元）">
          <el-input-number v-model="freight" :min="0" :precision="2" />
        </el-form-item>
        <template v-if="invoiced">
          <el-form-item label="发票类型"><el-input v-model="invoiceType" maxlength="32" /></el-form-item>
          <el-form-item label="税率（%）"><el-input-number v-model="taxRate" :min="0" :max="100" :precision="2" /></el-form-item>
          <el-form-item label="金额含税">
            <el-select v-model="taxIncluded" clearable placeholder="未填写">
              <el-option label="含税" value="INCLUDED" />
              <el-option label="未含税" value="EXCLUDED" />
            </el-select>
          </el-form-item>
        </template>
      </div>
      <el-form-item label="采购明细">
        <div class="editor-lines">
          <div class="editor-line" v-for="(line, index) in lines" :key="line.skuId" :class="{ 'editor-line-priced': session.isBoss }">
            <div><strong>{{ line.productName }}</strong><div class="muted">{{ line.skuName }}</div></div>
            <el-input-number v-model="line.quantity" :min="1" :max="2147483647" :precision="0" size="small" aria-label="数量" />
            <el-input-number v-if="session.isBoss" v-model="line.cost" :min="0" :precision="2" size="small" placeholder="进价" aria-label="进价（元）" :controls="false" />
            <el-button link type="danger" @click="lines.splice(index, 1)">移除</el-button>
          </div>
          <el-select v-model="pickedSku" filterable remote :remote-method="searchSkus" :loading="loadingSkus" placeholder="搜索商品，添加规格"
            aria-label="添加规格" @visible-change="openSkuPicker" @change="addSku">
            <el-option v-for="sku in availableSkus" :key="sku.skuId" :label="`${sku.productName} / ${sku.skuName}`" :value="sku.skuId" />
          </el-select>
        </div>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      <el-form-item v-if="isCorrection" label="更正原因" required>
        <el-input v-model="reason" type="textarea" :rows="2" maxlength="240" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!valid" @click="save">{{ isCorrection ? '提交更正' : '保存修改' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { correctPurchase, listProducts, purchaseSuppliers, updatePurchase } from '@/api/admin'
import type { PurchaseDraft, PurchaseSupplier, SupplierOrder } from '@/api/purchase'
import { fenToYuanNumber, yuanToFen } from '@/domain/money'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

interface SkuOption {
  skuId: number
  productName: string
  skuName: string
}

interface EditorLine extends SkuOption {
  quantity?: number
  cost?: number
}

const order = defineModel<SupplierOrder | null>({ required: true })
const emit = defineEmits<{ saved: [] }>()
const session = useSessionStore()
const supplierId = ref<number>()
const suppliers = ref<PurchaseSupplier[]>([])
const orderDate = ref('')
const freight = ref<number>()
const invoiced = ref(false)
const invoiceType = ref('')
const taxRate = ref<number>()
const taxIncluded = ref('')
const remark = ref('')
const reason = ref('')
const lines = ref<EditorLine[]>([])
const saving = ref(false)
const loadingSuppliers = ref(false)
const loadingSkus = ref(false)
const skuOptions = ref<SkuOption[]>([])
const pickedSku = ref<number>()
let skuRequest = 0

const isCorrection = computed(() => order.value?.purchase.status === 'RECEIVED')
const open = computed({ get: () => !!order.value, set: (value) => { if (!value) close() } })
const valid = computed(() => !!supplierId.value && !!orderDate.value && lines.value.length > 0 &&
  lines.value.every((line) => !!line.quantity && (!isCorrection.value || line.cost != null)) &&
  (!isCorrection.value || !!reason.value.trim()))
const availableSkus = computed(() => skuOptions.value.filter((sku) => !lines.value.some((line) => line.skuId === sku.skuId)))

watch(order, async (selected) => {
  if (!selected) return
  const purchase = selected.purchase
  supplierId.value = purchase.supplierId
  suppliers.value = [{ id: purchase.supplierId, name: purchase.supplierName }]
  orderDate.value = purchase.orderDate
  freight.value = fenToYuanNumber(purchase.freightAmount ?? 0)
  invoiced.value = purchase.invoiced
  invoiceType.value = purchase.invoiceType ?? ''
  taxRate.value = purchase.taxRate == null ? undefined : purchase.taxRate / 100
  taxIncluded.value = purchase.taxIncluded == null ? '' : purchase.taxIncluded ? 'INCLUDED' : 'EXCLUDED'
  remark.value = purchase.remark ?? ''
  reason.value = ''
  lines.value = purchase.lines.map((line) => ({
    skuId: line.skuId, productName: line.productName, skuName: line.skuName, quantity: line.quantity,
    cost: line.unitCost == null ? undefined : fenToYuanNumber(line.unitCost),
  }))
  if (purchase.status === 'DRAFT') {
    loadingSuppliers.value = true
    await run(async () => {
      const data = await purchaseSuppliers()
      if (order.value === selected) suppliers.value = data
    })
    loadingSuppliers.value = false
  }
})

function beforeClose(done: () => void) {
  if (!saving.value) done()
}

function close() {
  if (!saving.value) order.value = null
}

async function searchSkus(keyword: string) {
  const id = ++skuRequest
  loadingSkus.value = true
  await run(async () => {
    const data = await listProducts({ keyword: keyword.trim(), page: 1, size: 30 })
    if (id !== skuRequest) return
    skuOptions.value = data.list.flatMap((product) =>
      product.skus.map((sku) => ({ skuId: sku.id, productName: product.name, skuName: sku.title })),
    )
  })
  if (id === skuRequest) loadingSkus.value = false
}

function openSkuPicker(visible: boolean) {
  if (visible && !skuOptions.value.length) void searchSkus('')
}

function addSku(id: number) {
  const sku = availableSkus.value.find((item) => item.skuId === id)
  if (sku) lines.value.push({ ...sku, quantity: 1 })
  pickedSku.value = undefined
}

async function save() {
  const selected = order.value
  if (!selected || !supplierId.value || !valid.value || saving.value) return
  const body: PurchaseDraft = {
    supplierId: supplierId.value,
    orderDate: orderDate.value,
    revision: selected.revision,
    invoiced: invoiced.value,
    invoiceType: invoiced.value ? invoiceType.value.trim() : undefined,
    taxRate: invoiced.value && taxRate.value != null ? Math.round(taxRate.value * 100) : undefined,
    taxIncluded: invoiced.value && taxIncluded.value ? taxIncluded.value === 'INCLUDED' : undefined,
    freightAmount: session.isBoss ? yuanToFen(freight.value ?? 0) : undefined,
    remark: remark.value.trim(),
    attachmentUrls: selected.purchase.attachmentUrls,
    lines: lines.value.map((line) => ({
      skuId: line.skuId, quantity: line.quantity ?? 0,
      unitCost: session.isBoss && line.cost != null ? yuanToFen(line.cost) : undefined,
    })),
  }
  saving.value = true
  const ok = await run(async () => {
    if (isCorrection.value) {
      await correctPurchase(selected.purchase.id, { reason: reason.value.trim(), revision: selected.revision, purchase: body })
    } else {
      await updatePurchase(selected.purchase.id, body)
    }
  }, isCorrection.value ? '更正已保存' : '采购草稿已保存')
  saving.value = false
  if (ok) {
    close()
    emit('saved')
  }
}
</script>

<style scoped>
.editor-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.editor-grid :deep(.el-select) { width: 100%; }
.editor-lines { width: 100%; }
.editor-line { display: grid; grid-template-columns: minmax(110px, 1fr) 100px auto; gap: 8px; align-items: center; padding: 4px 0 9px; }
.editor-line-priced { grid-template-columns: minmax(100px, 1fr) 100px 94px auto; }
.editor-line :deep(.el-input-number) { width: 100%; }
@media (max-width: 540px) { .editor-grid { grid-template-columns: 1fr; } .editor-line-priced { grid-template-columns: minmax(75px, 1fr) 78px 66px auto; } }
</style>
