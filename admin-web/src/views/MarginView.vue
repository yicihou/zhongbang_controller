<template>
  <div>
    <div class="page-head">
      <div>
        <h2>销售毛利</h2>
        <p>销售额、实收、待收、成本和毛利由系统计算。维修收入和安装费分开列，不算净利润。未知成本不会当成 0。</p>
      </div>
      <div>
        <el-button @click="download">导出 Excel</el-button>
        <el-button type="primary" @click="ask">让 DeepSeek 解读</el-button>
      </div>
    </div>
    <div class="panel filters">
      <el-radio-group v-model="period" @change="reload">
        <el-radio-button value="MONTH">月报</el-radio-button>
        <el-radio-button value="YEAR">年报</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="scope" @change="reload">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="INVOICED">开票</el-radio-button>
        <el-radio-button value="UNINVOICED">不开票</el-radio-button>
      </el-radio-group>
    </div>
    <div class="metrics" v-if="current">
      <div><span>销售额</span><b>¥{{ yuan(current.salesAmount) }}</b></div>
      <div><span>含税</span><b>¥{{ yuan(current.salesTaxIncluded) }}</b></div>
      <div><span>不含税</span><b>¥{{ yuan(current.salesTaxExcluded) }}</b></div>
      <div><span>实收</span><b>¥{{ yuan(current.receivedAmount) }}</b></div>
      <div><span>待收</span><b>¥{{ yuan(current.unpaidAmount) }}</b></div>
      <div><span>商品成本</span><b>{{ current.goodsCost == null ? '未知成本' : '¥' + yuan(current.goodsCost) }}</b></div>
      <div><span>毛利</span><b>{{ current.grossProfit == null ? '未知成本' : '¥' + yuan(current.grossProfit) }}</b></div>
      <div><span>毛利率</span><b>{{ current.grossMargin ? current.grossMargin + '%' : '—' }}</b></div>
      <div><span>安装费</span><b>¥{{ yuan(current.installIncome) }}</b></div>
      <div><span>维修收入</span><b>¥{{ yuan(current.repairIncome) }}</b></div>
    </div>
    <p v-if="compareText" class="panel">{{ compareText }}</p>
    <div class="panel">
      <h3>机型 / 规格</h3>
      <el-table :data="products">
        <el-table-column prop="productName" label="商品" min-width="140" />
        <el-table-column prop="skuName" label="规格" min-width="140" />
        <el-table-column prop="quantity" label="销量" width="70" />
        <el-table-column label="销售额" width="110">
          <template #default="{ row }">¥{{ yuan(Number(row.salesAmount)) }}</template>
        </el-table-column>
        <el-table-column label="成本" width="110">
          <template #default="{ row }">{{ row.costAmount == null ? '未知' : '¥' + yuan(Number(row.costAmount)) }}</template>
        </el-table-column>
        <el-table-column label="毛利率" width="90">
          <template #default="{ row }">{{ row.grossMargin ? row.grossMargin + '%' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="70" />
        <el-table-column prop="turnoverDays" label="周转天数" width="90" />
        <el-table-column label="标记" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.hot" type="success">畅销</el-tag>
            <el-tag v-else-if="row.slow" type="warning">滞销</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div v-if="insight" class="panel">
      <h3>解读</h3>
      <p>{{ insight }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { askMarginInsight, downloadMargin, marginProducts, marginReport } from '@/api/admin'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

const period = ref('MONTH')
const scope = ref('ALL')
const current = ref<Record<string, number | string | null | undefined> | null>(null)
const previous = ref<Record<string, number | string | null | undefined> | null>(null)
const products = ref<Record<string, unknown>[]>([])
const insight = ref('')

const compareText = computed(() => {
  if (!current.value || !previous.value) return ''
  const now = Number(current.value.salesAmount ?? 0)
  const prev = Number(previous.value.salesAmount ?? 0)
  return `对比 ${previous.value.periodKey}：销售额相差 ¥${formatYuan(now - prev)}。`
})

function yuan(value: unknown) {
  return formatYuan(Number(value ?? 0))
}

async function reload() {
  await run(async () => {
    const query = { period: period.value, scope: scope.value }
    const report = await marginReport(query)
    current.value = report.current as typeof current.value
    previous.value = report.previous as typeof previous.value
    products.value = (await marginProducts(query)).list
  })
}

async function ask() {
  await run(async () => {
    const result = await askMarginInsight({ period: period.value, scope: scope.value })
    insight.value = result.analysis?.resultText || result.message
  })
}

async function download() {
  await run(async () => {
    await downloadMargin({ period: period.value, scope: scope.value })
  })
}

onMounted(reload)
</script>

<style scoped>
.metrics {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.metrics div {
  background: #fff;
  border: 1px solid var(--zb-line);
  border-radius: 8px;
  padding: 12px;
}
.metrics span {
  display: block;
  color: var(--zb-muted);
  font-size: 12px;
}
</style>
