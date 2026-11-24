<template>
  <div>
    <div class="page-head">
      <div>
        <h2>师傅与排期</h2>
        <p>师傅档案与 technician_schedule：一人、一天、一个时段，容量与已占用。</p>
      </div>
      <div class="nav">
        <el-button @click="shift(-7)">上一周</el-button>
        <span>{{ days[0] }} 起</span>
        <el-button @click="shift(7)">下一周</el-button>
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
      <h3>排期</h3>
      <el-table :data="grid" size="small" data-testid="schedule-table">
        <el-table-column prop="name" label="师傅" width="100" fixed />
        <el-table-column v-for="day in days" :key="day" :label="`${day.slice(5)} 周${weekdayLabel(day)}`" min-width="150">
          <template #default="{ row }">
            <div v-for="slot in SLOTS" :key="slot" class="slot" :class="{ busy: !!cell(row.id, day, slot) }">
              <b>{{ slot }}</b>
              <span v-if="cell(row.id, day, slot)">
                {{ cell(row.id, day, slot)?.used }}/{{ cell(row.id, day, slot)?.capacity }}
                <em v-if="cell(row.id, day, slot)?.customerName">{{ cell(row.id, day, slot)?.customerName }}</em>
              </span>
              <span v-else class="free">空闲</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listSchedules, listTechnicians } from '@/api/admin'
import type { Technician, TechnicianSchedule } from '@/api/types'
import { addDays, todayShanghai, weekdayLabel } from '@/domain/clock'
import { run } from '@/utils/feedback'

const SLOTS = ['上午', '下午', '晚上']
const technicians = ref<Technician[]>([])
const schedules = ref<TechnicianSchedule[]>([])
const start = ref(todayShanghai())
const days = computed(() => Array.from({ length: 7 }, (_, index) => addDays(start.value, index)))
const grid = computed(() => technicians.value)

function cell(technicianId: number, date: string, slot: string): TechnicianSchedule | undefined {
  return schedules.value.find(
    (row) => row.technicianId === technicianId && row.serviceDate === date && row.timeSlot === slot,
  )
}

async function load() {
  const [from, to] = [days.value[0], days.value[6]]
  await run(async () => {
    technicians.value = await listTechnicians()
    schedules.value = await listSchedules({ from, to })
  })
}

function shift(delta: number) {
  start.value = addDays(start.value, delta)
  void load()
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
.block {
  margin-top: 12px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.slot {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
  padding: 3px 0;
  color: var(--zb-muted);
}
.slot.busy {
  color: var(--zb-red);
}
.slot em {
  font-style: normal;
  margin-left: 4px;
}
.free {
  color: #b0b6bf;
}
</style>
