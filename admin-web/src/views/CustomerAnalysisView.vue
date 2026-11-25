<template>
  <div>
    <div class="page-head">
      <div>
        <h2>客户分析</h2>
        <p>下面的图不依赖 AI。经营建议会调用 DeepSeek，没配密钥时只提示，不会报错。</p>
      </div>
      <el-button type="primary" :loading="loadingInsight" @click="ask">生成经营建议</el-button>
    </div>
    <div class="charts" v-loading="loading">
      <section>
        <h3>等级分布</h3>
        <div v-for="item in levels" :key="item.name" class="bar-row">
          <span>{{ item.name }}</span>
          <i :style="{ width: width(item.count, levelMax) }" />
          <em>{{ item.count }}</em>
        </div>
      </section>
      <section>
        <h3>地区分布</h3>
        <div v-for="item in regions" :key="item.name" class="bar-row">
          <span>{{ item.name }}</span>
          <i :style="{ width: width(item.count, regionMax) }" />
          <em>{{ item.count }}</em>
        </div>
      </section>
      <section>
        <h3>机型排行</h3>
        <div v-for="item in models" :key="item.name" class="bar-row">
          <span>{{ item.name }}</span>
          <i :style="{ width: width(item.amount, modelMax) }" />
          <em>{{ item.quantity }} 台</em>
        </div>
      </section>
      <section>
        <h3>待付款排行</h3>
        <div v-for="item in unpaid" :key="item.name" class="bar-row">
          <span>{{ item.name }}</span>
          <i :style="{ width: width(item.amount, unpaidMax) }" />
          <em>{{ formatYuan(item.amount) }}</em>
        </div>
      </section>
    </div>
    <div class="panel" v-if="message || text">
      <p v-if="message">{{ message }}</p>
      <pre v-if="text">{{ text }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { analyzeOverall, customerOverview } from '@/api/admin'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

interface Count { name: string; count: number }
interface Amount { name: string; amount: number; quantity?: number }

const loading = ref(false)
const loadingInsight = ref(false)
const levels = ref<Count[]>([])
const regions = ref<Count[]>([])
const unpaid = ref<Amount[]>([])
const models = ref<Amount[]>([])
const message = ref('')
const text = ref('')

const levelMax = computed(() => Math.max(1, ...levels.value.map((item) => item.count)))
const regionMax = computed(() => Math.max(1, ...regions.value.map((item) => item.count)))
const unpaidMax = computed(() => Math.max(1, ...unpaid.value.map((item) => item.amount)))
const modelMax = computed(() => Math.max(1, ...models.value.map((item) => item.amount)))

function width(value: number, max: number) {
  return `${Math.max(4, Math.round((value / max) * 180))}px`
}

function ask() {
  loadingInsight.value = true
  void run(async () => {
    const result = (await analyzeOverall()) as { ok?: boolean; message?: string; analysis?: { resultText?: string } }
    message.value = result.ok ? '' : result.message || ''
    text.value = result.analysis?.resultText || ''
  }).finally(() => {
    loadingInsight.value = false
  })
}

onMounted(() => {
  loading.value = true
  void run(async () => {
    const data = (await customerOverview()) as { levels: Count[]; regions: Count[]; unpaidTop: Amount[]; models: Amount[] }
    levels.value = data.levels
    regions.value = data.regions
    unpaid.value = data.unpaidTop
    models.value = data.models
  }).finally(() => {
    loading.value = false
  })
})
</script>

<style scoped>
.charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
section { background: #fff; padding: 12px 16px; border: 1px solid var(--zb-line); }
.bar-row { display: flex; align-items: center; gap: 8px; margin: 6px 0; font-size: 13px; }
.bar-row span { width: 96px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-row i { display: inline-block; height: 10px; background: var(--zb-red); border-radius: 4px; }
pre { white-space: pre-wrap; }
</style>
