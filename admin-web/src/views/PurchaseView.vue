<template>
  <div>
    <div class="page-head">
      <div>
        <h2>采购入库</h2>
        <p>草稿确认后增加库存。作废已入库的单子会把库存减回去。进价和金额只有老板能看。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建采购单</el-button>
    </div>
    <div class="panel">
      <el-table :data="rows">
        <el-table-column prop="purchaseNo" label="单号" width="170" />
        <el-table-column prop="supplierName" label="供应商" min-width="140" />
        <el-table-column prop="orderDate" label="日期" width="120" />
        <el-table-column label="开票" width="90">
          <template #default="{ row }">{{ row.invoiced ? '开票' : '不开票' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(String(row.status)) }}</template>
        </el-table-column>
        <el-table-column v-if="session.isBoss" label="合计" width="120">
          <template #default="{ row }">¥{{ formatYuan(Number(row.totalAmount ?? 0)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="receive(Number(row.id))">入库</el-button>
            <el-button v-if="row.status !== 'VOID'" link type="danger" @click="voidOne(Number(row.id))">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="visible" title="采购单" width="640px">
      <el-form label-width="88px">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" style="width: 100%">
            <el-option v-for="item in suppliers" :key="String(item.id)" :label="String(item.name)" :value="Number(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票">
          <el-switch v-model="form.invoiced" />
        </el-form-item>
        <el-form-item v-if="form.invoiced" label="发票类型">
          <el-input v-model="form.invoiceType" placeholder="例如增值税专用发票" />
        </el-form-item>
        <el-form-item v-if="form.invoiced" label="税率(%)">
          <el-input-number v-model="form.taxPercent" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item v-if="session.isBoss" label="运费(元)">
          <el-input-number v-model="form.freightYuan" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="规格">
          <el-select v-model="form.skuId" style="width: 100%">
            <el-option v-for="sku in skuOptions" :key="sku.id" :label="sku.label" :value="sku.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="1" />
        </el-form-item>
        <el-form-item v-if="session.isBoss" label="进价(元)">
          <el-input-number v-model="form.costYuan" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listProducts, listPurchases, listSuppliers, receivePurchase, savePurchase, voidPurchase } from '@/api/admin'
import { formatYuan, yuanToFen } from '@/domain/money'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()
const rows = ref<Record<string, unknown>[]>([])
const suppliers = ref<Record<string, unknown>[]>([])
const skuOptions = ref<{ id: number; label: string }[]>([])
const visible = ref(false)
const form = reactive({
  supplierId: undefined as number | undefined,
  invoiced: false,
  invoiceType: '',
  taxPercent: 13,
  freightYuan: 0,
  skuId: undefined as number | undefined,
  quantity: 1,
  costYuan: 0,
})

function statusText(status: string) {
  if (status === 'RECEIVED') return '已入库'
  if (status === 'VOID') return '作废'
  return '草稿'
}

async function reload() {
  await run(async () => {
    rows.value = (await listPurchases()).list
  })
}

async function openCreate() {
  suppliers.value = (await listSuppliers()).list
  const products = await listProducts({ size: 100 })
  skuOptions.value = products.list.flatMap((product) => product.skus.map((sku) => ({ id: sku.id, label: `${product.name} / ${sku.title}` })))
  visible.value = true
}

async function save() {
  await run(async () => {
    await savePurchase({
      supplierId: form.supplierId,
      invoiced: form.invoiced,
      invoiceType: form.invoiceType,
      taxRate: form.invoiced ? Math.round(form.taxPercent * 100) : undefined,
      taxIncluded: form.invoiced ? true : undefined,
      freightAmount: session.isBoss ? yuanToFen(form.freightYuan) : undefined,
      lines: [{ skuId: form.skuId, quantity: form.quantity, unitCost: session.isBoss ? yuanToFen(form.costYuan) : undefined }],
    })
    visible.value = false
    await reload()
  })
}

async function receive(id: number) {
  await run(async () => {
    await receivePurchase(id)
    await reload()
  })
}

async function voidOne(id: number) {
  await run(async () => {
    await voidPurchase(id)
    await reload()
  })
}

onMounted(reload)
</script>
