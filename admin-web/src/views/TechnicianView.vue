<template>
  <div>
    <div class="page-head">
      <div>
        <h2>师傅与排期</h2>
        <p>
          一行一个师傅，一天一格，整月一眼看完。格子里的小条是当天上午、下午、晚上三段：
          灰是空闲，橙是有人，红是排满。点格子或点师傅名字直接排单。
        </p>
      </div>
      <div class="nav">
        <el-button @click="shiftMonth(-1)">上一月</el-button>
        <span class="month">{{ monthLabel }}</span>
        <el-button @click="shiftMonth(1)">下一月</el-button>
        <el-button @click="goToday">回到本月</el-button>
      </div>
    </div>

    <section class="panel">
      <h3>师傅</h3>
      <el-table :data="technicians" size="small" data-testid="tech-table">
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="mobile" label="手机" width="140" />
        <el-table-column label="技能" min-width="180">
          <template #default="{ row }">{{ row.skills.join('、') }}</template>
        </el-table-column>
        <el-table-column prop="region" label="服务区域" width="120" />
        <el-table-column prop="rating" label="评分" width="80" />
      </el-table>
    </section>

    <section class="panel block">
      <div class="panel-head">
        <h3>排期 · {{ monthLabel }}</h3>
        <div class="legend">
          <span><i class="bar free" />空闲</span>
          <span><i class="bar some" />有单</span>
          <span><i class="bar full" />排满</span>
        </div>
      </div>
      <div class="month-scroll">
        <div class="grid" :style="{ gridTemplateColumns: `88px repeat(${days.length}, 34px)` }">
          <div class="cell head corner">师傅</div>
          <div
            v-for="day in days"
            :key="`h-${day}`"
            class="cell head day-head"
            :class="{ weekend: isWeekend(day), today: day === today }"
          >
            <b>{{ Number(day.slice(8)) }}</b>
            <span>{{ weekdayLabel(day) }}</span>
          </div>

          <template v-for="tech in technicians" :key="tech.id">
            <div class="cell tech" :class="{ today: false }" :title="`给 ${tech.name} 排单`" @click="openAssign(tech, today)">
              {{ tech.name }}
            </div>
            <div
              v-for="day in days"
              :key="`${tech.id}-${day}`"
              class="cell day"
              :class="{ weekend: isWeekend(day), today: day === today, clickable: true }"
              @click="openAssign(tech, day)"
            >
              <el-tooltip :content="tooltip(tech, day)" placement="top" :show-after="150">
                <div class="slots">
                  <i
                    v-for="slot in SLOTS"
                    :key="slot"
                    class="bar"
                    :class="slotClass(tech.id, day, slot)"
                  />
                </div>
              </el-tooltip>
            </div>
          </template>
        </div>
      </div>
    </section>

    <el-dialog v-model="assignVisible" :title="assignTitle" width="520px">
      <p class="muted">
        在这里直接派工，不用先跳去订单详情。派完档期和订单上的安装师傅会一起更新。
      </p>
      <el-form label-width="80px">
        <el-form-item label="师傅">
          <el-select v-model="form.technicianId" style="width: 100%">
            <el-option v-for="tech in technicians" :key="tech.id" :label="`${tech.name} · ${tech.region}`" :value="tech.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.installDate" type="date" value-format="YYYY-MM-DD" :clearable="false" />
        </el-form-item>
        <el-form-item label="时段">
          <el-select v-model="form.installSlot" style="width: 140px">
            <el-option v-for="slot in SLOTS" :key="slot" :label="slot" :value="slot" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单">
          <el-select
            v-model="form.orderItemId"
            filterable
            clearable
            placeholder="选择还没排师傅的安装单"
            style="width: 100%"
          >
            <el-option
              v-for="item in pending"
              :key="item.orderItemId"
              :label="`${item.orderNo} · ${item.customerName} · ${item.productName}`"
              :value="item.orderItemId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" placeholder="改期或改派时填写，正常排单可留空" />
        </el-form-item>
      </el-form>
      <p v-if="!pending.length" class="muted">现在没有待排的安装单。等客户下单选了上门安装，这里就会出现。</p>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!form.orderItemId" @click="submitAssign">保存排单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 师傅名单与整月排期。改版的起因是原来的 7 天宽表一屏看不全，也不好直接派工。
 * 现在：一行一个师傅、一天一格，格子里的三条小杠是上午/下午/晚上，点格子或名字就弹排单窗口。
 * 排单走 POST /api/admin/orders/{id}/assign，档期由后端写，这里只刷新。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { assignOrder, listPendingInstalls, listSchedules, listTechnicians } from '@/api/admin'
import type { PendingInstall, Technician, TechnicianSchedule } from '@/api/types'
import { addDays, todayShanghai, weekdayLabel } from '@/domain/clock'
import { run } from '@/utils/feedback'

const SLOTS = ['上午', '下午', '晚上']
const technicians = ref<Technician[]>([])
const schedules = ref<TechnicianSchedule[]>([])
const pending = ref<PendingInstall[]>([])
const today = todayShanghai()
/** 当前显示哪个月，存当月 1 号。 */
const monthStart = ref(today.slice(0, 8) + '01')

