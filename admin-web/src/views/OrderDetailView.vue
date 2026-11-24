<template>
  <div v-loading="loading" data-testid="order-detail">
    <div class="page-head">
      <div>
        <h2>{{ order?.orderNo || '订单详情' }}</h2>
        <p v-if="order">{{ order.customerName }} · {{ order.customerMobile }} · {{ order.createdAt }}</p>
      </div>
      <div class="actions" v-if="order">
        <el-tag :type="orderTagType(order.status)" size="large" effect="light">{{ ORDER_STATUS_LABEL[order.status] }}</el-tag>
        <el-button @click="openAdjust(false)" :disabled="closed">改价</el-button>
        <el-button @click="openFreight" :disabled="closed">运费</el-button>
        <el-button @click="openSplit" :disabled="!fullWait">拆定金/尾款</el-button>
        <el-button @click="openAppend" :disabled="closed">追加应付</el-button>
        <el-button type="danger" plain @click="openCancel" :disabled="!canCancel">取消</el-button>
      </div>
    </div>

    <template v-if="order">
      <div class="metrics">
        <div><span>商品</span><b>¥{{ formatYuan(order.goodsAmount) }}</b></div>
        <div data-testid="install-fee-card">
          <span>安装费</span>
          <b v-if="feeState.kind === 'none'">—</b>
          <template v-else-if="feeState.kind === 'pending'">
            <b class="pending">待定</b>
            <el-button link type="primary" size="small" :disabled="closed" @click="openAdjust(true)">设定安装费</el-button>
          </template>
          <b v-else>¥{{ formatYuan(feeState.amount) }}</b>
        </div>
        <div><span>运费</span><b>¥{{ formatYuan(order.freightAmount) }}</b></div>
        <div><span>调整</span><b>¥{{ formatYuan(order.adjustAmount) }}</b></div>
        <div><span>应付</span><b class="money">¥{{ formatYuan(order.payableAmount) }}</b></div>
        <div><span>已付</span><b>¥{{ formatYuan(order.paidAmount) }}</b></div>
      </div>

      <div class="grid">
        <section class="panel">
          <h3>商品与发货</h3>
          <el-table :data="order.items" size="small">
            <el-table-column label="商品" min-width="160">
              <template #default="{ row }">
                <div>{{ row.productName }}</div>
                <div class="muted">{{ row.skuName }} × {{ row.quantity }}</div>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="110">
              <template #default="{ row }">¥{{ formatYuan(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="小计" width="110">
              <template #default="{ row }">¥{{ formatYuan(row.lineAmount) }}</template>
            </el-table-column>
            <el-table-column label="发货方式" width="110">
              <template #default="{ row }">
                <el-tag effect="plain">{{ deliveryText(row.deliveryMethod) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="安装 / 凭证" min-width="220">
              <template #default="{ row }">
                <div v-if="row.deliveryMethod === 'ON_SITE_INSTALL'">
                  <template v-if="row.installFee == null">
                    安装费 <span class="pending">待定</span>
                    <el-button link type="primary" size="small" :disabled="closed" @click="openAdjust(true)">设定安装费</el-button>
                  </template>
                  <template v-else>安装费 ¥{{ formatYuan(row.installFee) }}</template>
                  <div class="muted">
                    {{ row.installDate || '未定日期' }} {{ row.installSlot || '' }}
                    {{ row.technicianName ? `· ${row.technicianName}` : '· 未派师傅' }}
                    {{ row.workOrderNo ? `· ${row.workOrderNo}` : '' }}
                  </div>
                </div>
                <div class="muted">{{ shipmentText(row) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button v-if="row.deliveryMethod === 'ON_SITE_INSTALL'" link type="primary" @click="openAssign(row)">
                  派师傅
                </el-button>
                <el-button link type="primary" :disabled="!canShip(row)" @click="openShip(row)">发货</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="panel">
          <h3>客户</h3>
          <p>{{ order.addressText || '未留地址' }}</p>
          <p class="muted">{{ order.contactRemark || '无客户备注' }}</p>
          <el-button size="small" @click="copy(order.customerMobile)">复制手机号</el-button>
          <p v-if="order.adminRemark" class="remark">{{ order.adminRemark }}</p>
        </section>
      </div>

      <section class="panel block">
        <h3>应付与收款</h3>
        <el-table :data="order.payments" size="small">
          <el-table-column prop="title" label="标题" width="120" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ paymentTypeText(row.type) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }"><span class="money">¥{{ formatYuan(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ paymentStatusText(row.status) }}</template>
          </el-table-column>
          <el-table-column label="渠道" width="90">
            <template #default="{ row }">{{ row.channel === 'OFFLINE' ? '线下' : '微信' }}</template>
          </el-table-column>
          <el-table-column label="凭证 / 时间" min-width="220">
            <template #default="{ row }">
              <div>{{ (row.voucherUrls || []).join('；') || '—' }}</div>
              <div class="muted">{{ row.paidAt || '' }} {{ row.operatorName || '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <template v-if="row.status === 'WAIT'">
                <el-button link type="primary" @click="openPatch(row)">改金额</el-button>
                <el-button link type="primary" @click="openOffline(row)">线下收款</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel block">
        <h3>改价记录</h3>
        <el-empty v-if="order.priceLogs.length === 0" description="尚未改价" :image-size="64" />
        <el-timeline v-else>
          <el-timeline-item v-for="log in order.priceLogs" :key="log.id" :timestamp="log.createdAt">
            {{ log.operatorName }} 将{{ PRICE_FIELD_LABEL[log.field] || log.field }}
            从 {{ log.beforeAmount == null ? '未设定' : `¥${formatYuan(log.beforeAmount)}` }} 调整为 ¥{{ formatYuan(log.afterAmount) }}
            · {{ log.reason }}
          </el-timeline-item>
        </el-timeline>
      </section>
    </template>

    <el-dialog v-model="adjustVisible" title="改价" width="640px">
      <p class="muted">按行改单价和安装费，安装费只对上门安装行生效。整单调整可正可负，优惠填负数。原因会写入改价记录。</p>
      <el-table :data="lineDrafts" size="small">
        <el-table-column prop="name" label="商品" min-width="160" />
        <el-table-column label="单价（元）" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.unitYuan" :min="0" :precision="2" :step="10" />
          </template>
        </el-table-column>
        <el-table-column label="安装费（元）" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.installYuan"
              :min="0"
              :precision="2"
              :step="10"
              :value-on-clear="null"
              :placeholder="row.delivery === 'ON_SITE_INSTALL' ? '待定' : ''"
              :disabled="row.delivery !== 'ON_SITE_INSTALL'"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="120px" class="form-gap">
        <el-form-item label="整单调整（元）">
          <el-input-number v-model="adjustYuan" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="adjustReason" placeholder="例如：与客户微信沟通后优惠" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="freightVisible" title="设置运费" width="420px">
      <p class="muted">下单默认运费为 0。这里填写的是运费合计（元），不是增量。</p>
      <el-input-number v-model="freightYuan" :min="0" :precision="2" :step="10" />
      <template #footer>
        <el-button @click="freightVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFreight">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="splitVisible" title="拆成定金和尾款" width="480px">
      <p class="muted">关闭未付全款，改为两笔待付。金额由你填写，不按固定比例。</p>
      <p v-if="fullWait">当前未付全款 ¥{{ formatYuan(fullWait.amount) }}，两笔合计 ¥{{ formatYuan(splitSumFen) }}</p>
      <el-form label-width="80px">
        <el-form-item label="定金">
          <el-input-number v-model="depositYuan" :min="0.01" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="尾款">
          <el-input-number v-model="balanceYuan" :min="0.01" :precision="2" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSplit">拆分</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appendVisible" title="追加应付" width="480px">
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="appendType" style="width: 180px">
            <el-option v-for="(label, key) in PAYMENT_TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="appendTitle" />
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input-number v-model="appendYuan" :min="0.01" :precision="2" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appendVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAppend">追加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="patchVisible" title="修改未付金额" width="420px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="patchTitle" />
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input-number v-model="patchYuan" :min="0.01" :precision="2" :step="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="patchVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPatch">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offlineVisible" title="登记线下收款" width="480px">
      <p class="muted">一期只登记线下到账。微信支付在二期打开。</p>
      <el-form label-width="80px">
        <el-form-item label="凭证">
          <el-input v-model="voucherText" type="textarea" :rows="3" placeholder="转账单号或图片 URL，一行一条" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="offlineRemark" placeholder="例如：对公账户已到账" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOffline">确认收款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="shipVisible" title="按行发货" width="520px">
      <p v-if="shipItem">{{ shipItem.productName }} · {{ DELIVERY_LABEL[shipItem.deliveryMethod] }}</p>
      <el-form v-if="shipItem" label-width="100px">
        <template v-if="shipItem.deliveryMethod === 'EXPRESS'">
          <el-form-item label="承运商"><el-input v-model="shipForm.carrier" /></el-form-item>
          <el-form-item label="快递单号"><el-input v-model="shipForm.trackingNo" /></el-form-item>
        </template>
        <template v-else-if="shipItem.deliveryMethod === 'FREIGHT'">
          <el-form-item label="物流公司"><el-input v-model="shipForm.logisticsCompany" /></el-form-item>
          <el-form-item label="物流单号"><el-input v-model="shipForm.freightNo" /></el-form-item>
          <el-form-item label="车牌"><el-input v-model="shipForm.vehicleNo" /></el-form-item>
        </template>
        <template v-else-if="shipItem.deliveryMethod === 'HOME_DELIVERY'">
          <el-form-item label="送货人"><el-input v-model="shipForm.courierName" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="shipForm.courierMobile" /></el-form-item>
          <el-form-item label="预约时间"><el-input v-model="shipForm.appointAt" placeholder="2026-11-28 14:00:00" /></el-form-item>
        </template>
        <template v-else-if="shipItem.deliveryMethod === 'ON_SITE_INSTALL'">
          <el-form-item label="师傅"><el-input v-model="shipForm.courierName" /></el-form-item>
          <el-form-item label="电话"><el-input v-model="shipForm.courierMobile" /></el-form-item>
          <el-form-item label="上门时间"><el-input v-model="shipForm.appointAt" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="自提码"><el-input v-model="shipForm.pickupCode" placeholder="留空则自动生成" /></el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="shipForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="派师傅 / 定安装时间" width="480px">
      <p v-if="order && order.status !== 'PAID' && order.status !== 'FULFILLING'" class="muted">
        订单尚未付清。可以预派并占档期，付款结清后进入履约。
      </p>
      <el-form label-width="90px">
        <el-form-item label="师傅">
          <el-select v-model="assignTech" style="width: 220px">
            <el-option v-for="tech in technicians" :key="tech.id" :label="`${tech.name} · ${tech.region}`" :value="tech.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="assignDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="assignSlot" style="width: 140px">
            <el-option label="上午" value="上午" />
            <el-option label="下午" value="下午" />
            <el-option label="晚上" value="晚上" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="assignReason" placeholder="改期或改派时填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelVisible" title="取消订单" width="420px">
      <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="取消原因" />
      <template #footer>
        <el-button @click="cancelVisible = false">返回</el-button>
        <el-button type="danger" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 一张订单上的改价、运费、拆分应付、发货、派安装师傅、线下收款和取消。
 * 每个按钮调用 api/admin.ts 里对应的函数；Mock 下规则在 domain/order.ts。
 * 页面上的金额输入是元，提交前用 yuanToFen 换成分。后端还没有这些接口。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  adjustOrderPrice,
  assignOrder,
  cancelOrder as cancelOrderApi,
  createOrderPayments,
  createShipment,
  getOrder,
  listTechnicians,
  offlineConfirm,
  setOrderFreight,
  updatePayment,
} from '@/api/admin'
import type { AdminOrder, OrderItem, Payment, PaymentType, Technician } from '@/api/types'
import { fenToYuanNumber, formatYuan, yuanToFen } from '@/domain/money'
import { installFeeState } from '@/domain/order'
import {
  DELIVERY_LABEL,
  ORDER_STATUS_LABEL,
  PAYMENT_TYPE_LABEL,
  PRICE_FIELD_LABEL,
  deliveryText,
  orderTagType,
  paymentStatusText,
  paymentTypeText,
} from '@/domain/labels'
import { run } from '@/utils/feedback'

const route = useRoute()
const loading = ref(false)
const order = ref<AdminOrder | null>(null)
const technicians = ref<Technician[]>([])

const closed = computed(() => order.value?.status === 'CANCELLED' || order.value?.status === 'COMPLETED')
const fullWait = computed(() => order.value?.payments.find((item) => item.status === 'WAIT' && item.type === 'FULL'))
const canCancel = computed(
  () => !!order.value && order.value.status === 'PENDING_PAYMENT' && !order.value.payments.some((item) => item.status === 'SUCCESS'),
)
const splitSumFen = computed(() => yuanToFen(depositYuan.value || 0) + yuanToFen(balanceYuan.value || 0))

const adjustVisible = ref(false)
const adjustReason = ref('')
const adjustYuan = ref(0)
const lineDrafts = ref<
  { id: number; name: string; unitYuan: number; installYuan: number | null; delivery: OrderItem['deliveryMethod'] }[]
>([])
const feeState = computed(() => (order.value ? installFeeState(order.value) : ({ kind: 'none' } as const)))
const freightVisible = ref(false)
const freightYuan = ref(0)
const splitVisible = ref(false)
const depositYuan = ref<number>()
const balanceYuan = ref<number>()
const appendVisible = ref(false)
const appendType = ref<PaymentType>('FREIGHT')
const appendTitle = ref('运费补收')
const appendYuan = ref<number>()
const patchVisible = ref(false)
const patchTarget = ref<Payment | null>(null)
const patchTitle = ref('')
const patchYuan = ref(0)
const offlineVisible = ref(false)
const offlineTarget = ref<Payment | null>(null)
const voucherText = ref('')
const offlineRemark = ref('')
const shipVisible = ref(false)
const shipItem = ref<OrderItem | null>(null)
const shipForm = reactive({
  carrier: '顺丰',
  trackingNo: '',
  logisticsCompany: '',
  freightNo: '',
  vehicleNo: '',
  courierName: '',
  courierMobile: '',
  appointAt: '',
  pickupCode: '',
  remark: '',
})
const assignVisible = ref(false)
const assignItem = ref<OrderItem | null>(null)
const assignTech = ref<number>()
const assignDate = ref('')
const assignSlot = ref('上午')
const assignReason = ref('')
const cancelVisible = ref(false)
const cancelReason = ref('')

function shipmentText(item: OrderItem): string {
  const shipment = item.shipment
  if (!shipment) return '未发货'
  if (shipment.method === 'EXPRESS') return `${shipment.carrier || ''} ${shipment.trackingNo || ''}`.trim()
  if (shipment.method === 'FREIGHT') return `${shipment.logisticsCompany || ''} ${shipment.freightNo || shipment.vehicleNo || ''}`.trim()
  if (shipment.method === 'STORE_PICKUP') return `自提码 ${shipment.pickupCode || ''}`
  return `${shipment.courierName || ''} ${shipment.courierMobile || ''} ${shipment.appointAt || ''}`.trim()
}

function canShip(item: OrderItem): boolean {
  if (!order.value) return false
  if (order.value.status !== 'PAID' && order.value.status !== 'FULFILLING') return false
  return !item.shipment || item.shipment.status === 'CANCELLED'
}

async function load() {
  loading.value = true
  await run(async () => {
    order.value = await getOrder(Number(route.params.id))
  })
  loading.value = false
}

function openAdjust(forInstallFee = false) {
  if (!order.value) return
  adjustReason.value = forInstallFee ? '设定上门安装费' : ''
  adjustYuan.value = fenToYuanNumber(order.value.adjustAmount)
  lineDrafts.value = order.value.items.map((item) => ({
    id: item.id,
    name: `${item.productName} ${item.skuName}`,
    unitYuan: fenToYuanNumber(item.unitPrice),
    installYuan: item.installFee == null ? null : fenToYuanNumber(item.installFee),
    delivery: item.deliveryMethod,
  }))
  adjustVisible.value = true
}

function openFreight() {
  freightYuan.value = fenToYuanNumber(order.value?.freightAmount ?? 0)
  freightVisible.value = true
}

function openSplit() {
  depositYuan.value = undefined
  balanceYuan.value = undefined
  splitVisible.value = true
}

function openAppend() {
  appendType.value = 'FREIGHT'
  appendTitle.value = '运费补收'
  appendYuan.value = undefined
  appendVisible.value = true
}

function openPatch(payment: Payment) {
  patchTarget.value = payment
  patchTitle.value = payment.title
  patchYuan.value = fenToYuanNumber(payment.amount)
  patchVisible.value = true
}

function openOffline(payment: Payment) {
  offlineTarget.value = payment
  voucherText.value = ''
  offlineRemark.value = ''
  offlineVisible.value = true
}

function openShip(item: OrderItem) {
  shipItem.value = item
  shipForm.carrier = '顺丰'
  shipForm.trackingNo = ''
  shipForm.logisticsCompany = ''
  shipForm.freightNo = ''
  shipForm.vehicleNo = ''
  shipForm.courierName = item.technicianName || ''
  shipForm.courierMobile = ''
  shipForm.appointAt = item.installDate ? `${item.installDate} ${item.installSlot || ''}`.trim() : ''
  shipForm.pickupCode = ''
  shipForm.remark = ''
  shipVisible.value = true
}

function openAssign(item: OrderItem) {
  assignItem.value = item
  assignTech.value = item.technicianId
  assignDate.value = item.installDate || ''
  assignSlot.value = item.installSlot || '上午'
  assignReason.value = ''
  assignVisible.value = true
}

function openCancel() {
  cancelReason.value = ''
  cancelVisible.value = true
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制手机号')
  } catch {
    ElMessage.info(text)
  }
}

async function submitAdjust() {
  if (!order.value) return
  const ok = await run(async () => {
    order.value = await adjustOrderPrice(order.value!.id, {
      reason: adjustReason.value.trim(),
      adjustAmount: yuanToFen(adjustYuan.value || 0),
      lines: lineDrafts.value.map((line) => ({
        orderItemId: line.id,
        unitPrice: yuanToFen(line.unitYuan || 0),
        installFee:
          line.delivery === 'ON_SITE_INSTALL' && line.installYuan != null ? yuanToFen(line.installYuan) : undefined,
      })),
    })
  }, '已改价')
  if (ok) adjustVisible.value = false
}

async function submitFreight() {
  if (!order.value) return
  const ok = await run(async () => {
    order.value = await setOrderFreight(order.value!.id, { freightAmount: yuanToFen(freightYuan.value || 0) })
  }, '已更新运费')
  if (ok) freightVisible.value = false
}

async function submitSplit() {
  if (!order.value || depositYuan.value == null || balanceYuan.value == null) {
    ElMessage.error('请填写定金和尾款')
    return
  }
  const ok = await run(async () => {
    order.value = await createOrderPayments(order.value!.id, {
      mode: 'SPLIT',
      payments: [
        { type: 'DEPOSIT', title: '定金', amount: yuanToFen(depositYuan.value || 0) },
        { type: 'BALANCE', title: '尾款', amount: yuanToFen(balanceYuan.value || 0) },
      ],
    })
  }, '已拆分应付')
  if (ok) splitVisible.value = false
}

async function submitAppend() {
  if (!order.value || appendYuan.value == null) {
    ElMessage.error('请填写金额')
    return
  }
  const ok = await run(async () => {
    order.value = await createOrderPayments(order.value!.id, {
      mode: 'APPEND',
      payments: [{ type: appendType.value, title: appendTitle.value, amount: yuanToFen(appendYuan.value || 0) }],
    })
  }, '已追加应付')
  if (ok) appendVisible.value = false
}

async function submitPatch() {
  if (!patchTarget.value) return
  const ok = await run(async () => {
    await updatePayment(patchTarget.value!.id, { title: patchTitle.value, amount: yuanToFen(patchYuan.value || 0) })
    await load()
  }, '已修改应付')
  if (ok) patchVisible.value = false
}

async function submitOffline() {
  if (!offlineTarget.value) return
  const vouchers = voucherText.value.split('\n').map((line) => line.trim()).filter(Boolean)
  const ok = await run(async () => {
    await offlineConfirm(offlineTarget.value!.id, { voucherUrls: vouchers, remark: offlineRemark.value })
    await load()
  }, '已登记收款')
  if (ok) offlineVisible.value = false
}

async function submitShip() {
  if (!order.value || !shipItem.value) return
  const ok = await run(async () => {
    order.value = await createShipment(order.value!.id, {
      orderItemId: shipItem.value!.id,
      method: shipItem.value!.deliveryMethod,
      ...shipForm,
    })
  }, '已发货')
  if (ok) shipVisible.value = false
}

async function submitAssign() {
  if (!order.value || !assignItem.value || assignTech.value == null) {
    ElMessage.error('请选择师傅')
    return
  }
  const ok = await run(async () => {
    order.value = await assignOrder(order.value!.id, {
      orderItemId: assignItem.value!.id,
      technicianId: assignTech.value!,
      installDate: assignDate.value,
      installSlot: assignSlot.value,
      reason: assignReason.value || undefined,
    })
  }, '已安排师傅')
  if (ok) assignVisible.value = false
}

async function submitCancel() {
  if (!order.value) return
  const ok = await run(async () => {
    order.value = await cancelOrderApi(order.value!.id, { reason: cancelReason.value })
  }, '订单已取消')
  if (ok) cancelVisible.value = false
}

onMounted(() => {
  void load()
  void run(async () => {
    technicians.value = await listTechnicians()
  })
})
</script>

<style scoped>
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.metrics div {
  background: #fff;
  border: 1px solid var(--zb-line);
  border-radius: 12px;
  padding: 12px 14px;
}
.metrics span {
  color: var(--zb-muted);
  font-size: 12px;
}
.metrics b {
  display: block;
  margin-top: 4px;
  font-size: 16px;
}
.grid {
  display: grid;
  grid-template-columns: 1.7fr 0.8fr;
  gap: 12px;
}
.block {
  margin-top: 12px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.muted {
  color: var(--zb-muted);
  font-size: 12px;
  line-height: 1.6;
}
.pending {
  color: #b45309;
}
.remark {
  white-space: pre-wrap;
  background: var(--zb-tint);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
}
.form-gap {
  margin-top: 12px;
}
@media (max-width: 1100px) {
  .metrics,
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
