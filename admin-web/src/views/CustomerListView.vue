<template>
  <div>
    <div class="page-head">
      <div>
        <h2>客户</h2>
        <p>只有老板能看客户档案。订单页上的手机号仍然给其他员工看，但进不了这里。</p>
      </div>
      <div class="actions">
        <el-button @click="downloadCustomerTemplate('csv')">下载导入模板</el-button>
        <el-button @click="pickImport">导入</el-button>
        <el-button @click="exportFile('csv')">导出 CSV</el-button>
        <el-button @click="exportFile('xlsx')">导出 Excel</el-button>
        <el-button @click="seed">生成演示数据</el-button>
        <el-button type="primary" @click="openCreate">新增客户</el-button>
      </div>
    </div>
    <input ref="fileInput" type="file" accept=".csv,.xlsx" hidden @change="onImport" />
    <div class="panel">
      <div class="filters">
        <el-input v-model="query.name" clearable placeholder="姓名" style="width: 140px" @keyup.enter="reload" />
        <el-input v-model="query.mobile" clearable placeholder="手机" style="width: 150px" @keyup.enter="reload" />
        <el-input v-model="query.region" clearable placeholder="地区" style="width: 150px" @keyup.enter="reload" />
        <el-input v-model="query.tag" clearable placeholder="标签" style="width: 120px" @keyup.enter="reload" />
        <el-select v-model="query.customerType" clearable placeholder="客户类型" style="width: 140px" @change="reload">
          <el-option v-for="(label, code) in CUSTOMER_TYPE_LABEL" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="query.level" clearable placeholder="等级" style="width: 120px" @change="reload">
          <el-option v-for="rule in rules" :key="rule.code" :label="rule.name" :value="rule.code" />
        </el-select>
        <el-checkbox v-model="unpaidOnly" @change="reload">只看有待付款</el-checkbox>
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="id" @sort-change="onSort">
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">
            <router-link :to="`/customers/${row.id}`">{{ row.name }}</router-link>
            <el-tag v-if="row.overdue" type="danger" size="small" style="margin-left: 6px">逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机" width="130" />
        <el-table-column prop="region" label="地区" min-width="140" />
        <el-table-column prop="customerTypeLabel" label="客户类型" width="120" />
        <el-table-column label="等级" width="90">
          <template #default="{ row }">{{ row.levelName }}<span v-if="row.levelManual">*</span></template>
        </el-table-column>
        <el-table-column prop="orderAmount" label="下单总金额" width="120" sortable="custom">
          <template #default="{ row }">{{ formatYuan(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" label="待付款金额" width="120" sortable="custom">
          <template #default="{ row }">{{ formatYuan(row.unpaidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" min-width="120" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page" @current-change="onPage" />
      </div>
    </div>

    <el-dialog v-model="visible" title="新增客户" width="560px">
      <el-form label-width="110px">
        <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机" required><el-input v-model="form.mobile" maxlength="11" /></el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="form.customerType" style="width: 100%">
            <el-option v-for="(label, code) in CUSTOMER_TYPE_LABEL" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.source" style="width: 100%">
            <el-option label="电话下单" value="PHONE" />
            <el-option label="门店散客" value="STORE" />
            <el-option label="转介绍" value="REFERRAL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="地区"><el-input v-model="form.region" /></el-form-item>
        <el-form-item label="厂名或店名"><el-input v-model="form.shopName" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 客户列表。搜索、排序、导出、手动建档和导入都在这一页。
 * 待付款是未收齐的订单加上报修待付，和「下单总金额」分两列。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createCustomer,
  downloadCustomerTemplate,
  downloadCustomers,
  importCustomers,
  listCustomers,
  listLevelRules,
  seedDemoCustomers,
  type CustomerQuery,
} from '@/api/admin'
import { CUSTOMER_TYPE_LABEL, type CustomerListItem, type LevelRule } from '@/domain/customer'
import { formatYuan } from '@/domain/money'
import { run } from '@/utils/feedback'

const router = useRouter()
const rows = ref<CustomerListItem[]>([])
const rules = ref<LevelRule[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const unpaidOnly = ref(false)
const visible = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
/**
 * 默认按最近下单倒序：老板习惯最新的客户排在最上面。
 * 这一列不在表格里显示（老板要求去掉订单数和最近下单两列），但排序口径保留，
 * 所以取消排序时仍然回到 lastOrderAt，而不是悄悄换成别的字段。
 */
const query = reactive<CustomerQuery>({ sort: 'lastOrderAt', direction: 'desc' })
const form = reactive({ name: '', mobile: '', customerType: 'PERSONAL', source: 'PHONE', region: '', shopName: '', tags: '' })

async function reload() {
  loading.value = true
  try {
    const data = await listCustomers({ ...query, unpaidOnly: unpaidOnly.value ? '1' : '', page: page.value, size: 20 })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onPage(next: number) {
  page.value = next
  void reload()
}

function onSort(event: { prop: string; order: 'ascending' | 'descending' | null }) {
  query.sort = event.prop || 'lastOrderAt'
  query.direction = event.order === 'ascending' ? 'asc' : 'desc'
  void reload()
}

function openCreate() {
  visible.value = true
}

function submit() {
  void run(async () => {
    const created = await createCustomer({ ...form })
    visible.value = false
    const id = (created.profile as { id: number }).id
    await router.push(`/customers/${id}`)
  })
}

function exportFile(format: 'csv' | 'xlsx') {
  void run(() => downloadCustomers({ ...query, unpaidOnly: unpaidOnly.value ? '1' : '' }, format))
}

function pickImport() {
  fileInput.value?.click()
}

function onImport(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  void run(async () => {
    const result = await importCustomers(file)
    window.alert(`导入完成：成功 ${result.created}，跳过 ${result.skipped}${result.errors.length ? '\n' + result.errors.join('\n') : ''}`)
    await reload()
  })
}

function seed() {
  void run(async () => {
    const result = await seedDemoCustomers()
    window.alert(result.message)
    await reload()
  })
}

onMounted(() => {
  void run(async () => {
    rules.value = await listLevelRules()
    await reload()
  })
})
</script>

<style scoped>
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; align-items: center; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
