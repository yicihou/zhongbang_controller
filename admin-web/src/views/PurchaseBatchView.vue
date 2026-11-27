<template>
  <div v-loading="loading" class="batch-page">
    <div class="page-head">
      <div>
        <h2>{{ contacts ? '联系供应商' : '进货记录' }}</h2>
        <p v-if="batch" class="batch-ref muted">{{ batch.summary.purchaseNo }} · {{ batch.summary.orderDate }}</p>
      </div>
      <div class="batch-actions">
        <el-button @click="router.push({ name: 'purchases' })">进货列表</el-button>
        <el-button v-if="batch" type="primary" plain @click="showRelatedPage">
          {{ contacts ? '查看进货记录' : '联系供应商' }}
        </el-button>
      </div>
    </div>

    <el-empty v-if="!batch && !loading" description="进货记录无法加载" />
    <template v-else-if="batch">
      <div v-if="contacts" class="panel">
        <el-tabs v-if="activeContacts.length" v-model="activeContact">
          <el-tab-pane v-for="entry in activeContacts" :key="entry.purchase.id" :name="entry.purchase.id" :label="entry.purchase.supplierName" lazy>
            <div class="supplier-info">
              <div><span class="muted">联系人</span><strong>{{ entry.contact || '未登记' }}</strong></div>
              <div>
                <span class="muted">电话</span>
                <a v-if="entry.mobile" :href="`tel:${entry.mobile}`">{{ entry.mobile }}</a>
                <strong v-else>未登记</strong>
              </div>
              <div class="batch-actions">
                <el-button v-if="entry.mobile" @click="copy(entry.mobile, '电话号码已复制')">复制号码</el-button>
                <el-button type="primary" @click="copyList(entry)">复制采购清单</el-button>
              </div>
            </div>
            <el-table :data="entry.purchase.lines">
              <el-table-column prop="productName" label="商品" min-width="150" />
              <el-table-column prop="skuName" label="规格" min-width="120" />
              <el-table-column prop="quantity" label="数量" width="76" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else description="暂无可联系的供应商" />
      </div>

      <template v-else>
        <div class="panel batch-overview">
          <el-tag :type="purchaseStatus[batch.summary.status].type" effect="light">{{ purchaseStatus[batch.summary.status].text }}</el-tag>
          <span>{{ batch.summary.suppliers.length }} 家供应商</span>
          <strong v-if="session.isBoss">已录入金额 ¥{{ formatYuan(batch.summary.totalAmount ?? 0) }}</strong>
        </div>
        <section v-for="entry in batch.orders" :key="entry.purchase.id" class="panel supplier-order">
          <header class="supplier-order-head">
            <div>
              <h3>{{ entry.purchase.supplierName || '供应商' }}</h3>
              <p class="muted">{{ [entry.purchase.purchaseNo, entry.purchase.orderDate, entry.purchase.operatorName].filter(Boolean).join(' · ') }}</p>
            </div>
            <div class="batch-actions">
              <el-tag :type="purchaseStatus[entry.purchase.status].type" effect="light">{{ purchaseStatus[entry.purchase.status].text }}</el-tag>
              <el-button v-if="entry.purchase.status !== 'VOID'" link type="primary" @click="goContacts(entry.purchase.id)">联系供应商</el-button>
              <el-button v-if="entry.purchase.status === 'DRAFT'" @click="editing = entry">编辑</el-button>
              <el-button v-if="entry.purchase.status === 'DRAFT'" type="primary" :disabled="!!pending" :loading="pending === entry.purchase.id"
                @click="update(entry, 'receive')">确认入库</el-button>
              <el-button v-if="entry.purchase.status === 'RECEIVED' && session.isBoss" type="primary" plain @click="editing = entry">更正</el-button>
              <el-button v-if="entry.purchase.status === 'DRAFT' || (entry.purchase.status === 'RECEIVED' && session.isBoss)"
                link type="danger" :disabled="!!pending" @click="update(entry, 'void')">作废</el-button>
            </div>
          </header>
          <el-table :data="entry.purchase.lines">
            <el-table-column prop="productName" label="商品" min-width="150" />
            <el-table-column prop="skuName" label="规格" min-width="110" />
            <el-table-column prop="quantity" label="数量" width="76" />
            <el-table-column v-if="session.isBoss" label="进价" width="125">
              <template #default="{ row }">{{ amount(row.unitCost) }}</template>
            </el-table-column>
          </el-table>
          <div class="purchase-terms">
            <span>{{ invoiceText(entry.purchase) }}</span>
            <span v-if="session.isBoss">运费 {{ amount(entry.purchase.freightAmount) }}</span>
            <strong v-if="session.isBoss">
              {{ entry.purchase.lines.some((line) => line.unitCost == null) ? '已录入金额' : '合计' }} {{ amount(entry.purchase.totalAmount) }}
            </strong>
          </div>
          <p v-if="entry.purchase.remark" class="purchase-note">{{ entry.purchase.remark }}</p>
          <div v-for="(url, index) in entry.purchase.attachmentUrls ?? []" :key="url">
            <a v-if="attachmentUrl(url)" :href="attachmentUrl(url)" target="_blank" rel="noopener noreferrer">进货附件 {{ index + 1 }}</a>
          </div>

          <div v-if="entry.corrections.length" class="corrections">
            <h4>更正记录</h4>
            <details v-for="correction in entry.corrections" :key="correction.id" class="correction">
              <summary>{{ correction.createdAt }} · {{ correction.operatorName }} · {{ correction.reason }}</summary>
              <div class="correction-versions">
                <div v-for="version in [{ name: '更正前', data: correction.before }, { name: '更正后', data: correction.after }]" :key="version.name">
                  <strong>{{ version.name }}</strong>
                  <ul>
                    <li v-for="line in version.data.lines" :key="line.id">
                      {{ line.productName }} / {{ line.skuName }} × {{ line.quantity }} · 进价 {{ amount(line.unitCost) }}
                    </li>
                  </ul>
                  <p>{{ version.data.orderDate }} · {{ invoiceText(version.data) }}</p>
                  <p>运费 {{ amount(version.data.freightAmount) }} · 合计 {{ amount(version.data.totalAmount) }}</p>
                  <p v-if="version.data.remark">{{ version.data.remark }}</p>
                </div>
              </div>
            </details>
          </div>
        </section>
      </template>
    </template>
    <PurchaseEditor v-model="editing" @saved="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getPurchaseBatch, receivePurchase, voidPurchase } from '@/api/admin'
