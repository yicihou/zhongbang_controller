<template>
  <div>
    <div class="page-head">
      <div>
        <h2>还款提醒</h2>
        <p>即将到期、今天到期、已经逾期。每天早上 9 点也会推给老板，其他员工收不到。</p>
      </div>
      <el-button type="primary" @click="scan">立即检查</el-button>
    </div>
    <el-tabs v-model="bucket" @tab-change="reload">
      <el-tab-pane label="即将到期" name="SOON" />
      <el-tab-pane label="今天到期" name="TODAY" />
      <el-tab-pane label="已逾期" name="OVERDUE" />
    </el-tabs>
    <div class="panel">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="客户">
          <template #default="{ row }"><router-link :to="`/customers/${row.customerId}`">{{ row.customerName }}</router-link></template>
        </el-table-column>
        <el-table-column prop="dueDate" label="约定日" width="140" />
        <el-table-column label="约定金额" width="140"><template #default="{ row }">{{ formatYuan(row.agreedAmount) }}</template></el-table-column>
        <el-table-column prop="note" label="说明" />
        <el-table-column label="材料" min-width="220">
          <template #default="{ row }">
            <div v-for="(file, index) in row.materials || []" :key="index">
              <audio v-if="file.kind === 'AUDIO' && file.url" :src="file.url" controls />
              <a v-else-if="file.url" :href="file.url" target="_blank">{{ file.name || '下载' }}</a>
              <span v-else>{{ file.text }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listRepayments, scanRepayments } from '@/api/admin'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

const bucket = ref('OVERDUE')
interface ReminderRow {
  customerId: number
  customerName: string
  dueDate: string
  agreedAmount: number
  note: string
  materials?: { kind: string; url?: string; name?: string; text?: string }[]
}
const rows = ref<ReminderRow[]>([])
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    const page = await listRepayments(bucket.value)
    rows.value = page.list as unknown as ReminderRow[]
  } finally {
    loading.value = false
  }
}

function scan() {
  void run(async () => {
    const count = await scanRepayments()
    window.alert(count ? `新提醒 ${count} 条` : '今天已经检查过，没有新的提醒')
    await reload()
  })
}

onMounted(() => void reload())
</script>
