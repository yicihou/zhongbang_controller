<template>
  <div>
    <div class="page-head">
      <h2>采购入库</h2>
      <el-button type="primary" @click="router.push({ name: 'purchase-catalog' })">新建采购</el-button>
    </div>
    <div class="panel">
      <form class="filters" @submit.prevent="search">
        <el-input v-model="keyword" clearable placeholder="搜索主单号" aria-label="主单号" style="width: 260px" @clear="search" />
        <el-button native-type="submit">查询</el-button>
      </form>
      <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无进货记录">
        <el-table-column label="主单号" min-width="220">
          <template #default="{ row }">
            <router-link :to="{ name: 'purchase-detail', params: { id: row.id } }">{{ row.purchaseNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">{{ row.suppliers.join('、') || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orderDate" label="日期" width="120" />
        <el-table-column label="状态" width="108">
          <template #default="{ row }">
            <el-tag :type="purchaseStatus[row.status as PurchaseStatus].type" effect="light">
              {{ purchaseStatus[row.status as PurchaseStatus].text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="session.isBoss" label="已录入金额" width="130">
          <template #default="{ row }">¥{{ formatYuan(row.totalAmount ?? 0) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="155">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'purchase-detail', params: { id: row.id } })">查看</el-button>
            <el-button v-if="row.status !== 'VOID'" link type="primary"
              @click="router.push({ name: 'purchase-contacts', params: { id: row.id } })">联系供应商</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listPurchaseBatches } from '@/api/admin'
import type { PurchaseBatchRow, PurchaseStatus } from '@/api/purchase'
import { formatYuan } from '@/domain/money'
import { purchaseStatus } from '@/domain/purchase'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()
const router = useRouter()
const rows = ref<PurchaseBatchRow[]>([])
const loading = ref(false)
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const size = 20
let loadId = 0

function search() {
  if (appliedKeyword.value === keyword.value.trim() && page.value === 1) {
    void reload()
    return
  }
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
}

async function reload() {
  const id = ++loadId
  loading.value = true
  await run(async () => {
    const data = await listPurchaseBatches({ keyword: appliedKeyword.value, page: page.value, size })
    if (id !== loadId) return
    rows.value = data.list
    total.value = data.total
  })
  if (id === loadId) loading.value = false
}

watch([page, appliedKeyword], reload, { immediate: true })
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
