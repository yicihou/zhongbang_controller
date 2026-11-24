<template>
  <div>
    <div class="page-head">
      <div>
        <h2>订单</h2>
        <p>按状态筛选。新订单经 SSE 推送，断线后改为 30 秒轮询未读通知。</p>
      </div>
      <el-button v-if="mockMode" type="primary" data-testid="mock-new-order" @click="mockNewOrder">模拟新订单</el-button>
    </div>
    <div class="panel">
      <div class="filters">
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="item in ORDER_STATUS_OPTIONS" :key="item" :value="item">
            {{ ORDER_STATUS_LABEL[item] }}
          </el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" clearable placeholder="订单号 / 客户 / 手机" style="width: 240px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        data-testid="order-table"
        row-key="id"
        :row-class-name="rowClass"
      >
        <el-table-column label="订单号" min-width="170">
          <template #default="{ row }">
            <router-link :to="`/orders/${row.id}`" :data-testid="`order-link-${row.id}`">{{ row.orderNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="100" />
        <el-table-column prop="customerMobile" label="手机" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="orderTagType(row.status)" effect="light">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="180">
          <template #default="{ row }">{{ goodsText(row) }}</template>
        </el-table-column>
        <el-table-column label="发货方式" min-width="160">
          <template #default="{ row }">{{ methodsText(row) }}</template>
        </el-table-column>
        <el-table-column label="应付" width="120">
          <template #default="{ row }"><span class="money">¥{{ formatYuan(row.payableAmount) }}</span></template>
        </el-table-column>
        <el-table-column label="已付" width="120">
          <template #default="{ row }">¥{{ formatYuan(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" min-width="188" />
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
  </div>
</template>

<script setup lang="ts">
/**
 * 订单列表。打开页面或改筛选时调用 GET /api/admin/orders。
 * 默认走真实后端。inbox.revision 变化（来了新通知）会重新拉列表。
 * 「模拟新订单」只在 Mock 下出现：写入内存库并广播一条通知，不请求 Java。
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listOrders } from '@/api/admin'
import type { AdminOrder } from '@/api/types'
import { USE_MOCK } from '@/config'
import { formatYuan } from '@/domain/money'
import { DELIVERY_LABEL, ORDER_STATUS_LABEL, ORDER_STATUS_OPTIONS, orderStatusText, orderTagType } from '@/domain/labels'
import { publishMockStream } from '@/mock/bus'
import { simulateIncomingOrder } from '@/mock/sim'
import { useInboxStore } from '@/stores/inbox'
import { run } from '@/utils/feedback'

const route = useRoute()
const inbox = useInboxStore()
const mockMode = USE_MOCK
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref<AdminOrder[]>([])
const loading = ref(false)

function goodsText(order: AdminOrder): string {
  const first = order.items[0]
  if (!first) return '—'
  const extra = order.items.length > 1 ? ` 等 ${order.items.length} 件` : ''
  return `${first.productName}${extra}`
}

function methodsText(order: AdminOrder): string {
  const labels = [...new Set(order.items.map((item) => DELIVERY_LABEL[item.deliveryMethod]))]
  return labels.join(' / ')
}

function rowClass({ row }: { row: AdminOrder }): string {
  return inbox.freshOrderIds.includes(row.id) ? 'row-fresh' : ''
}

async function load() {
  loading.value = true
  await run(async () => {
    const data = await listOrders({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = data.list
    total.value = data.total
  })
  loading.value = false
}

function reload() {
  page.value = 1
  void load()
}

function onPage(next: number) {
  page.value = next
  void load()
}

async function mockNewOrder() {
  const notice = simulateIncomingOrder()
  const delivered = publishMockStream(notice)
  if (!delivered) inbox.ingest(notice, true)
  await load()
}

watch(
  () => route.query.status,
  (value) => {
    status.value = typeof value === 'string' ? value : ''
    page.value = 1
    void load()
  },
)

watch(
  () => inbox.revision,
  () => {
    void load()
  },
)

onMounted(() => {
  inbox.clearOrderFlash()
  void load()
})
</script>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  align-items: center;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
