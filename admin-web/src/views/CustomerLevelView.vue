<template>
  <div>
    <div class="page-head">
      <div>
        <h2>等级规则</h2>
        <p>按累计实收自动升级。手动指定的客户不受这里影响。等级不参与价格。</p>
      </div>
    </div>
    <div class="panel">
      <el-table :data="rules">
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column label="名称" width="160">
          <template #default="{ row }"><el-input v-model="row.name" /></template>
        </el-table-column>
        <el-table-column label="累计实收门槛（元）" width="200">
          <template #default="{ row }"><el-input v-model.number="row.yuan" /></template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }"><el-switch v-model="row.enabled" :disabled="row.code === 'NORMAL'" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button link type="primary" @click="save(row)">保存</el-button></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listLevelRules, updateLevelRule } from '@/api/admin'
import { fenToYuanNumber, yuanToFen } from '@/domain/money'
import { run } from '@/utils/feedback'

interface Row { id: number; code: string; name: string; yuan: number; enabled: boolean }

const rules = ref<Row[]>([])

async function reload() {
  const list = await listLevelRules()
  rules.value = list.map((rule) => ({ id: rule.id, code: rule.code, name: rule.name, yuan: fenToYuanNumber(rule.minPaid), enabled: rule.enabled }))
}

function save(row: Row) {
  void run(() => updateLevelRule(row.id, { name: row.name, minPaid: yuanToFen(row.yuan), enabled: row.enabled }).then(reload))
}

onMounted(() => void run(reload))
</script>