import type { PurchaseBatch, PurchaseDetail, SupplierOrder } from '@/api/purchase'
import PurchaseEditor from '@/components/PurchaseEditor.vue'
import { API_BASE } from '@/config'
import { formatYuan } from '@/domain/money'
import { purchaseStatus } from '@/domain/purchase'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const batch = ref<PurchaseBatch>()
const loading = ref(false)
const pending = ref<number>()
const editing = ref<SupplierOrder | null>(null)
const activeContact = ref<number>()
const contacts = computed(() => route.name === 'purchase-contacts')
const activeContacts = computed(() => batch.value?.orders.filter((entry) => entry.purchase.status !== 'VOID') ?? [])
let loadId = 0

async function load() {
  const id = ++loadId
  loading.value = true
  await run(async () => {
    const result = await getPurchaseBatch(Number(route.params.id))
    if (id === loadId) batch.value = result
  })
  if (id === loadId) loading.value = false
}

watch(() => route.params.id, () => {
  batch.value = undefined
  void load()
}, { immediate: true })
watch([activeContacts, () => route.query.order, contacts], ([entries, requested, contactPage]) => {
  if (!contactPage) return
  activeContact.value = entries.find((entry) => entry.purchase.id === Number(requested))?.purchase.id ?? entries[0]?.purchase.id
}, { immediate: true })

