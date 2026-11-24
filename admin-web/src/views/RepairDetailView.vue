<template>
  <div v-loading="loading" data-testid="repair-detail">
    <div class="page-head" v-if="ticket">
      <div>
        <h2>{{ ticket.ticketNo }}</h2>
        <p>{{ ticket.customerName }} · {{ ticket.contactMobile }} · 期望 {{ ticket.expectDate || '未填' }} {{ ticket.expectSlot || '' }}</p>
      </div>
      <div class="actions">
        <el-tag :type="repairTagType(ticket.status)" size="large" effect="light">{{ REPAIR_STATUS_LABEL[ticket.status] }}</el-tag>
        <el-button type="primary" :disabled="!canQuote" @click="openQuote">报价</el-button>
      </div>
    </div>

    <template v-if="ticket">
      <section class="panel">
        <h3>报修行</h3>
        <p class="muted">{{ ticket.remark || '无备注' }}</p>
        <el-table :data="ticket.lines" size="small">
          <el-table-column label="#" prop="lineNo" width="50" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag :type="row.source === 'EXTERNAL' ? 'warning' : 'info'" effect="plain">
                {{ row.source === 'EXTERNAL' ? '外购' : '本店' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="设备" min-width="180">
            <template #default="{ row }">
              <div>{{ row.brand }} {{ row.model }}</div>
              <div class="muted">{{ row.sn || '无机身码' }}{{ row.assetId ? ` · 档案 ${row.assetId}` : '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="故障" min-width="220">
            <template #default="{ row }">
              <div>{{ row.faultType }}</div>
              <div class="muted">{{ row.faultDesc }}</div>
              <div class="muted">照片 {{ row.images.length }} 张：{{ row.images.join('、') }}</div>
              <div v-if="row.purchaseChannel" class="muted">购买渠道 {{ row.purchaseChannel }}</div>
              <div v-if="row.purchaseProofUrl" class="muted">凭证 {{ row.purchaseProofUrl }}</div>
            </template>
          </el-table-column>
          <el-table-column label="报价" width="110">
            <template #default="{ row }">{{ row.lineQuoteAmount == null ? '—' : `¥${formatYuan(row.lineQuoteAmount)}` }}</template>
          </el-table-column>
          <el-table-column label="派工" min-width="180">
            <template #default="{ row }">
              <div>{{ row.technicianName || '未派' }}</div>
              <div class="muted">{{ row.appointAt || '' }} {{ row.workOrderId ? `工单 ${row.workOrderId}` : '' }}</div>
              <el-button link type="primary" :disabled="!canAssign" @click="openAssign(row)">派师傅</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel block">
        <h3>维修费</h3>
        <p class="muted">
          报价 ¥{{ formatYuan(ticket.quoteAmount || 0) }}
          · 应付 ¥{{ formatYuan(ticket.payableAmount || 0) }}
          · 已付 ¥{{ formatYuan(ticket.paidAmount || 0) }}
          · {{ ticket.inWarranty ? '保内' : '保外 / 未标记' }}
        </p>
        <el-table :data="ticket.payments" size="small" empty-text="还没有应付单">
          <el-table-column prop="title" label="标题" width="120" />
          <el-table-column label="金额" width="120">
            <template #default="{ row }"><span class="money">¥{{ formatYuan(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ paymentStatusText(row.status) }}</template>
          </el-table-column>
          <el-table-column label="凭证" min-width="180">
            <template #default="{ row }">{{ (row.voucherUrls || []).join('；') || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === 'WAIT'" link type="primary" @click="openOffline(row)">线下收款</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <el-dialog v-model="quoteVisible" title="报价" width="560px">
      <el-switch v-model="inWarranty" active-text="保内（0 元可派工）" inactive-text="保外" />
      <el-table :data="quoteLines" size="small" class="gap">
        <el-table-column prop="name" label="设备" min-width="180" />
        <el-table-column label="金额（元）" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.yuan" :min="0" :precision="2" :step="10" :disabled="inWarranty" />
          </template>
        </el-table-column>
      </el-table>
      <el-input v-model="quoteRemark" class="gap" placeholder="报价说明" />
      <template #footer>
        <el-button @click="quoteVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQuote">提交报价</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="按行派工" width="460px">
      <el-form label-width="90px">
        <el-form-item label="师傅">
          <el-select v-model="assignTech" style="width: 220px">
            <el-option v-for="tech in technicians" :key="tech.id" :label="`${tech.name} · ${tech.skills.join('/')}`" :value="tech.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="上门时间">
          <el-date-picker v-model="appointAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offlineVisible" title="登记维修费" width="460px">
      <el-input v-model="voucherText" type="textarea" :rows="3" placeholder="转账凭证，一行一条" />
      <el-input v-model="offlineRemark" class="gap" placeholder="备注" />
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOffline">确认收款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { assignRepair as assignRepairApi, getRepair, listTechnicians, offlineConfirm, quoteRepair as quoteRepairApi } from '@/api/admin'
import type { Payment, RepairLine, RepairTicket, Technician } from '@/api/types'
import { formatYuan, yuanToFen } from '@/domain/money'
import { REPAIR_STATUS_LABEL, paymentStatusText, repairTagType } from '@/domain/labels'
import { run } from '@/utils/feedback'

const route = useRoute()
const loading = ref(false)
const ticket = ref<RepairTicket | null>(null)
const technicians = ref<Technician[]>([])
const canQuote = computed(() => {
  const status = ticket.value?.status
  return status === 'SUBMITTED' || status === 'QUOTED' || status === 'PENDING_PAYMENT'
})
const canAssign = computed(() => {
  const status = ticket.value?.status
  return status === 'PAID' || status === 'CONFIRMED' || status === 'IN_SERVICE'
})

const quoteVisible = ref(false)
const inWarranty = ref(false)
const quoteRemark = ref('')
const quoteLines = ref<{ id: number; name: string; yuan: number }[]>([])
const assignVisible = ref(false)
const assignLine = ref<RepairLine | null>(null)
const assignTech = ref<number>()
const appointAt = ref('')
const offlineVisible = ref(false)
const offlineTarget = ref<Payment | null>(null)
const voucherText = ref('')
const offlineRemark = ref('')

async function load() {
  loading.value = true
  await run(async () => {
    ticket.value = await getRepair(Number(route.params.id))
  })
  loading.value = false
}

function openQuote() {
  if (!ticket.value) return
  inWarranty.value = !!ticket.value.inWarranty
  quoteRemark.value = ticket.value.remark || ''
  quoteLines.value = ticket.value.lines.map((line) => ({
    id: line.id,
    name: `${line.brand} ${line.model}`,
    yuan: (line.lineQuoteAmount ?? 0) / 100,
  }))
  quoteVisible.value = true
}

function openAssign(line: RepairLine) {
  assignLine.value = line
  assignTech.value = line.technicianId
  appointAt.value = line.appointAt || ''
  assignVisible.value = true
}

function openOffline(payment: Payment) {
  offlineTarget.value = payment
  voucherText.value = ''
  offlineRemark.value = ''
  offlineVisible.value = true
}

async function submitQuote() {
  if (!ticket.value) return
  const ok = await run(async () => {
    ticket.value = await quoteRepairApi(ticket.value!.id, {
      inWarranty: inWarranty.value,
      remark: quoteRemark.value,
      lines: quoteLines.value.map((line) => ({
        lineId: line.id,
        lineQuoteAmount: inWarranty.value ? 0 : yuanToFen(line.yuan || 0),
      })),
    })
  }, '已报价')
  if (ok) quoteVisible.value = false
}

async function submitAssign() {
  if (!ticket.value || !assignLine.value || assignTech.value == null || !appointAt.value) {
    ElMessage.error('请选择师傅和上门时间')
    return
  }
  const ok = await run(async () => {
    ticket.value = await assignRepairApi(ticket.value!.id, {
      lineId: assignLine.value!.id,
      technicianId: assignTech.value!,
      appointAt: appointAt.value,
    })
  }, '已派工')
  if (ok) assignVisible.value = false
}

async function submitOffline() {
  if (!offlineTarget.value) return
  const vouchers = voucherText.value.split('\n').map((line) => line.trim()).filter(Boolean)
  const ok = await run(async () => {
    await offlineConfirm(offlineTarget.value!.id, { voucherUrls: vouchers, remark: offlineRemark.value })
    await load()
  }, '已登记维修费')
  if (ok) offlineVisible.value = false
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
  gap: 8px;
  align-items: center;
}
.block {
  margin-top: 12px;
}
h3 {
  margin: 0 0 8px;
  font-size: 15px;
}
.muted {
  color: var(--zb-muted);
  font-size: 12px;
  line-height: 1.6;
}
.gap {
  margin-top: 12px;
}
</style>
