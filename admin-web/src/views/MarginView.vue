<template>
  <div class="margin-page">
    <div class="page-head">
      <h2>经营分析</h2>
      <div class="header-actions">
        <el-button :loading="exporting" @click="download">导出 Excel</el-button>
        <el-button :loading="analyzing" @click="ask">DeepSeek 解读</el-button>
      </div>
    </div>

    <el-radio-group v-model="scope" aria-label="开票范围">
      <el-radio-button value="ALL">全部</el-radio-button>
      <el-radio-button value="INVOICED">开票</el-radio-button>
      <el-radio-button value="UNINVOICED">不开票</el-radio-button>
    </el-radio-group>

    <div v-loading="loadingReport" class="report-stack">
      <MarginPeriodPanel
        title="本期"
        :today="today"
        :period="period"
        :selected-key="selectedKey"
        :totals="report?.current"
        :differences="report?.differences"
        @period="selectPeriod"
        @date="selectedKey = $event"
      />
      <MarginPeriodPanel
        title="对比期"
        :today="today"
        :period="comparePeriod ?? report?.comparePeriodType ?? period"
        :selected-key="compareKey || report?.previous.periodKey || defaultComparison(period)"
        :totals="report?.previous"
        @period="selectComparePeriod"
        @date="selectCompareDate"
      />
    </div>

    <section v-if="insight" class="panel insight"><h3>DeepSeek 解读</h3><p>{{ insight }}</p></section>

    <section class="panel" v-loading="loadingTrend">
      <div class="section-heading">
        <h3>历史趋势</h3>
        <div class="trend-controls">
          <el-radio-group v-model="trendMode" aria-label="趋势周期">
            <el-radio-button value="YEAR">按月</el-radio-button>
            <el-radio-button value="MONTH">按日</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-if="trendMode === 'MONTH'"
            v-model="trendMonth"
            type="month"
            value-format="YYYY-MM"
            aria-label="趋势月份"
            :disabled-date="futureDate"
            :clearable="false"
          />
          <el-date-picker
            v-model="trendRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="—"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :disabled-date="futureDate"
            :clearable="false"
          />
        </div>
      </div>

      <div class="chart-controls">
        <el-radio-group v-model="trendMetric" size="small" aria-label="趋势指标">
          <el-radio-button v-for="metric in metrics" :key="metric.key" :value="metric.key">{{ metric.label }}</el-radio-button>
        </el-radio-group>
        <div class="legend"><span class="legend-actual" />实际 <span class="legend-average" />{{ averageTitle }}</div>
      </div>
      <MarginTrendChart :title="metricLabel + '趋势'" :points="trendPoints" :metric="trendMetric" />

      <el-table :data="trendRows" size="small" row-key="date" max-height="385">
        <el-table-column prop="date" label="期间" min-width="115" />
        <el-table-column v-for="metric in metrics" :key="metric.key" :label="metric.label" align="right" min-width="120">
          <template #default="{ row }">
            {{ amount(row.actual?.[metric.key]) }}
            <small v-if="trendMode === 'MONTH'" class="table-average">{{ amount(row.average?.[metric.key]) }}</small>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel" v-loading="loadingReport">
      <div class="section-heading">
        <h3>商品明细</h3>
        <el-radio-group v-model="productFilter" aria-label="商品筛选">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="UNKNOWN">缺成本</el-radio-button>
          <el-radio-button value="HOT">畅销</el-radio-button>
          <el-radio-button value="SLOW">滞销</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="filteredProducts" row-key="skuId" empty-text="暂无商品" :default-sort="{ prop: 'salesAmount', order: 'descending' }">
        <el-table-column prop="productName" label="商品" min-width="145" />
        <el-table-column prop="skuName" label="规格" min-width="145" />
        <el-table-column prop="quantity" label="销量" width="80" sortable />
        <el-table-column prop="salesAmount" label="销售额" min-width="122" sortable align="right">
          <template #default="{ row }">{{ amount(row.salesAmount) }}</template>
        </el-table-column>
        <el-table-column label="成本" min-width="112" align="right">
          <template #default="{ row }">{{ amount(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="毛利率" width="90" align="right">
          <template #default="{ row }">{{ row.grossMargin == null ? '—' : row.grossMargin + '%' }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="当前库存" width="90" align="right" />
        <el-table-column prop="turnoverDays" label="周转天数" width="92" align="right" />
        <el-table-column label="标记" min-width="132">
          <template #default="{ row }">
            <el-tag v-if="row.hot" type="success" size="small">畅销</el-tag>
            <el-tag v-else-if="row.slow" type="warning" size="small">滞销</el-tag>
            <el-tag v-if="row.unknownLineCount" type="info" size="small">缺成本</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { askMarginInsight, downloadMargin, marginProducts, marginReport, marginTrend } from '@/api/admin'
import type { MarginHistory, MarginPeriod, MarginProduct, MarginQuery, MarginReport, MarginScope, MarginTrendValues } from '@/api/margin'
import MarginPeriodPanel from '@/components/MarginPeriodPanel.vue'
import MarginTrendChart from '@/components/MarginTrendChart.vue'
import { addDays, todayShanghai } from '@/domain/clock'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

