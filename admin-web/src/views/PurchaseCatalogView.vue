<template>
  <div class="purchase-catalog">
    <div class="page-head">
      <h2>选购商品</h2>
      <el-button @click="router.push({ name: 'purchases' })">返回进货记录</el-button>
    </div>

    <div class="catalog-layout">
      <section class="catalog">
        <form class="panel catalog-filters" @submit.prevent="search">
          <el-input v-model="keyword" clearable placeholder="搜索商品名称或型号" aria-label="搜索商品" @clear="search" />
          <el-select v-model="categoryId" clearable placeholder="全部分类" aria-label="按分类筛选" @change="search">
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-select v-model="brand" clearable filterable placeholder="全部品牌" aria-label="按品牌筛选" @change="search">
            <el-option v-for="item in brands" :key="item" :label="item" :value="item" />
          </el-select>
          <el-button native-type="submit" type="primary">搜索</el-button>
        </form>
        <div v-loading="loading" class="catalog-results">
          <div v-if="products.length" class="product-grid">
            <article v-for="product in products" :key="product.id" class="product-card">
              <el-image v-if="product.coverImage" :src="imageUrl(product.coverImage)" :alt="product.name" fit="contain" loading="lazy" class="product-image">
                <template #error><div class="image-placeholder"><el-icon><Picture /></el-icon></div></template>
              </el-image>
              <div v-else class="product-image">
                <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
              </div>
              <div class="product-content">
                <h3>{{ product.name }}</h3>
                <p class="product-meta">{{ [product.brand, product.model].filter(Boolean).join(' · ') }}</p>
                <el-select v-model="selectedSku[product.id]" :aria-label="`选择 ${product.name} 的规格`" placeholder="规格">
                  <el-option v-for="sku in product.skus" :key="sku.id" :label="sku.title" :value="sku.id" />
                </el-select>
                <el-button type="primary" plain :disabled="!selectedSku[product.id] || !supplierReady" @click="add(product)">加入采购清单</el-button>
              </div>
            </article>
          </div>
          <el-empty v-else-if="!loading" description="暂无符合条件的商品" />
        </div>
        <div v-if="total > size" class="catalog-pager">
          <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </section>

      <aside class="panel cart">
        <header class="cart-header">
          <h3>采购清单</h3>
          <span v-if="cart.length" class="muted">{{ totalQuantity }} 件</span>
        </header>
        <el-empty v-if="!cart.length" :image-size="78" description="从商品列表添加采购商品" />
        <ul v-else class="cart-items">
          <li v-for="item in cart" :key="item.skuId" class="cart-item">
            <div class="cart-line">
              <div><strong>{{ item.product }}</strong><div class="muted">{{ item.sku }}</div></div>
              <el-button link type="danger" :aria-label="`移除 ${item.product} ${item.sku}`" @click="remove(item.skuId)">移除</el-button>
            </div>
            <div class="cart-controls">
              <el-select v-model="item.supplierId" filterable placeholder="选择供应商" :aria-label="`${item.product} 的供应商`">
                <el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name" :value="supplier.id" />
              </el-select>
              <el-input-number v-model="item.quantity" :min="1" :max="999999" :precision="0" size="small" :aria-label="`${item.product} 的采购数量`" />
            </div>
          </li>
        </ul>
        <footer class="cart-footer">
          <span v-if="supplierCount" class="muted">{{ supplierCount }} 家供应商</span>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">确认并联系供应商</el-button>
        </footer>
        <el-button v-if="supplierReady && !suppliers.length" link type="primary" @click="router.push({ name: 'suppliers' })">新增供应商</el-button>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { createPurchaseBatch, listProducts, productOptions, purchaseSuppliers } from '@/api/admin'
import type { PurchaseSelection, PurchaseSupplier } from '@/api/purchase'
import type { AdminProduct, ProductCategory } from '@/api/types'
import { API_BASE } from '@/config'
import { matchPurchaseSupplier } from '@/domain/purchase'
import { run } from '@/utils/feedback'

interface CartItem {
  skuId: number
  product: string
  sku: string
  quantity?: number
  supplierId?: number
}

const router = useRouter()
const keyword = ref('')
const appliedKeyword = ref('')
const brand = ref<string>()
const categoryId = ref<number>()
const page = ref(1)
const size = 12
const total = ref(0)
const products = ref<AdminProduct[]>([])
const categories = ref<ProductCategory[]>([])
const brands = ref<string[]>([])
const suppliers = ref<PurchaseSupplier[]>([])
const supplierReady = ref(false)
const loading = ref(false)
const submitting = ref(false)
const selectedSku = reactive<Record<number, number>>({})
const cart = ref<CartItem[]>([])
let loadId = 0
let lastRequest: { lines: string; id: string } | undefined

