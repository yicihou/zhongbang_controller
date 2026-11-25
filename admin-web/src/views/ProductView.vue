<template>
  <div>
    <div class="page-head">
      <div>
        <h2>商品</h2>
        <p>上架后小程序才能看到。价格单位在页面上是元，提交给接口时换成了分。图片只填地址。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增商品</el-button>
    </div>
    <div class="panel">
      <div class="filters">
        <el-input v-model="keyword" clearable placeholder="名称 / 型号" style="width: 220px" @keyup.enter="reload" />
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
          <el-option label="上架" value="ON" />
          <el-option label="下架" value="OFF" />
        </el-select>
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <b>{{ row.name }}</b>
            <div class="muted">{{ row.model || '未填型号' }} · {{ row.brand || '未填品牌' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="图片" min-width="180">
          <template #default="{ row }">
            <span class="muted">{{ row.coverImage || '未填地址' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="120">
          <template #default="{ row }"><span class="money">¥{{ formatYuan(row.price) }}</span></template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            <span>{{ row.stock }}</span>
            <el-tag v-if="isLowStock(row)" type="danger" effect="dark" size="small" class="low-stock-tag">低库存</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="session.isBoss" label="进价" width="120">
          <template #default="{ row }">
            <span v-if="row.skus?.[0]?.costPrice != null">¥{{ formatYuan(row.skus[0].costPrice) }}</span>
            <span v-else class="muted">未设</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON' ? 'success' : 'info'">{{ row.status === 'ON' ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openPrice(row)">改价</el-button>
            <el-button v-if="session.isBoss" link type="primary" @click="openCost(row)">进价</el-button>
            <el-button link type="primary" @click="toggle(row)">{{ row.status === 'ON' ? '下架' : '上架' }}</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑商品' : '新增商品'" width="640px">
      <el-form label-width="88px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" clearable placeholder="选择分类" style="width: 100%">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" />
        </el-form-item>
        <el-form-item label="图片地址">
          <el-input v-model="form.coverImage" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，例如 现货,含上门安装" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ON">上架</el-radio>
            <el-radio value="OFF">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规格">
          <div class="sku" v-for="(sku, index) in form.skus" :key="index">
            <el-input v-model="sku.title" placeholder="规格名" />
            <el-input-number v-model="sku.priceYuan" :min="0" :precision="2" :step="10" />
            <el-input-number v-model="sku.stock" :min="0" :step="1" />
            <el-button link type="danger" @click="form.skus.splice(index, 1)">删除</el-button>
          </div>
          <el-button @click="form.skus.push({ title: '', priceYuan: 0, stock: 0 })">加一条规格</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="priceVisible" title="改价" width="420px">
      <el-form label-width="88px">
        <el-form-item label="规格">
          <el-select v-model="priceForm.skuId" style="width: 100%">
            <el-option v-for="sku in priceForm.skus" :key="sku.id" :label="sku.title" :value="sku.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="新价格">
          <el-input-number v-model="priceForm.priceYuan" :min="0" :precision="2" :step="10" />
          <span class="muted"> 元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPrice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="costVisible" title="进价" width="420px">
      <p class="muted">只老板能改。每次保存都会留下改前、改后和操作人。</p>
      <el-form label-width="88px">
        <el-form-item label="规格">
          <el-select v-model="costForm.skuId" style="width: 100%">
            <el-option v-for="sku in costForm.skus" :key="sku.id" :label="sku.title" :value="sku.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="进价">
          <el-input-number v-model="costForm.costYuan" :min="0" :precision="2" :step="10" />
          <span class="muted"> 元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitCost">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 商品管理。列表、新增、编辑、上下架、改价都走 api/admin.ts。
 * USE_MOCK 为 true 时这些请求停在浏览器内存里；否则打到 Spring Boot。
 * 页面上的价格是元，交给接口前用 yuanToFen 换成整数分。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { changeProductPrice, createProduct, deleteProduct, listProducts, listStockAlerts, setSkuCost, shelveProduct, updateProduct } from '@/api/admin'
import type { AdminProduct, ProductStatus } from '@/api/types'
import { fenToYuanNumber, formatYuan, yuanToFen } from '@/domain/money'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()

const categories = [
  { id: 1, name: '家用多功能' },
  { id: 2, name: '工业平缝' },
  { id: 3, name: '包缝绷缝' },
  { id: 4, name: '特种机' },
]

interface SkuForm {
  id?: number
  title: string
  priceYuan: number
  stock: number
}

const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref<AdminProduct[]>([])
const loading = ref(false)
/** 有规格低于预警线的商品 id。数据来自库存预警接口的 alerting 标记。 */
const lowStockProductIds = ref<Set<number>>(new Set())
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  brand: '',
  categoryId: undefined as number | undefined,
  model: '',
  subtitle: '',
  coverImage: '',
  tags: '',
  status: 'ON' as ProductStatus,
  skus: [] as SkuForm[],
})
const priceVisible = ref(false)
const costVisible = ref(false)
const costForm = reactive({ skuId: 0, costYuan: 0, skus: [] as AdminProduct['skus'] })
const priceForm = reactive({
  productId: 0,
  skuId: 0,
  priceYuan: 0,
  skus: [] as AdminProduct['skus'],
})

function resetForm() {
  form.name = ''
  form.brand = ''
  form.categoryId = undefined
  form.model = ''
  form.subtitle = ''
  form.coverImage = ''
  form.tags = ''
  form.status = 'ON'
  form.skus = [{ title: '', priceYuan: 0, stock: 0 }]
}

async function load() {
  loading.value = true
  await run(async () => {
    const [data, alerts] = await Promise.all([
      listProducts({
        keyword: keyword.value || undefined,
        status: status.value || undefined,
        page: page.value,
        size: size.value,
      }),
      // 预警接口失败时不影响商品列表，只是这一次不显示低库存标记。
      listStockAlerts().catch(() => null),
    ])
    rows.value = data.list
    total.value = data.total
    lowStockProductIds.value = new Set(
      (alerts?.list ?? [])
        .filter((row) => Boolean(row.alerting))
        .map((row) => Number(row.productId)),
    )
  })
  loading.value = false
}

/** 任意一个规格低于自己的预警线，就认为这个商品要补货。 */
function isLowStock(row: AdminProduct): boolean {
  return lowStockProductIds.value.has(row.id)
}

function reload() {
  page.value = 1
  void load()
}

function onPage(next: number) {
  page.value = next
  void load()
}

function openCreate() {
  editingId.value = null
  resetForm()
  editorVisible.value = true
}

function openEdit(row: AdminProduct) {
  editingId.value = row.id
  form.name = row.name
  form.brand = row.brand || ''
  form.categoryId = row.categoryId
  form.model = row.model || ''
  form.subtitle = row.subtitle || ''
  form.coverImage = row.coverImage || ''
  form.tags = row.tags || ''
  form.status = row.status
  form.skus = row.skus.map((sku) => ({
    id: sku.id,
    title: sku.title,
    priceYuan: fenToYuanNumber(sku.price),
    stock: sku.stock,
  }))
  editorVisible.value = true
}

function categoryName(id?: number) {
  return categories.find((item) => item.id === id)?.name
}

async function submit() {
  const body = {
    name: form.name.trim(),
    brand: form.brand.trim(),
    categoryId: form.categoryId,
    category: categoryName(form.categoryId),
    model: form.model.trim(),
    subtitle: form.subtitle.trim(),
    coverImage: form.coverImage.trim(),
    tags: form.tags.trim(),
    status: form.status,
    skus: form.skus.map((sku) => ({
      id: sku.id,
      title: sku.title.trim(),
      price: yuanToFen(sku.priceYuan),
      stock: Math.trunc(sku.stock),
    })),
  }
  const ok = await run(async () => {
    if (editingId.value) await updateProduct(editingId.value, body)
    else await createProduct(body)
  })
  if (!ok) return
  editorVisible.value = false
  await load()
}

function openPrice(row: AdminProduct) {
  priceForm.productId = row.id
  priceForm.skus = row.skus
  priceForm.skuId = row.skus[0]?.id ?? 0
  priceForm.priceYuan = fenToYuanNumber(row.skus[0]?.price ?? row.price)
  priceVisible.value = true
}

function openCost(row: AdminProduct) {
  costForm.skus = row.skus
  costForm.skuId = row.skus[0]?.id ?? 0
  costForm.costYuan = fenToYuanNumber(row.skus[0]?.costPrice ?? 0)
  costVisible.value = true
}

async function submitCost() {
  const ok = await run(async () => {
    await setSkuCost(costForm.skuId, yuanToFen(costForm.costYuan))
  })
  if (!ok) return
  costVisible.value = false
  await load()
}

async function submitPrice() {
  const ok = await run(async () => {
    await changeProductPrice(priceForm.productId, {
      skuId: priceForm.skuId,
      price: yuanToFen(priceForm.priceYuan),
    })
  })
  if (!ok) return
  priceVisible.value = false
  await load()
}

async function toggle(row: AdminProduct) {
  const ok = await run(async () => {
    await shelveProduct(row.id, row.status !== 'ON')
  })
  if (ok) await load()
}

async function remove(row: AdminProduct) {
  try {
    await ElMessageBox.confirm(`删除「${row.name}」？已下过的订单仍保留当时的名称。`, '删除商品', { type: 'warning' })
  } catch {
    return
  }
  const ok = await run(async () => {
    await deleteProduct(row.id)
  })
  if (ok) await load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.muted {
  color: var(--zb-muted);
  font-size: 12px;
  word-break: break-all;
}
.sku {
  display: grid;
  grid-template-columns: 1fr 140px 120px auto;
  gap: 8px;
  margin-bottom: 8px;
  width: 100%;
}
.low-stock-tag {
  margin-left: 6px;
}
</style>