const today = todayShanghai()
const thisMonth = today.slice(0, 7)
const thisYear = today.slice(0, 4)
const period = ref<MarginPeriod>('MONTH')
const selectedKey = ref(thisMonth)
const comparePeriod = ref<MarginPeriod>()
const compareKey = ref('')
const scope = ref<MarginScope>('ALL')
const report = ref<MarginReport>()
const products = ref<MarginProduct[]>([])
const productFilter = ref('ALL')
const trendMode = ref<MarginPeriod>('YEAR')
const trendMonth = ref(thisMonth)
const trendRange = ref<[string, string] | null>([`${thisYear}-01-01`, today])
const trend = ref<MarginHistory>()
const trendMetric = ref<keyof MarginTrendValues>('salesAmount')
const insight = ref('')
const loadingReport = ref(false)
const loadingTrend = ref(false)
const exporting = ref(false)
const analyzing = ref(false)

const metrics = [
  { key: 'salesAmount', label: '销售额' },
  { key: 'receivedAmount', label: '实收' },
  { key: 'unpaidAmount', label: '待收' },
  { key: 'grossProfit', label: '商品毛利' },
] as const
const metricLabel = computed(() => metrics.find(({ key }) => key === trendMetric.value)?.label ?? '')
const averageTitle = computed(() => trendMode.value === 'MONTH' ? '历史均值' : '历史月均')
const trendPoints = computed(() => {
  if (trendMode.value === 'MONTH') return trend.value?.days ?? []
  return (trend.value?.months ?? []).map((point) => ({ ...point, average: trend.value?.monthlyAverage }))
})
const trendRows = computed(() => {
  const average = trend.value?.monthlyAverage
  return trendMode.value === 'YEAR' && average
    ? [...trendPoints.value, { date: averageTitle.value, actual: average }]
    : trendPoints.value
})

const query = computed<MarginQuery>(() => ({
  period: period.value,
  key: selectedKey.value,
  scope: scope.value,
  comparePeriod: comparePeriod.value,
  compareKey: compareKey.value || undefined,
}))

const filteredProducts = computed(() => products.value.filter((row) => {
  if (productFilter.value === 'UNKNOWN') return row.unknownLineCount > 0
  if (productFilter.value === 'HOT') return row.hot
  if (productFilter.value === 'SLOW') return row.slow
  return true
}))

function defaultComparison(mode: MarginPeriod) {
  return mode === 'YEAR'
    ? String(Number(selectedKey.value.slice(0, 4)) - 1)
    : addDays(`${selectedKey.value.length === 7 ? selectedKey.value : thisMonth}-01`, -1).slice(0, 7)
}

function selectPeriod(mode: MarginPeriod) {
  const year = selectedKey.value.slice(0, 4)
  selectedKey.value = mode === 'YEAR' ? year : year === thisYear ? thisMonth : `${year}-12`
  period.value = mode
}

function selectComparePeriod(mode: MarginPeriod) {
  comparePeriod.value = mode
  compareKey.value = defaultComparison(mode)
}

function selectCompareDate(key: string) {
  comparePeriod.value = key.length === 4 ? 'YEAR' : 'MONTH'
  compareKey.value = key
}

function futureDate(date: Date) {
  const key = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
  return key > today
}

function amount(value?: number | null) {
  return value == null ? '—' : '¥' + formatYuan(value)
}

watch(query, async (filters, _, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  insight.value = ''
  loadingReport.value = true
  await run(async () => {
    const [summary, details] = await Promise.all([marginReport(filters), marginProducts(filters)])
    if (!active) return
    report.value = summary
    products.value = details.list
  })
  if (active) loadingReport.value = false
}, { immediate: true })

watch([trendMonth, trendRange, scope], async ([month, range, invoiceScope], _, onCleanup) => {
  let active = true
  onCleanup(() => { active = false })
  loadingTrend.value = true
  await run(async () => {
    const data = await marginTrend({ month, scope: invoiceScope, from: range?.[0], through: range?.[1] })
    if (active) trend.value = data
  })
  if (active) loadingTrend.value = false
}, { immediate: true })

async function ask() {
  const filters = query.value
  analyzing.value = true
  await run(async () => {
    const result = await askMarginInsight(filters)
    if (filters === query.value) insight.value = result.analysis?.resultText || result.message
  })
  analyzing.value = false
}

async function download() {
  exporting.value = true
  await run(() => downloadMargin(query.value))
  exporting.value = false
}
</script>

<style scoped>
.margin-page, .report-stack { display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; }
.page-head { margin-bottom: 0; align-items: center; }
.header-actions, .section-heading, .trend-controls, .chart-controls, .legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
.section-heading, .chart-controls { justify-content: space-between; margin-bottom: 14px; }
.header-actions :deep(.el-button + .el-button) { margin-left: 0; }
h3 { margin: 0; font-size: 17px; }
.legend, .table-average { color: var(--zb-muted); font-size: 12px; }
.legend span { width: 21px; border-top: 2px solid var(--zb-red); }
.legend .legend-average { border-top: 2px dashed #687489; margin-left: 6px; }
.table-average { display: block; }
.insight p { white-space: pre-wrap; margin-bottom: 0; }
.el-tag + .el-tag { margin-left: 4px; }
</style>
