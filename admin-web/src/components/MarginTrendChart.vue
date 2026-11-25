<template>
  <svg v-if="chart.hasValues" class="chart" viewBox="0 0 900 250" role="img" :aria-label="title">
    <g v-for="tick in chart.ticks" :key="tick.value">
      <line x1="104" x2="880" :y1="tick.y" :y2="tick.y" class="grid" />
      <text x="96" :y="tick.y + 4" text-anchor="end">{{ show(tick.value) }}</text>
    </g>
    <path :d="chart.average.path" class="average" />
    <path :d="chart.actual.path" class="actual" />
    <g v-for="series in [chart.average, chart.actual]" :key="series.name" :class="series.name">
      <circle v-for="point in series.points" :key="point.date" :cx="point.x" :cy="point.y" r="3.5">
        <title>{{ point.date }} · {{ show(point.value) }}</title>
      </circle>
    </g>
    <text v-for="tick in chart.dates" :key="tick.date" :x="tick.x" y="240" text-anchor="middle">{{ tick.date }}</text>
  </svg>
  <el-empty v-else description="暂无数据" :image-size="52" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MarginTrendPoint, MarginTrendValues } from '@/api/margin'
import { formatYuan } from '@/domain/money'

const props = defineProps<{
  title: string
  points: MarginTrendPoint[]
  metric: keyof MarginTrendValues
}>()

function show(value: number) {
  return '¥' + formatYuan(Math.round(value))
}

const chart = computed(() => {
  const values = props.points.flatMap(({ actual, average }) => [actual?.[props.metric], average?.[props.metric]])
    .filter((value): value is number => value != null)
  const lower = Math.min(0, ...values)
  const higher = Math.max(100, ...values)
  const stride = Math.max(4, Math.ceil((higher - lower) / 4))
  const x = (index: number) => 104 + (props.points.length === 1 ? 388 : 776 * index / Math.max(1, props.points.length - 1))
  const y = (value: number) => 212 - (value - lower) * 190 / (stride * 4)
  const ticks = Array.from({ length: 5 }, (_, index) => {
    const value = lower + stride * index
    return { y: y(value), value }
  })
  const labels = new Set(Array.from({ length: Math.min(6, props.points.length) }, (_, index) =>
    Math.round(index * (props.points.length - 1) / Math.max(1, Math.min(6, props.points.length) - 1))))
  const dates = props.points.flatMap(({ date }, index) => labels.has(index)
    ? [{ date: date.length === 10 ? date.slice(5) : date, x: x(index) }]
    : [])

  function series(name: 'actual' | 'average') {
    let contiguous = false
    let path = ''
    const points = props.points.flatMap((point, index) => {
      const value = point[name]?.[props.metric]
      if (value == null) {
        contiguous = false
        return []
      }
      const position = { date: point.date, value, x: x(index), y: y(value) }
      path += `${contiguous ? 'L' : 'M'}${position.x},${position.y} `
      contiguous = true
      return [position]
    })
    return { name, points, path }
  }

  return { ticks, dates, actual: series('actual'), average: series('average'), hasValues: values.length > 0 }
})
</script>

<style scoped>
.chart { display: block; width: 100%; overflow: visible; }
.grid { stroke: var(--zb-line); }
text { fill: var(--zb-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
path { fill: none; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
path.actual { stroke: var(--zb-red); }
path.average { stroke: #687489; stroke-dasharray: 6 5; }
g.actual { fill: var(--zb-red); }
g.average { fill: #687489; }
circle:hover { r: 6px; }
</style>
