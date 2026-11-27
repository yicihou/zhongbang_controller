<template>
  <div>
    <div class="page-head">
      <div>
        <h2>库存流水</h2>
        <p>
          每一次库存变动都在这里：进货入库、入库单作废、销售出库、订单取消回滚、商品期初和盘点调整。
          数量正数是增加，负数是减少，最后一列是这一笔之后的余额。
        </p>
      </div>
    </div>
    <div class="panel">
      <div class="filters">
        <el-select v-model="skuId" clearable filterable placeholder="全部规格" style="width: 260px" @change="reload">
          <el-option v-for="sku in skus" :key="sku.id" :label="sku.label" :value="sku.id" />
        </el-select>
        <el-select v-model="bizType" clearable placeholder="全部类型" style="width: 150px" @change="reload">
          <el-option v-for="(label, code) in BIZ_TYPE_LABEL" :key="code" :label="label" :value="code" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" @change="reload" />
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" data-testid="stock-log-table">
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <b>{{ row.productName || '已删除的规格' }}</b>
            <div class="muted">{{ row.model || '未填型号' }} · {{ row.skuName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.bizType)" effect="light">{{ BIZ_TYPE_LABEL[row.bizType] || row.bizType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="100">
          <template #default="{ row }">
            <span :class="row.quantity > 0 ? 'in' : 'out'">{{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stockAfter" label="结存" width="90" />
        <el-table-column label="进价" width="110">
          <template #default="{ row }">
            <span v-if="row.unitCost != null && session.isBoss">¥{{ formatYuan(row.unitCost) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column label="关联单号" min-width="140">
          <template #default="{ row }">
            <span v-if="row.bizId == null" class="muted">—</span>
            <span v-else class="muted">#{{ row.bizId }}</span>
          </template>
        </el-table-column>
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
/**
 * 库存流水（进销存）。老板按它对库存：期初 + 进货 − 销售 ± 盘点 = 结存。
 * 列表接口是 /api/admin/stock-logs，规格下拉复用商品列表，不另开接口。
 */
import { onMounted, ref } from 'vue'
import { listProducts, listStockLogs } from '@/api/admin'
import type { StockLogRow } from '@/api/types'
import { formatYuan } from '@/domain/money'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()

/** 和后端 StockLogService 上的常量一一对应。 */
const BIZ_TYPE_LABEL: Record<string, string> = {
  INIT: '期初',
  PURCHASE_IN: '进货入库',
  PURCHASE_VOID: '入库作废',
  PURCHASE_CORRECT: '采购更正',
  PURCHASE_CORRECT_IN: '更正新增规格',
  SALE_OUT: '销售出库',
  SALE_CANCEL: '取消回滚',
  ADJUST: '盘点调整',
}

const rows = ref<StockLogRow[]>([])
const skus = ref<{ id: number; label: string }[]>([])
const skuId = ref<number | undefined>(undefined)
const bizType = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)

function tagType(type: string): 'success' | 'warning' | 'info' | 'danger' {
  if (type === 'PURCHASE_IN' || type === 'INIT' || type === 'SALE_CANCEL') return 'success'
  if (type === 'SALE_OUT') return 'danger'
  if (type === 'ADJUST') return 'warning'
  return 'info'
}

async function loadSkus() {
  const data = await listProducts({ page: 1, size: 100 })
  skus.value = data.list.flatMap((product) =>
    (product.skus ?? []).map((sku) => ({
      id: sku.id,
      label: `${product.name}${product.model ? ` ${product.model}` : ''} · ${sku.title}（结存 ${sku.stock}）`,
    })),
  )
}

async function load() {
  loading.value = true
  await run(async () => {
    const [from, to] = range.value ?? [undefined, undefined]
    const data = await listStockLogs({
      skuId: skuId.value,
      bizType: bizType.value || undefined,
      from,
      to,
      page: page.value,
      size: size.value,
    })
    rows.value = data.list
    total.value = data.total
  })
  loading.value = false
}

function reload() {
  page.value = 1
  void load()
}

function onPage(next: number) {
  page.value = next
  void load()
}

onMounted(() => {
  void run(loadSkus)
  void load()
})
</script>

<style scoped>
.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.muted {
  color: var(--zb-muted);
  font-size: 12px;
}
.in {
  color: var(--zb-green, #2f9e44);
  font-weight: 600;
}
.out {
  color: var(--zb-red);
  font-weight: 600;
}
</style>
