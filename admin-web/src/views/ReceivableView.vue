<template>
  <div>
    <div class="page-head">
      <div>
        <h2>待收款</h2>
        <p>所有状态为 WAIT 的应付单，含订单与报修。点进详情后登记线下收款。</p>
      </div>
    </div>
    <div class="panel">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="单号" min-width="170">
          <template #default="{ row }">
            <router-link :to="row.bizType === 'ORDER' ? `/orders/${row.bizId}` : `/repairs/${row.bizId}`">{{ row.bizNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.bizType === 'ORDER' ? '订单' : '报修' }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" width="100" />
        <el-table-column prop="mobile" label="手机" width="130" />
        <el-table-column prop="title" label="应付" width="100" />
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">{{ paymentTypeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><span class="money">¥{{ formatYuan(row.amount) }}</span></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
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
import { onMounted, ref } from 'vue'
import { listReceivables } from '@/api/admin'
import type { Receivable } from '@/api/types'
import { paymentTypeText } from '@/domain/labels'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

const rows = ref<Receivable[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

async function load() {
  loading.value = true
  await run(async () => {
    const data = await listReceivables({ page: page.value, size: size.value })
    rows.value = data.list
    total.value = data.total
  })
  loading.value = false
}

function onPage(next: number) {
  page.value = next
  void load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
