<template>
  <div>
    <div class="page-head">
      <div>
        <h2>报修单</h2>
        <p>一张报修单可含多行，支持本店设备与外购设备。</p>
      </div>
    </div>
    <div class="panel">
      <div class="filters">
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="item in REPAIR_STATUS_OPTIONS" :key="item" :value="item">
            {{ REPAIR_STATUS_LABEL[item] }}
          </el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" clearable placeholder="单号 / 客户 / 机型" style="width: 220px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" data-testid="repair-table">
        <el-table-column label="报修单号" min-width="170">
          <template #default="{ row }">
            <router-link :to="`/repairs/${row.id}`" :data-testid="`repair-link-${row.id}`">{{ row.ticketNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="100" />
        <el-table-column prop="contactMobile" label="电话" width="130" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="repairTagType(row.status)" effect="light">{{ repairStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="70">
          <template #default="{ row }">{{ row.lines.length }}</template>
        </el-table-column>
        <el-table-column label="设备" min-width="220">
          <template #default="{ row }">{{ lineText(row) }}</template>
        </el-table-column>
        <el-table-column label="报价" width="110">
          <template #default="{ row }">{{ row.quoteAmount == null ? '—' : `¥${formatYuan(row.quoteAmount)}` }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="188" />
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
 * 报修列表。数据来自 GET /api/admin/repairs。Mock 开关打开时走浏览器里的假数据。
 * 新报修通知让 inbox.revision 增加时会重新查询。没有「模拟新报修」按钮。
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listRepairs } from '@/api/admin'
import type { RepairTicket } from '@/api/types'
import { formatYuan } from '@/domain/money'
import { REPAIR_STATUS_LABEL, REPAIR_STATUS_OPTIONS, repairStatusText, repairTagType } from '@/domain/labels'
import { useInboxStore } from '@/stores/inbox'
import { run } from '@/utils/feedback'

const route = useRoute()
const inbox = useInboxStore()
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref<RepairTicket[]>([])
const loading = ref(false)

function lineText(ticket: RepairTicket): string {
  return ticket.lines
    .map((line) => `${line.brand} ${line.model}${line.source === 'EXTERNAL' ? '（外购）' : ''}`)
    .join('、')
}

async function load() {
  loading.value = true
  await run(async () => {
    const data = await listRepairs({
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
  () => void load(),
)

onMounted(() => {
  inbox.clearRepairFlash()
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
