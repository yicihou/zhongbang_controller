<template>
  <div>
    <div class="page-head">
      <h2>师傅与排期</h2>
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
        <div class="schedule-controls">
          <h3>排期</h3>
          <el-date-picker
            v-model="selectedDate"
            class="schedule-date"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            aria-label="选择排期日期"
            :editable="false"
            :clearable="false"
          />
          <el-radio-group v-model="view" aria-label="排期视图">
            <el-radio-button value="week">周</el-radio-button>
            <el-radio-button value="month">月</el-radio-button>
          </el-radio-group>
          <el-button-group>
            <el-button @click="shiftPeriod(-1)">上一{{ periodLabel }}</el-button>
            <el-button @click="shiftPeriod(1)">下一{{ periodLabel }}</el-button>
          </el-button-group>
          <el-button @click="goToday">今天</el-button>
        </div>
        <div class="legend">
          <span><i class="bar free" />空闲</span>
          <span><i class="bar some" />有单</span>
          <span><i class="bar full" />排满</span>
        </div>
      </div>
      <div v-if="!loadingSchedules && !schedulesAvailable" class="schedule-error">
        <span>排期加载失败</span>
        <el-button link type="primary" @click="loadSchedules">重试</el-button>
      </div>
      <div v-else ref="scheduleScroll" v-loading="loadingSchedules" class="schedule-scroll" :aria-busy="loadingSchedules">
        <div class="grid" :class="{ 'week-grid': view === 'week' }" :style="{ '--schedule-days': days.length }">
          <div class="cell head corner">师傅</div>
          <div
            v-for="day in days"
            :key="`h-${day}`"
            class="cell head day-head"
            :class="{ weekend: isWeekend(day), selected: day === selectedDate }"
            :aria-current="day === today ? 'date' : undefined"
          >
            <time :datetime="day">{{ view === 'week' ? day.slice(5).replace('-', '/') : Number(day.slice(8)) }}</time>
            <span>{{ weekdayLabel(day) }}</span>
          </div>

          <template v-for="tech in technicians" :key="tech.id">
            <button type="button" class="cell tech" :title="`给 ${tech.name} 排单`" @click="openAssign(tech, selectedDate)">
              {{ tech.name }}
            </button>
            <button
              v-for="day in days"
              :key="`${tech.id}-${day}`"
              type="button"
              class="cell day"
              :class="{ weekend: isWeekend(day), today: day === today, selected: day === selectedDate }"
              :aria-label="schedulesAvailable ? tooltip(tech, day) : `${tech.name} · ${day}`"
              @click="openAssign(tech, day)"
            >
              <el-tooltip v-if="schedulesAvailable" :content="tooltip(tech, day)" placement="top" :show-after="150">
                <div class="slots">
                  <i
                    v-for="slot in SLOTS"
                    :key="slot"
                    class="bar"
                    :class="slotClass(tech.id, day, slot)"
                  />
                </div>
              </el-tooltip>
            </button>
          </template>
        </div>
      </div>
    </section>

    <el-dialog v-model="assignVisible" :title="assignTitle" width="520px">
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
      <p v-if="!pending.length" class="muted">暂无待排安装单</p>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!form.orderItemId" @click="submitAssign">保存排单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { assignOrder, listPendingInstalls, listSchedules, listTechnicians } from '@/api/admin'
import type { PendingInstall, Technician, TechnicianSchedule } from '@/api/types'
import { todayShanghai, weekdayLabel } from '@/domain/clock'
import { scheduleDays, shiftScheduleDate, type ScheduleView } from '@/domain/schedule-calendar'
import { run } from '@/utils/feedback'

const SLOTS = ['上午', '下午', '晚上']
const technicians = ref<Technician[]>([])
const schedules = ref<TechnicianSchedule[]>([])
const pending = ref<PendingInstall[]>([])
const today = ref(todayShanghai())
const selectedDate = ref(today.value)
const view = ref<ScheduleView>('week')
const periodLabel = computed(() => view.value === 'week' ? '周' : '月')
const days = computed(() => scheduleDays(selectedDate.value, view.value))
const firstDay = computed(() => days.value[0])
const lastDay = computed(() => days.value.at(-1))
const scheduleScroll = ref<HTMLDivElement>()
const loadingSchedules = ref(false)
const schedulesAvailable = ref(false)
let scheduleRequestId = 0

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

async function loadSchedules() {
  const requestId = ++scheduleRequestId
  const from = firstDay.value
  const to = lastDay.value
  loadingSchedules.value = true
  schedulesAvailable.value = false
  await run(async () => {
    try {
      const rows = await listSchedules({ from, to })
      if (requestId === scheduleRequestId) {
        schedules.value = rows
        schedulesAvailable.value = true
      }
    } catch (error) {
      if (requestId === scheduleRequestId) throw error
    }
  })
  if (requestId === scheduleRequestId) loadingSchedules.value = false
}

async function loadPending() {
  await run(async () => {
    pending.value = await listPendingInstalls()
  })
}

function shiftPeriod(direction: -1 | 1) {
  selectedDate.value = shiftScheduleDate(selectedDate.value, view.value, direction)
}

function goToday() {
  today.value = todayShanghai()
  selectedDate.value = today.value
}

const assignVisible = ref(false)
const form = reactive({
  technicianId: 0,
  installDate: today.value,
  installSlot: '上午',
  orderItemId: undefined as number | undefined,
  reason: '',
})

const assignTitle = computed(() => {
  const tech = technicians.value.find((item) => item.id === form.technicianId)
  return `排单 · ${tech ? tech.name : ''} · ${form.installDate || ''}`
})

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
  await Promise.all([loadSchedules(), loadPending()])
}

watch([firstDay, lastDay], loadSchedules, { immediate: true })

watch([selectedDate, view, scheduleScroll], () => {
  const viewport = scheduleScroll.value
  const header = viewport?.querySelector('.day-head.selected')
  if (!viewport || !header) return

  const dayBounds = header.getBoundingClientRect()
  const viewportBounds = viewport.getBoundingClientRect()
  if (dayBounds.left < viewportBounds.left) {
    viewport.scrollLeft += dayBounds.left - viewportBounds.left
  } else if (dayBounds.right > viewportBounds.right) {
    viewport.scrollLeft += dayBounds.right - viewportBounds.right
  }
}, { flush: 'post' })

onMounted(() => {
  void run(async () => {
    technicians.value = await listTechnicians()
  })
  void loadPending()
})
</script>

<style scoped>
.schedule-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.schedule-date.el-date-editor {
  --el-date-editor-width: 146px;
}
.block {
  margin-top: 12px;
}
.panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
.schedule-error {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--zb-muted);
}
.schedule-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}
.grid {
  display: grid;
  grid-template-columns: 88px repeat(var(--schedule-days), 34px);
  gap: 2px;
  width: max-content;
}
.week-grid {
  grid-template-columns: 88px repeat(var(--schedule-days), minmax(46px, 1fr));
  width: 100%;
  min-width: 424px;
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
.head time {
  font-size: 12px;
  font-weight: 700;
  color: var(--zb-text, #1f2329);
}
.corner {
  justify-content: flex-start;
  padding-left: 4px;
}
.day-head.weekend time {
  color: var(--zb-red);
}
.day-head.selected,
.day.selected {
  background: var(--zb-tint);
}
.day-head.selected {
  border-radius: 4px;
  box-shadow: inset 0 -2px var(--zb-red);
}
.tech,
.day {
  border: 0;
  padding: 0;
  background: transparent;
  font-family: inherit;
  color: inherit;
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
.day.weekend:not(.selected) {
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