const totalQuantity = computed(() => cart.value.reduce((sum, item) => sum + (item.quantity ?? 0), 0))
const supplierCount = computed(() => new Set(cart.value.map((item) => item.supplierId).filter(Boolean)).size)
const canSubmit = computed(() => cart.value.length > 0 && cart.value.every((item) => item.supplierId && item.quantity))

function imageUrl(url: string) {
  return url.startsWith('/') && !url.startsWith('//') ? API_BASE + url : url
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  void load()
}

async function load() {
  const id = ++loadId
  loading.value = true
  await run(async () => {
    const data = await listProducts({ keyword: appliedKeyword.value, categoryId: categoryId.value, brand: brand.value, page: page.value, size })
    if (id !== loadId) return
    products.value = data.list
    total.value = data.total
    for (const product of data.list) {
      if (!product.skus.some((sku) => sku.id === selectedSku[product.id]) && product.skus[0]) {
        selectedSku[product.id] = product.skus[0].id
      }
    }
  })
  if (id === loadId) loading.value = false
}

function add(product: AdminProduct) {
  const sku = product.skus.find((item) => item.id === selectedSku[product.id])
  if (!sku) return
  const item = cart.value.find((row) => row.skuId === sku.id)
  if (item) {
    item.quantity = Math.min(999999, (item.quantity ?? 0) + 1)
    return
  }
  cart.value.push({
    skuId: sku.id, product: product.name, sku: sku.title, quantity: 1,
    supplierId: matchPurchaseSupplier(product, suppliers.value),
  })
}

function remove(skuId: number) {
  cart.value = cart.value.filter((item) => item.skuId !== skuId)
}

async function submit() {
  if (submitting.value || !canSubmit.value) return
  const lines: PurchaseSelection[] = cart.value.map((item) => ({
    skuId: item.skuId, quantity: item.quantity ?? 0, supplierId: item.supplierId,
  }))
  const fingerprint = JSON.stringify(lines)
  if (lastRequest?.lines !== fingerprint) {
    const id = Array.from(crypto.getRandomValues(new Uint8Array(16)), (value) => value.toString(16).padStart(2, '0')).join('')
    lastRequest = { lines: fingerprint, id }
  }
  const requestId = lastRequest.id
  submitting.value = true
  await run(async () => {
    const batch = await createPurchaseBatch({ requestId, lines })
    await router.push({ name: 'purchase-contacts', params: { id: batch.summary.id } })
  })
  submitting.value = false
}

onMounted(() => {
  void load()
  void run(async () => {
    suppliers.value = await purchaseSuppliers()
    supplierReady.value = true
  })
  void run(async () => {
    const options = await productOptions()
    categories.value = options.categories
    brands.value = options.brands
  })
})
</script>

<style scoped>
.catalog-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(275px, 335px); gap: 20px; align-items: start; }
.catalog { min-width: 0; }
.catalog-filters { display: grid; grid-template-columns: minmax(135px, 2fr) minmax(88px, 1fr) minmax(88px, 1fr) auto; gap: 9px; padding: 12px; margin-bottom: 16px; }
.catalog-results { min-height: 150px; }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(185px, 100%), 1fr)); gap: 15px; }
.product-card { background: #fff; border: 1px solid var(--el-border-color-light); border-radius: 12px; overflow: hidden; }
.product-image { display: block; width: 100%; height: 151px; background: var(--el-fill-color-light); }
.image-placeholder { display: grid; height: 100%; place-items: center; color: var(--el-text-color-placeholder); font-size: 36px; }
.product-content { display: grid; gap: 9px; padding: 12px; }
.product-content h3, .cart-header h3 { margin: 0; font-size: 16px; }
.product-meta { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; min-height: 16px; }
.catalog-pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.cart { position: sticky; top: 12px; padding: 16px; }
.cart-header, .cart-line, .cart-footer { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.cart-items { list-style: none; padding: 0; margin: 8px 0 14px; max-height: 54vh; overflow: auto; }
.cart-item { border-bottom: 1px solid var(--el-border-color-light); padding: 10px 0; }
.cart-controls { display: grid; grid-template-columns: minmax(0, 1fr) 94px; align-items: center; gap: 8px; margin-top: 8px; }
.cart-controls :deep(.el-input-number) { width: 94px; }
.cart-footer { flex-wrap: wrap; justify-content: flex-end; }
@media (max-width: 1000px) { .catalog-layout { grid-template-columns: minmax(0, 1fr); } .cart { position: static; } }
@media (max-width: 560px) { .catalog-filters { grid-template-columns: 1fr 1fr; } }
</style>
