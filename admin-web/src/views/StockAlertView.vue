<template>
  <div>
    <div class="page-head">
      <div>
        <h2>库存预警</h2>
        <p>低于最低库存时通知老板，同一种规格在补货回来之前不会重复提醒。建议采购量 = 最低库存 × {{ multiplier }} − 现有库存 − 在途。</p>
      </div>
    </div>
    <div class="panel">
      <el-table :data="rows">
        <el-table-column prop="productName" label="商品" min-width="160" />
        <el-table-column prop="skuName" label="规格" min-width="160" />
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="minStock" label="最低" width="80" />
        <el-table-column prop="inTransit" label="在途" width="80" />
        <el-table-column prop="suggestQty" label="建议采购" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.alerting ? 'danger' : 'info'">{{ row.alerting ? '低于下限' : '关注' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listStockAlerts } from '@/api/admin'
import { run } from '@/utils/feedback'

const rows = ref<Record<string, unknown>[]>([])
const multiplier = ref(2)

onMounted(() => {
  void run(async () => {
    const page = await listStockAlerts()
    rows.value = page.list
    multiplier.value = page.suggestMultiplier
  })
})
</script>