function showRelatedPage() {
  if (contacts.value) {
    void router.push({ name: 'purchase-detail', params: { id: route.params.id } })
  } else {
    goContacts()
  }
}

function goContacts(id?: number) {
  void router.push({ name: 'purchase-contacts', params: { id: route.params.id }, query: id ? { order: id } : {} })
}

function amount(value?: number) {
  return value == null ? '—' : `¥${formatYuan(value)}`
}

function invoiceText(purchase: PurchaseDetail) {
  if (!purchase.invoiced) return '不开票'
  return [
    purchase.invoiceType || '开票',
    purchase.taxRate == null ? '' : `${purchase.taxRate / 100}%`,
    purchase.taxIncluded == null ? '' : purchase.taxIncluded ? '含税' : '未含税',
  ].filter(Boolean).join(' · ')
}

function attachmentUrl(url: string) {
  if (url.startsWith('/') && !url.startsWith('//')) return API_BASE + url
  return /^https?:\/\//i.test(url) ? url : ''
}

async function copy(text: string, success: string) {
  await run(async () => {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
    } else {
      const area = document.createElement('textarea')
      area.value = text
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.append(area)
      area.select()
      try {
        if (!document.execCommand('copy')) throw new Error('复制失败，请手动复制')
      } finally {
        area.remove()
      }
    }
  }, success)
}

function copyList(entry: SupplierOrder) {
  const list = entry.purchase.lines.map((line) => `${line.productName} / ${line.skuName} × ${line.quantity}`)
  const text = [entry.purchase.supplierName, `采购单 ${entry.purchase.purchaseNo}`, ...list].join('\n')
  void copy(text, '采购清单已复制')
}

async function update(entry: SupplierOrder, action: 'receive' | 'void') {
  if (pending.value) return
  const id = entry.purchase.id
  const receive = action === 'receive'
  pending.value = id
  try {
    await ElMessageBox.confirm(receive ? `确认将「${entry.purchase.supplierName}」的商品入库？` : '确定作废这张采购子单？',
      receive ? '确认入库' : '作废采购子单', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch {
    pending.value = undefined
    return
  }
  await run(async () => {
    if (receive) await receivePurchase(id, entry.revision)
    else await voidPurchase(id, entry.revision)
    await load()
  }, receive ? '已入库' : '采购子单已作废')
  pending.value = undefined
}
</script>

<style scoped>
.batch-page { min-height: 240px; }
.batch-ref, .supplier-order-head p { margin: 5px 0 0; }
.batch-overview, .batch-actions, .supplier-order-head, .purchase-terms { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; }
.batch-actions { gap: 8px; }
.batch-actions :deep(.el-button + .el-button) { margin-left: 0; }
.batch-overview { margin-bottom: 16px; }
.batch-overview strong { margin-left: auto; }
.supplier-order { margin-bottom: 16px; }
.supplier-order-head { justify-content: space-between; margin-bottom: 12px; }
.supplier-order-head h3 { margin: 0; }
.purchase-terms { justify-content: flex-end; margin-top: 12px; }
.purchase-note { white-space: pre-wrap; }
.supplier-info { display: flex; flex-wrap: wrap; gap: 14px 30px; align-items: center; padding: 10px 0 18px; }
.supplier-info > div:not(.batch-actions) { display: grid; gap: 4px; }
.supplier-info .batch-actions { margin-left: auto; }
.supplier-info a { font-size: 16px; font-weight: 600; }
.corrections { border-top: 1px solid var(--el-border-color-light); margin-top: 14px; }
.corrections h4 { margin: 12px 0 8px; }
.correction { margin-bottom: 8px; padding: 8px 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.correction summary { cursor: pointer; overflow-wrap: anywhere; }
.correction-versions { display: grid; grid-template-columns: repeat(auto-fit, minmax(215px, 1fr)); gap: 8px 18px; padding-top: 12px; }
.correction-versions ul { padding-left: 18px; }
.correction-versions p { margin: 4px 0; white-space: pre-wrap; }
</style>
