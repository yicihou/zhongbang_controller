<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="500px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="payment">
      <p class="muted">
        应付「{{ payment.title }}」剩余 <span class="money">¥{{ formatYuan(payment.amount) }}</span
        >。一期只登记线下到账，微信支付在二期打开。
      </p>
      <el-form label-width="110px" data-testid="offline-form">
        <el-form-item label="本次收款金额" required :error="amountError ?? ''">
          <el-input-number
            v-model="yuan"
            :min="0"
            :max="maxYuan"
            :precision="2"
            :step="100"
            controls-position="right"
            data-testid="offline-amount"
          />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="收款方式" required>
          <el-radio-group v-model="method" data-testid="offline-method">
            <el-radio-button v-for="item in OFFLINE_METHOD_OPTIONS" :key="item" :value="item">
              {{ OFFLINE_METHOD_LABEL[item] }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="收款时间" required>
          <el-date-picker
            v-model="paidAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="isFuture"
            :clearable="false"
            data-testid="offline-paid-at"
          />
        </el-form-item>
        <el-form-item label="凭证">
          <el-input
            v-model="voucherText"
            type="textarea"
            :rows="2"
            placeholder="转账单号或图片 URL，一行一条（选填）"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" placeholder="例如：对公账户已到账" />
        </el-form-item>
      </el-form>
      <el-alert v-if="partialRest > 0" type="warning" :closable="false" show-icon data-testid="offline-partial-hint">
        部分收款：本笔按 ¥{{ formatYuan(amountFen) }} 记为已收，剩余 ¥{{ formatYuan(partialRest) }} 自动生成一笔待付「{{
          splitTitle(payment.title)
        }}」。
      </el-alert>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!!amountError"
        data-testid="offline-submit"
        @click="submit"
      >
        确认收款
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { offlineConfirm } from '@/api/admin'
import type { OfflinePayMethod, Payment } from '@/api/types'
import { formatDateTime } from '@/domain/clock'
import { OFFLINE_METHOD_LABEL, OFFLINE_METHOD_OPTIONS } from '@/domain/labels'
import { fenToYuanNumber, formatYuan, yuanToFen } from '@/domain/money'
import { offlineAmountError, splitTitle } from '@/domain/order'
import { run } from '@/utils/feedback'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    payment: Pick<Payment, 'id' | 'title' | 'amount'> | null
    title?: string
  }>(),
  { title: '登记线下收款' },
)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: [payment: Payment]
}>()

const yuan = ref<number | undefined>()
const method = ref<OfflinePayMethod>('BANK_TRANSFER')
const paidAt = ref('')
const voucherText = ref('')
const remark = ref('')
const saving = ref(false)

const maxYuan = computed(() => fenToYuanNumber(props.payment?.amount ?? 0))
const amountFen = computed(() => (yuan.value == null ? 0 : yuanToFen(yuan.value)))
const amountError = computed(() =>
  props.payment ? offlineAmountError(yuan.value == null ? null : amountFen.value, props.payment.amount) : null,
)
const partialRest = computed(() => (props.payment && !amountError.value ? props.payment.amount - amountFen.value : 0))

watch(
  () => [props.modelValue, props.payment?.id] as const,
  ([visible]) => {
    if (!visible || !props.payment) return
    yuan.value = fenToYuanNumber(props.payment.amount)
    method.value = 'BANK_TRANSFER'
    paidAt.value = formatDateTime()
    voucherText.value = ''
    remark.value = ''
  },
  { immediate: true },
)

function isFuture(date: Date): boolean {
  return date.getTime() > Date.now()
}

async function submit() {
  if (!props.payment || amountError.value) return
  saving.value = true
  let saved: Payment | undefined
  const ok = await run(
    async () => {
      saved = await offlineConfirm(props.payment!.id, {
        amount: amountFen.value,
        method: method.value,
        paidAt: paidAt.value,
        voucherUrls: voucherText.value
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean),
        remark: remark.value,
      })
    },
    partialRest.value > 0 ? `已登记部分收款，剩余 ¥${formatYuan(partialRest.value)} 待付` : '已登记收款',
  )
  saving.value = false
  if (ok && saved) {
    emit('update:modelValue', false)
    emit('done', saved)
  }
}
</script>

<style scoped>
.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
.muted {
  margin-top: 0;
}
</style>
