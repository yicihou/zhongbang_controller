<template>
  <div v-loading="loading">
    <div class="page-head" v-if="detail">
      <div>
        <h2>{{ detail.profile.name }} <el-tag v-if="detail.overdue" type="danger">逾期</el-tag></h2>
        <p>{{ detail.profile.customerTypeLabel }} · {{ detail.profile.levelName }}<span v-if="detail.profile.levelManual">（手动指定）</span> · {{ detail.profile.mobile }}</p>
      </div>
      <el-button @click="router.push('/customers')">返回列表</el-button>
    </div>
    <el-tabs v-if="detail" type="border-card">
      <el-tab-pane label="基本资料">
        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="姓名"><el-input v-model="detail.profile.name" /></el-form-item>
          <el-form-item label="手机"><el-input v-model="detail.profile.mobile" /></el-form-item>
          <el-form-item label="备用电话"><el-input v-model="detail.profile.altMobile" /></el-form-item>
          <el-form-item label="微信号"><el-input v-model="detail.profile.wechat" /></el-form-item>
          <el-form-item label="客户类型">
            <el-select v-model="detail.profile.customerType">
              <el-option v-for="(label, code) in CUSTOMER_TYPE_LABEL" :key="code" :label="label" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="厂名或店名"><el-input v-model="detail.profile.shopName" /></el-form-item>
          <el-form-item label="厂里机器数量"><el-input v-model.number="detail.profile.machineCount" /></el-form-item>
          <el-form-item label="主营产品"><el-input v-model="detail.profile.mainProducts" /></el-form-item>
          <el-form-item label="来源"><el-input v-model="detail.profile.sourceLabel" disabled /></el-form-item>
          <el-form-item label="介绍人"><el-input v-model="detail.profile.referrer" /></el-form-item>
          <el-form-item label="生日"><el-input v-model="detail.profile.birthday" placeholder="yyyy-MM-dd" /></el-form-item>
          <el-form-item label="地区"><el-input v-model="detail.profile.region" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="detail.profile.remark" type="textarea" /></el-form-item>
          <el-form-item label="标签"><el-input v-model="detail.profile.tags" /></el-form-item>
          <el-button type="primary" @click="saveProfile">保存资料</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="地址簿">
        <el-table :data="detail.addresses">
          <el-table-column prop="region" label="地区" width="160" />
          <el-table-column prop="detail" label="详细地址" />
          <el-table-column label="默认" width="80">
            <template #default="{ row }">{{ row.defaultAddress ? '是' : '' }}</template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="100" />
        </el-table>
        <el-form inline style="margin-top: 12px">
          <el-input v-model="address.detail" placeholder="详细地址" style="width: 320px" />
          <el-button type="primary" @click="saveAddress">添加地址</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="消费与订单">
        <p>
          下单总金额 {{ formatYuan(detail.stats.orderAmount) }}，实收 {{ formatYuan(detail.stats.receivedAmount) }}，
          待付款 {{ formatYuan(detail.stats.unpaidAmount) }}，订单 {{ detail.stats.orderCount }}，
          客单价 {{ detail.stats.avgAmount == null ? '—' : formatYuan(detail.stats.avgAmount) }}。
          首次 {{ detail.stats.firstOrderAt || '—' }}，最近 {{ detail.stats.lastOrderAt || '—' }}，
          距今 {{ detail.stats.daysSinceLast ?? '—' }} 天。
        </p>
        <h3>买过的机型</h3>
        <el-table :data="detail.models">
          <el-table-column prop="model" label="型号" />
          <el-table-column prop="brand" label="品牌" width="120" />
          <el-table-column prop="times" label="次数" width="80" />
          <el-table-column prop="quantity" label="台数" width="80" />
          <el-table-column label="金额" width="120"><template #default="{ row }">{{ formatYuan(row.amount) }}</template></el-table-column>
          <el-table-column prop="lastAt" label="最后购买" width="180" />
        </el-table>
        <h3>过往订单</h3>
        <el-table :data="detail.orders">
          <el-table-column prop="orderNo" label="订单">
            <template #default="{ row }"><router-link :to="`/orders/${row.id}`">{{ row.orderNo }}</router-link></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column label="应付" width="120"><template #default="{ row }">{{ formatYuan(row.payableAmount) }}</template></el-table-column>
          <el-table-column label="已付" width="120"><template #default="{ row }">{{ formatYuan(row.paidAmount) }}</template></el-table-column>
          <el-table-column prop="createdAt" label="下单时间" width="180" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="设备与报修">
        <el-table :data="detail.assets">
          <el-table-column prop="brand" label="品牌" />
          <el-table-column prop="model" label="型号" />
          <el-table-column prop="sn" label="机身码" />
          <el-table-column prop="source" label="来源" width="110" />
          <el-table-column prop="warrantyEnd" label="保修到期" width="120" />
          <el-table-column label="已过保" width="90"><template #default="{ row }">{{ row.warrantyExpired ? '是' : '否' }}</template></el-table-column>
          <el-table-column prop="repairCount" label="报修次数" width="100" />
        </el-table>
        <h3>报修记录</h3>
        <el-table :data="detail.repairs">
          <el-table-column prop="ticketNo" label="单号">
            <template #default="{ row }"><router-link :to="`/repairs/${row.id}`">{{ row.ticketNo }}</router-link></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" />
          <el-table-column label="待付"><template #default="{ row }">{{ formatYuan(row.unpaidAmount) }}</template></el-table-column>
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="偏好与跟进">
        <p>常买品牌：{{ detail.preference.autoBrand || '—' }}，品类：{{ detail.preference.autoCategory || '—' }}，价格区间：{{ detail.preference.autoPriceBand || '—' }}，付款方式：{{ detail.preference.autoPayMethod || '—' }}，常需上门安装：{{ detail.preference.autoNeedInstall == null ? '不清楚' : detail.preference.autoNeedInstall ? '是' : '否' }}</p>
        <el-input v-model="detail.preference.manualNote" type="textarea" placeholder="老板补充" />
        <el-button style="margin-top: 8px" @click="savePreference">保存补充</el-button>
        <el-table :data="detail.follows" style="margin-top: 12px">
          <el-table-column prop="followDate" label="日期" width="120" />
          <el-table-column prop="content" label="内容" />
          <el-table-column prop="nextDate" label="下次跟进" width="120" />
        </el-table>
        <el-form inline style="margin-top: 12px">
          <el-input v-model="follow.content" placeholder="跟进内容" style="width: 280px" />
          <el-input v-model="follow.nextDate" placeholder="下次 yyyy-MM-dd" style="width: 160px" />
          <el-button type="primary" @click="saveFollow">记一笔</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="协议还款">
        <el-table :data="detail.agreements">
          <el-table-column prop="dueDate" label="约定日" width="120" />
          <el-table-column label="金额" width="120"><template #default="{ row }">{{ formatYuan(row.agreedAmount) }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="note" label="说明" />
          <el-table-column label="材料">
            <template #default="{ row }">
              <div v-for="(file, index) in row.materials" :key="index">
                <audio v-if="file.kind === 'AUDIO'" :src="file.url" controls />
                <a v-else-if="file.url" :href="file.url" target="_blank">{{ file.name || '下载' }}</a>
                <span v-else>{{ file.text }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="fulfill(row.id)">已履约</el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="voidOne(row.id)">作废</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-form label-width="90px" style="max-width: 640px; margin-top: 12px">
          <el-form-item label="范围">
            <el-select v-model="agreement.scope">
              <el-option label="整个客户" value="CUSTOMER" />
              <el-option label="某张订单" value="ORDER" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="agreement.scope === 'ORDER'" label="订单">
            <el-select v-model="agreement.orderId">
              <el-option v-for="order in detail.orders" :key="order.id" :label="order.orderNo" :value="order.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="约定日"><el-input v-model="agreement.dueDate" placeholder="yyyy-MM-dd" /></el-form-item>
          <el-form-item label="金额（元）"><el-input v-model.number="agreement.yuan" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="agreement.note" type="textarea" /></el-form-item>
          <el-form-item label="材料">
            <input type="file" multiple accept=".jpg,.jpeg,.png,.webp,.mp3,.m4a,.wav,.amr,.pdf,.doc,.docx,.xls,.xlsx" @change="onFiles" />
            <div v-for="(file, index) in materials" :key="index">{{ file.name }}</div>
          </el-form-item>
          <el-button type="primary" @click="saveAgreement">登记协议</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="分析">
        <el-button type="primary" :loading="analyzing" @click="analyze">生成分析</el-button>
        <p v-if="insightMessage">{{ insightMessage }}</p>
        <pre v-if="insightText" class="insight">{{ insightText }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
/** 客户详情。资料、地址、订单、设备、偏好、协议和分析分在不同标签里。 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  addCustomerAddress,
  addCustomerAgreement,
  addCustomerFollow,
  analyzeCustomer,
  fulfillAgreement,
  getCustomer,
  saveCustomerPreference,
  updateCustomer,
  uploadImages,
  voidAgreement,
} from '@/api/admin'
import { CUSTOMER_TYPE_LABEL } from '@/domain/customer'
import { formatYuan, yuanToFen } from '@/domain/money'
import { run } from '@/utils/feedback'

interface Detail {
  profile: {
    id: number
    name: string
    mobile: string
    altMobile: string
    wechat: string
    customerType: string
    customerTypeLabel: string
    shopName: string
    machineCount: number | null
    mainProducts: string
    sourceLabel: string
    referrer: string
    birthday: string
    remark: string
    tags: string
    region: string
    levelName: string
    levelManual: boolean
    source: string
  }
  stats: {
    orderAmount: number
    receivedAmount: number
    unpaidAmount: number
    orderCount: number
    avgAmount: number | null
    firstOrderAt: string
    lastOrderAt: string
    daysSinceLast: number | null
  }
  addresses: { id: number; region: string; detail: string; defaultAddress: boolean; source: string }[]
  orders: { id: number; orderNo: string; status: string; payableAmount: number; paidAmount: number; createdAt: string }[]
  models: { model: string; brand: string; times: number; quantity: number; amount: number; lastAt: string }[]
  assets: { brand: string; model: string; sn: string; source: string; warrantyEnd: string; warrantyExpired: boolean; repairCount: number }[]
  repairs: { id: number; ticketNo: string; status: string; unpaidAmount: number; createdAt: string }[]
  preference: { autoBrand: string; autoCategory: string; autoPriceBand: string; autoPayMethod: string; autoNeedInstall: boolean | null; manualNote: string }
  follows: { followDate: string; content: string; nextDate: string }[]
  agreements: { id: number; dueDate: string; agreedAmount: number; status: string; note: string; materials: { kind: string; url?: string; name?: string; text?: string }[] }[]
  overdue: boolean
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const analyzing = ref(false)
const detail = ref<Detail | null>(null)
const insightText = ref('')
const insightMessage = ref('')
const address = reactive({ detail: '' })
const follow = reactive({ content: '', nextDate: '' })
const agreement = reactive({ scope: 'CUSTOMER', orderId: undefined as number | undefined, dueDate: '', yuan: 0, note: '' })
const materials = ref<{ kind?: string; url: string; name: string; contentType?: string }[]>([])

async function load() {
  loading.value = true
  try {
    detail.value = (await getCustomer(String(route.params.id))) as unknown as Detail
  } finally {
    loading.value = false
  }
}

function saveProfile() {
  if (!detail.value) return
  const profile = detail.value.profile
  void run(() => updateCustomer(profile.id, { ...profile }).then(load))
}
function saveAddress() {
  if (!detail.value) return
  void run(() => addCustomerAddress(detail.value!.profile.id, { detail: address.detail, defaultAddress: false }).then(load))
}
function saveFollow() {
  if (!detail.value) return
  void run(() => addCustomerFollow(detail.value!.profile.id, { ...follow }).then(load))
}
function savePreference() {
  if (!detail.value) return
  void run(() => saveCustomerPreference(detail.value!.profile.id, { manualNote: detail.value!.preference.manualNote }).then(load))
}
function saveAgreement() {
  if (!detail.value) return
  void run(() =>
    addCustomerAgreement(detail.value!.profile.id, {
      scope: agreement.scope,
      orderId: agreement.orderId,
      dueDate: agreement.dueDate,
      agreedAmount: yuanToFen(agreement.yuan),
      note: agreement.note,
      materials: materials.value,
    }).then(() => {
      materials.value = []
      return load()
    }),
  )
}
function fulfill(id: number) {
  void run(() => fulfillAgreement(id).then(load))
}
function voidOne(id: number) {
  void run(() => voidAgreement(id).then(load))
}
function onFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? [...input.files] : []
  if (!files.length) return
  void run(async () => {
    const uploaded = await uploadImages(files, 'agreement')
    materials.value.push(...uploaded)
  })
}

function analyze() {
  if (!detail.value) return
  analyzing.value = true
  void run(async () => {
    const result = (await analyzeCustomer(detail.value!.profile.id)) as { ok?: boolean; message?: string; analysis?: { resultText?: string } }
    insightMessage.value = result.ok ? '' : result.message || ''
    insightText.value = result.analysis?.resultText || ''
  }).finally(() => {
    analyzing.value = false
  })
}

onMounted(() => void load())
</script>

<style scoped>
.insight { white-space: pre-wrap; background: #fafafa; padding: 12px; }
</style>