const monthLabel = computed(() => monthStart.value.slice(0, 7))
const days = computed(() => {
  const list: string[] = []
  let cursor = monthStart.value
  // 一直排到月底：只要下一天还在同一个月就继续。
  while (cursor.slice(0, 7) === monthLabel.value) {
    list.push(cursor)
    cursor = addDays(cursor, 1)
  }
  return list
})

function isWeekend(day: string): boolean {
  const label = weekdayLabel(day)
  return label === '六' || label === '日'
}

function cell(technicianId: number, date: string, slot: string): TechnicianSchedule | undefined {
  return schedules.value.find(
    (row) => row.technicianId === technicianId && row.serviceDate === date && row.timeSlot === slot,
  )
}

/** 一格三段的颜色：没有记录或没占用是空闲，占到一半是有单，满了是红。 */
function slotClass(technicianId: number, date: string, slot: string): string {
  const row = cell(technicianId, date, slot)
  if (!row || !row.used) return 'free'
  return row.used >= row.capacity ? 'full' : 'some'
}

function tooltip(tech: Technician, day: string): string {
  const parts = SLOTS.map((slot) => {
    const row = cell(tech.id, day, slot)
    if (!row || !row.used) return `${slot} 空闲`
    const who = row.customerName ? `（${row.customerName}）` : ''
    return `${slot} ${row.used}/${row.capacity}${who}`
  })
  return `${tech.name} · ${day} ${parts.join('，')}`
}

async function load() {
  await run(async () => {
    technicians.value = await listTechnicians()
    const from = days.value[0]
    const to = days.value[days.value.length - 1]
    schedules.value = await listSchedules({ from, to })
    pending.value = await listPendingInstalls()
  })
}

function shiftMonth(delta: number) {
  const [year, month] = monthLabel.value.split('-').map(Number)
  const target = new Date(Date.UTC(year, (month || 1) - 1 + delta, 1))
  monthStart.value = target.toISOString().slice(0, 10)
  void load()
}

function goToday() {
  monthStart.value = today.slice(0, 8) + '01'
  void load()
}

const assignVisible = ref(false)
const form = reactive({
  technicianId: 0,
  installDate: today,
  installSlot: '上午',
  orderItemId: undefined as number | undefined,
  reason: '',
})

const assignTitle = computed(() => {
  const tech = technicians.value.find((item) => item.id === form.technicianId)
  return `排单 · ${tech ? tech.name : ''} · ${form.installDate || ''}`
})

/** 点师傅名字用今天，点某一格用那一天。 */
function openAssign(tech: Technician, day: string) {
  form.technicianId = tech.id
  form.installDate = day
  form.installSlot = '上午'
  form.orderItemId = undefined
  form.reason = ''
  assignVisible.value = true
}

async function submitAssign() {
  const item = pending.value.find((row) => row.orderItemId === form.orderItemId)
  if (!item) return
  const ok = await run(async () => {
    await assignOrder(item.orderId, {
      orderItemId: item.orderItemId,
      technicianId: form.technicianId,
      installDate: form.installDate,
      installSlot: form.installSlot,
      reason: form.reason.trim() || undefined,
    })
  })
  if (!ok) return
  assignVisible.value = false
  await load()
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.nav {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--zb-muted);
}
.month {
  font-weight: 600;
  color: var(--zb-text, #1f2329);
  min-width: 78px;
  text-align: center;
}
.block {
  margin-top: 12px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
h3 {
  margin: 0;
  font-size: 15px;
}
.legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--zb-muted);
}
.legend span {
  display: flex;
  align-items: center;
  gap: 4px;
}
.month-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}
.grid {
  display: grid;
  gap: 2px;
  width: max-content;
}
.cell {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.head {
  color: var(--zb-muted);
  padding: 4px 0;
  flex-direction: column;
  gap: 1px;
}
.head b {
  font-size: 12px;
  color: var(--zb-text, #1f2329);
}
.corner {
  justify-content: flex-start;
  padding-left: 4px;
}
.day-head.weekend b {
  color: var(--zb-red);
}
.tech {
  justify-content: flex-start;
  padding-left: 4px;
  cursor: pointer;
  color: var(--zb-red);
  border-radius: 4px;
}
.tech:hover {
  background: var(--zb-red-soft, #fdecec);
}
.day {
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
}
.day.weekend {
  background: #fafafa;
}
.day.today {
  outline: 1px solid var(--zb-red);
  outline-offset: -1px;
}
.day:hover {
  background: var(--zb-red-soft, #fdecec);
}
.slots {
  display: flex;
  gap: 2px;
}
.bar {
  display: block;
  width: 6px;
  height: 14px;
  border-radius: 2px;
  background: #eceff3;
}
.bar.some {
  background: #f0a020;
}
.bar.full {
  background: var(--zb-red);
}
.muted {
  color: var(--zb-muted);
  font-size: 12px;
}
</style>
