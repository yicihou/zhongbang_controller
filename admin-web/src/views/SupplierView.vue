<template>
  <div>
    <div class="page-head">
      <div>
        <h2>供应商</h2>
        <p>名称、联系人、电话、主营品牌和账期。应付余额只有老板能看到。</p>
      </div>
      <div>
        <el-button v-if="session.isBoss" @click="seed">生成演示数据</el-button>
        <el-button type="primary" @click="openCreate">新增供应商</el-button>
      </div>
    </div>
    <div class="panel">
      <div class="filters">
        <el-input v-model="keyword" clearable placeholder="名称 / 品牌" style="width: 220px" @keyup.enter="reload" />
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table :data="rows">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="mobile" label="电话" width="140" />
        <el-table-column prop="brands" label="主营品牌" min-width="140" />
        <el-table-column prop="accountDays" label="账期(天)" width="100" />
        <el-table-column v-if="session.isBoss" label="应付余额" width="120">
          <template #default="{ row }">¥{{ formatYuan(Number(row.balance ?? 0)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="session.isBoss" link type="primary" @click="openPay(row)">登记付款</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="visible" title="供应商" width="480px">
      <el-form label-width="88px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.mobile" /></el-form-item>
        <el-form-item label="主营品牌"><el-input v-model="form.brands" /></el-form-item>
        <el-form-item label="账期"><el-input-number v-model="form.accountDays" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="payVisible" title="登记已付" width="420px">
      <el-form label-width="88px">
        <el-form-item label="金额(元)"><el-input-number v-model="payYuan" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="方式">
          <el-select v-model="payMethod" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="微信" value="WECHAT_TRANSFER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="pay">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listSuppliers, paySupplier, saveSupplier, seedTradeDemo } from '@/api/admin'
import { formatYuan, yuanToFen } from '@/domain/money'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const session = useSessionStore()
const rows = ref<Record<string, unknown>[]>([])
const keyword = ref('')
const visible = ref(false)
const payVisible = ref(false)
const payYuan = ref(0)
const payMethod = ref('BANK_TRANSFER')
const payingId = ref(0)
const form = reactive({ name: '', contact: '', mobile: '', brands: '', accountDays: 0, remark: '' })

async function reload() {
  await run(async () => {
    const page = await listSuppliers(keyword.value)
    rows.value = page.list
  })
}

function openCreate() {
  form.name = ''
  form.contact = ''
  form.mobile = ''
  form.brands = ''
  form.accountDays = 0
  form.remark = ''
  visible.value = true
}

async function save() {
  await run(async () => {
    await saveSupplier({ ...form })
    visible.value = false
    await reload()
  })
}

function openPay(row: Record<string, unknown>) {
  payingId.value = Number(row.id)
  payYuan.value = 0
  payVisible.value = true
}

async function pay() {
  await run(async () => {
    await paySupplier(payingId.value, { amount: yuanToFen(payYuan.value), method: payMethod.value })
    payVisible.value = false
    await reload()
  })
}

async function seed() {
  await run(async () => {
    await seedTradeDemo()
    await reload()
  })
}

onMounted(reload)
</script>
