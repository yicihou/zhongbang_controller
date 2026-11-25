<template>
  <div>
    <div class="page-head">
      <div>
        <h2>数据导入</h2>
        <p>先下载模板或上传自己的表，把列名对上系统字段，看过每一行的错误后再确认。出错的行会跳过。</p>
      </div>
      <el-button @click="download">下载模板</el-button>
    </div>
    <div class="panel">
      <div class="filters">
        <el-select v-model="kind" style="width: 160px">
          <el-option label="商品和进价" value="PRODUCT" />
          <el-option label="供应商" value="SUPPLIER" />
          <el-option label="采购记录" value="PURCHASE" />
          <el-option label="历史销售" value="SALE" />
        </el-select>
        <input type="file" accept=".csv,.xlsx" @change="onFile" />
        <el-button type="primary" :disabled="!token" @click="commit">确认导入</el-button>
      </div>
      <div v-if="fields.length" class="map">
        <label v-for="field in fields" :key="String(field.key)">
          {{ field.label }}{{ field.required ? ' *' : '' }}
          <el-select v-model="mapping[String(field.key)]" clearable placeholder="不导入" style="width: 180px">
            <el-option v-for="header in headers" :key="header" :label="header" :value="header" />
          </el-select>
        </label>
      </div>
      <el-table v-if="rows.length" :data="rows" size="small">
        <el-table-column prop="rowNo" label="行" width="70" />
        <el-table-column label="结果" min-width="220">
          <template #default="{ row }">
            <span :class="{ bad: row.error }">{{ row.error || '可以导入' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="result">成功 {{ result.created }} 行，跳过 {{ result.skipped }} 行。{{ result.errors.join('；') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { commitImport, downloadImportTemplate, previewImport } from '@/api/admin'
import { run } from '@/utils/feedback'

const kind = ref('PRODUCT')
const token = ref('')
const headers = ref<string[]>([])
const fields = ref<Record<string, unknown>[]>([])
const rows = ref<Record<string, unknown>[]>([])
const mapping = reactive<Record<string, string>>({})
const result = ref<{ created: number; skipped: number; errors: string[] } | null>(null)
let file: File | null = null

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  file = input.files?.[0] ?? null
  if (!file) return
  await run(async () => {
    const preview = await previewImport(kind.value, file as File)
    token.value = String(preview.token ?? '')
    headers.value = (preview.headers as string[]) ?? []
    fields.value = (preview.fields as Record<string, unknown>[]) ?? []
    rows.value = (preview.rows as Record<string, unknown>[]) ?? []
    const suggested = (preview.suggestedMapping as Record<string, string>) ?? {}
    for (const key of Object.keys(mapping)) delete mapping[key]
    Object.assign(mapping, suggested)
    result.value = null
  })
}

async function commit() {
  await run(async () => {
    result.value = await commitImport(token.value, { ...mapping })
  })
}

async function download() {
  await run(async () => {
    await downloadImportTemplate(kind.value)
  })
}
</script>

<style scoped>
.map {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 12px 0;
}
.bad {
  color: var(--zb-red);
}
</style>
