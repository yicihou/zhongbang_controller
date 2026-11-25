<template>
  <section class="panel report-panel">
    <div class="report-heading">
      <div>
        <h3>{{ title }}</h3>
        <span v-if="totals">{{ totals.from }} — {{ totals.through }}</span>
      </div>
      <div class="period-controls">
        <el-radio-group :model-value="period" @change="changePeriod" :aria-label="title + '报表周期'">
          <el-radio-button value="MONTH">月报</el-radio-button>
          <el-radio-button value="YEAR">年报</el-radio-button>
        </el-radio-group>
        <el-date-picker
          :key="period"
          :model-value="selectedKey"
          :type="period === 'MONTH' ? 'month' : 'year'"
          :value-format="period === 'MONTH' ? 'YYYY-MM' : 'YYYY'"
          :aria-label="title + '日期'"
          :disabled-date="futureDate"
          :clearable="false"
          @update:model-value="changeDate"
        />
      </div>
    </div>
    <div class="primary-metrics">
      <div v-for="metric in metrics" :key="metric.key" class="metric">
        <span class="label">{{ metric.label }}</span>
        <div class="metric-value">
          <strong>{{ display(totals?.available ? totals[metric.key] : null) }}</strong>
          <small v-if="deltas[metric.key]" :class="deltas[metric.key]?.tone">{{ deltas[metric.key]?.text }}</small>
        </div>
        <div v-if="metric.key === 'grossProfit'" class="rate">
          <span>毛利率 {{ totals?.grossMargin == null ? '—' : totals.grossMargin + '%' }}</span>
          <small v-if="rateDelta" :class="rateDelta.tone">{{ rateDelta.text }}</small>
        </div>
      </div>
    </div>
    <dl class="secondary-metrics">
      <div v-for="metric in details" :key="metric.key">
        <dt>{{ metric.label }}</dt>
        <dd>
          {{ display(totals?.available ? totals[metric.key] : null) }}
          <small v-if="deltas[metric.key]" :class="deltas[metric.key]?.tone">{{ deltas[metric.key]?.text }}</small>
        </dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MarginPeriod, MarginTotals } from '@/api/margin'
import { formatYuan } from '@/domain/money'

const props = defineProps<{
  title: string
  totals?: MarginTotals
  differences?: Record<string, number>
  period: MarginPeriod
  selectedKey: string
  today: string
}>()
const emit = defineEmits<{ period: [value: MarginPeriod]; date: [value: string] }>()

const metrics = [
  { key: 'salesAmount', label: '销售额' },
  { key: 'receivedAmount', label: '实收' },
  { key: 'unpaidAmount', label: '待收' },
  { key: 'grossProfit', label: '商品毛利' },
] as const
const details = [
  { key: 'salesTaxIncluded', label: '含税销售额' },
  { key: 'salesTaxExcluded', label: '不含税销售额' },
  { key: 'goodsCost', label: '商品成本' },
  { key: 'unknownSalesAmount', label: '缺成本销售额' },
  { key: 'installIncome', label: '安装费' },
  { key: 'repairIncome', label: '维修收入' },
] as const

type MetricKey = (typeof metrics | typeof details)[number]['key']
type Delta = { tone: 'up' | 'down'; text: string }

function display(amount?: number | null) {
  return amount == null ? '—' : '¥' + formatYuan(amount)
}

function delta(diff: number, magnitude: string): Delta | undefined {
  if (diff === 0) return
  return { tone: diff > 0 ? 'up' : 'down', text: (diff > 0 ? '+' : '−') + magnitude }
}

const deltas = computed(() => {
  const result: Partial<Record<MetricKey, Delta>> = {}
  for (const { key } of [...metrics, ...details]) {
    const diff = props.differences?.[key]
    if (diff == null) continue
    result[key] = delta(diff, display(Math.abs(diff)))
  }
  return result
})
const rateDelta = computed(() => {
  const diff = props.differences?.grossMargin
  if (diff == null) return
  return delta(diff, Math.abs(diff).toFixed(2) + ' 个百分点')
})

function changePeriod(value: string | number | boolean | undefined) {
  if (value === 'MONTH' || value === 'YEAR') emit('period', value)
}

function changeDate(value: unknown) {
  if (typeof value === 'string' && value) emit('date', value)
}

function futureDate(date: Date) {
  const key = [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-')
  return key > props.today
}
</script>

<style scoped>
.report-heading,
.period-controls,
.metric-value,
.rate {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
}
.report-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}
h3 { margin: 0 0 3px; font-size: 17px; }
.report-heading span, .label, dt, .rate { color: var(--zb-muted); font-size: 13px; }
.primary-metrics, .secondary-metrics { display: grid; gap: 10px; }
.primary-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.metric {
  padding: 12px;
  min-height: 68px;
  border: 1px solid var(--zb-line);
  border-radius: 8px;
  background: var(--zb-bg);
}
.metric-value { margin-top: 5px; row-gap: 2px; }
.metric-value strong { font-size: clamp(17px, 1.65vw, 25px); }
.rate { margin-top: 4px; }
.secondary-metrics {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin: 14px 0 0;
}
dd { margin: 4px 0 0; font-size: 14px; overflow-wrap: anywhere; }
dd small { display: block; margin-top: 2px; }
small { font-size: 12px; font-weight: 600; }
.up { color: var(--zb-red); }
.down { color: var(--zb-muted); }
.metric strong, .rate, dd { font-variant-numeric: tabular-nums; }
@media (max-width: 1080px) {
  .secondary-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 850px) {
  .primary-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
