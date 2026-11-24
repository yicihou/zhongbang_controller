<template>
  <div>
    <div class="page-head">
      <div>
        <h2>工作台</h2>
        <p>待付款、待发货、待派工、待报价。数字来自订单 / 报修列表，不另设汇总接口。</p>
      </div>
    </div>
    <div class="stats" data-testid="dashboard-stats">
      <button class="stat" @click="go('/orders?status=PENDING_PAYMENT')">
        <span>待付款</span>
        <b>{{ stats.pendingPay }}</b>
        <em>含部分付款 {{ stats.partial }}</em>
      </button>
      <button class="stat" @click="go('/orders?status=PAID')">
        <span>待发货</span>
        <b>{{ stats.pendingShip }}</b>
        <em>已付款、尚未进入履约</em>
      </button>
      <button class="stat" @click="go('/technicians')">
        <span>待派工</span>
        <b>{{ stats.pendingAssign }}</b>
        <em>订单 {{ stats.assignOrders }} · 报修 {{ stats.assignRepairs }}</em>
      </button>
      <button class="stat" @click="go('/repairs?status=SUBMITTED')">
        <span>待报价</span>
        <b>{{ stats.pendingQuote }}</b>
        <em>已提交、尚未报价</em>
      </button>
    </div>
    <div class="cols">
      <section class="panel">
        <h3>待派工明细</h3>
        <el-table :data="assignRows" size="small" empty-text="暂无待派工">
          <el-table-column prop="kind" label="类型" width="80" />
          <el-table-column prop="no" label="单号" min-width="160" />
          <el-table-column prop="who" label="客户" width="100" />
          <el-table-column prop="what" label="内容" min-width="180" />
          <el-table-column label="" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="go(row.href)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
      <section class="panel">
        <h3>最近通知</h3>
        <div v-if="inbox.notices.length === 0" class="empty">暂无通知</div>
        <button v-for="item in inbox.notices.slice(0, 6)" :key="item.id" class="note" @click="openNotice(item)">
          <b>{{ item.title }}</b>
          <span>{{ item.body }}</span>
        </button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrders, listRepairs } from '@/api/admin'
import type { AdminNotice, AdminOrder, RepairTicket } from '@/api/types'
import { useInboxStore } from '@/stores/inbox'
import { run } from '@/utils/feedback'

interface AssignRow {
  kind: string
  no: string
  who: string
  what: string
  href: string
}

const router = useRouter()
const inbox = useInboxStore()
const stats = reactive({
  pendingPay: 0,
  partial: 0,
  pendingShip: 0,
  pendingAssign: 0,
  assignOrders: 0,
  assignRepairs: 0,
  pendingQuote: 0,
})
const assignRows = ref<AssignRow[]>([])

function go(path: string) {
  void router.push(path)
}

function openNotice(item: AdminNotice) {
  if (item.bizType === 'ORDER') void router.push(`/orders/${item.bizId}`)
  else if (item.bizType === 'REPAIR') void router.push(`/repairs/${item.bizId}`)
}

function orderNeedsAssign(order: AdminOrder): boolean {
  return order.items.some((item) => item.deliveryMethod === 'ON_SITE_INSTALL' && !item.technicianId)
}

function repairNeedsAssign(ticket: RepairTicket): boolean {
  return ticket.lines.some((line) => !line.technicianId)
}

onMounted(() => {
  void run(async () => {
    const [pending, partial, paid, fulfilling, submitted, repairPaid, confirmed] = await Promise.all([
      listOrders({ status: 'PENDING_PAYMENT', page: 1, size: 1 }),
      listOrders({ status: 'PARTIALLY_PAID', page: 1, size: 1 }),
      listOrders({ status: 'PAID', page: 1, size: 100 }),
      listOrders({ status: 'FULFILLING', page: 1, size: 100 }),
      listRepairs({ status: 'SUBMITTED', page: 1, size: 1 }),
      listRepairs({ status: 'PAID', page: 1, size: 100 }),
      listRepairs({ status: 'CONFIRMED', page: 1, size: 100 }),
    ])
    stats.partial = partial.total
    stats.pendingPay = pending.total + partial.total
    stats.pendingShip = paid.total
    stats.pendingQuote = submitted.total
    const rows: AssignRow[] = []
    for (const order of [...paid.list, ...fulfilling.list].filter(orderNeedsAssign)) {
      rows.push({
        kind: '订单',
        no: order.orderNo,
        who: order.customerName,
        what: '上门安装未派师傅',
        href: `/orders/${order.id}`,
      })
    }
    for (const ticket of [...repairPaid.list, ...confirmed.list].filter(repairNeedsAssign)) {
      rows.push({
        kind: '报修',
        no: ticket.ticketNo,
        who: ticket.customerName,
        what: '有行尚未派工',
        href: `/repairs/${ticket.id}`,
      })
    }
    assignRows.value = rows
    stats.assignOrders = rows.filter((row) => row.kind === '订单').length
    stats.assignRepairs = rows.filter((row) => row.kind === '报修').length
    stats.pendingAssign = rows.length
  })
})
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  text-align: left;
  background: #fff;
  border: 1px solid var(--zb-line);
  border-radius: 12px;
  padding: 16px 18px;
  cursor: pointer;
}
.stat:hover {
  border-color: var(--zb-edge);
  background: var(--zb-tint);
}
.stat span,
.stat em {
  display: block;
  color: var(--zb-muted);
  font-size: 13px;
  font-style: normal;
}
.stat b {
  display: block;
  margin: 6px 0 4px;
  font-size: 28px;
  color: var(--zb-red);
}
.cols {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 12px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.note {
  display: flex;
  flex-direction: column;
  width: 100%;
  text-align: left;
  border: 0;
  border-top: 1px solid var(--zb-line);
  background: transparent;
  padding: 10px 0;
  cursor: pointer;
}
.note span,
.empty {
  color: var(--zb-muted);
  font-size: 12px;
}
@media (max-width: 1100px) {
  .stats,
  .cols {
    grid-template-columns: 1fr;
  }
}
</style>
