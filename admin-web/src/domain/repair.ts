/**
 * 报修规则，同样只给 Mock 用，后端 aftersale 包是空的。
 * 报价：已取消、已完成、维修中不能再报。保内或合计为 0 时关掉待付并变成可派工（CONFIRMED）。
 * 保外则生成或更新一笔 type=REPAIR 的待付。派工只允许已付款、可派工或维修中，并占用师傅档期。
 */
import type {
  OfflineConfirmRequest,
  RepairAssignRequest,
  RepairQuoteRequest,
  RepairTicket,
  Technician,
  TechnicianSchedule,
} from '@/api/types'
import { slotFromAppoint } from './clock'
import { ApiError } from './errors'
import { buildPayment, confirmOffline, type IdSource, cloneData } from './order'

function paidOf(ticket: RepairTicket): number {
  return ticket.payments.filter((p) => p.status === 'SUCCESS').reduce((sum, p) => sum + p.amount, 0)
}

export function syncRepairPayment(ticket: RepairTicket): void {
  ticket.paidAmount = paidOf(ticket)
  if (ticket.status === 'CANCELLED' || ticket.status === 'DONE' || ticket.status === 'IN_SERVICE' || ticket.status === 'CONFIRMED') {
    return
  }
  const waits = ticket.payments.some((p) => p.status === 'WAIT')
  const payable = ticket.payableAmount ?? 0
  if (!waits && (ticket.paidAmount ?? 0) >= payable && payable > 0) {
    ticket.status = 'PAID'
  }
}

export function quoteRepair(ticket: RepairTicket, req: RepairQuoteRequest, now: string, ids: IdSource): RepairTicket {
  if (ticket.status === 'CANCELLED' || ticket.status === 'DONE' || ticket.status === 'IN_SERVICE') {
    throw new ApiError(3003, '当前状态不允许报价')
  }
  if (!req.lines?.length) throw new ApiError(1001, '请填写各行报价')
  const next = cloneData(ticket)
  for (const line of req.lines) {
    const row = next.lines.find((item) => item.id === line.lineId)
    if (!row) throw new ApiError(4040, '报修行不存在')
    if (line.lineQuoteAmount < 0) throw new ApiError(1001, '报价不能为负')
    row.lineQuoteAmount = line.lineQuoteAmount
  }
  const total = next.lines.reduce((sum, line) => sum + (line.lineQuoteAmount ?? 0), 0)
  next.quoteAmount = total
  next.inWarranty = req.inWarranty
  if (req.remark != null) next.remark = req.remark
  next.updatedAt = now
  if (req.inWarranty || total === 0) {
    next.payableAmount = 0
    for (const payment of next.payments) {
      if (payment.status === 'WAIT') {
        payment.status = 'CLOSED'
        payment.updatedAt = now
      }
    }
    next.paidAmount = paidOf(next)
    next.status = 'CONFIRMED'
    return next
  }
  next.payableAmount = total
  const waiting = next.payments.find((p) => p.status === 'WAIT' && p.type === 'REPAIR')
  if (waiting) {
    waiting.amount = total
    waiting.title = '维修费'
    waiting.updatedAt = now
  } else {
    const sortNo = next.payments.reduce((max, p) => Math.max(max, p.sortNo), 0) + 1
    next.payments.push(
      buildPayment({
        ids,
        bizType: 'REPAIR',
        bizId: next.id,
        bizNo: next.ticketNo,
        draft: { type: 'REPAIR', title: '维修费', amount: total, channel: 'OFFLINE' },
        sortNo,
        now,
      }),
    )
  }
  next.paidAmount = paidOf(next)
  const stillWait = next.payments.some((p) => p.status === 'WAIT')
  next.status = !stillWait && next.paidAmount >= total ? 'PAID' : 'PENDING_PAYMENT'
  return next
}

export function offlineConfirmRepair(
  ticket: RepairTicket,
  paymentId: number,
  req: OfflineConfirmRequest,
  operator: { name: string; id: number },
  now: string,
  ids: IdSource,
): RepairTicket {
  if (ticket.status === 'CANCELLED') throw new ApiError(3003, '已取消报修单不能登记收款')
  const next = cloneData(ticket)
  confirmOffline(next.payments, paymentId, next.ticketNo, req, operator, now, ids)
  next.updatedAt = now
  syncRepairPayment(next)
  return next
}

export function assignRepair(
  ticket: RepairTicket,
  schedules: TechnicianSchedule[],
  technicians: Technician[],
  req: RepairAssignRequest,
  now: string,
  ids: IdSource,
): { ticket: RepairTicket; schedules: TechnicianSchedule[] } {
  if (ticket.status !== 'PAID' && ticket.status !== 'CONFIRMED' && ticket.status !== 'IN_SERVICE') {
    throw new ApiError(3003, '报价确认或付款后才能派工')
  }
  const technician = technicians.find((item) => item.id === req.technicianId)
  if (!technician) throw new ApiError(4040, '师傅不存在')
  if (!req.appointAt?.trim()) throw new ApiError(1001, '请填写上门时间')
  const next = cloneData(ticket)
  const nextSchedules = cloneData(schedules)
  const line = next.lines.find((item) => item.id === req.lineId)
  if (!line) throw new ApiError(4040, '报修行不存在')
  const { date, slot } = slotFromAppoint(req.appointAt)
  const previous = line.appointAt ? slotFromAppoint(line.appointAt) : undefined
  const same =
    line.technicianId === req.technicianId && previous?.date === date && previous?.slot === slot
  if (!same) {
    if (line.technicianId && previous) {
      const old = nextSchedules.find(
        (row) =>
          row.technicianId === line.technicianId &&
          row.serviceDate === previous.date &&
          row.timeSlot === previous.slot,
      )
      if (old && old.used > 0) old.used -= 1
    }
    let row = nextSchedules.find(
      (item) => item.technicianId === req.technicianId && item.serviceDate === date && item.timeSlot === slot,
    )
    if (!row) {
      row = {
        id: ids.next(),
        technicianId: req.technicianId,
        serviceDate: date,
        timeSlot: slot,
        capacity: 2,
        used: 0,
        orderNo: next.ticketNo,
        customerName: next.customerName,
      }
      nextSchedules.push(row)
    }
    if (row.used >= row.capacity) throw new ApiError(3002, '师傅档期冲突')
    row.used += 1
    if (!row.orderNo) row.orderNo = next.ticketNo
    if (!row.customerName) row.customerName = next.customerName
  }
  line.technicianId = technician.id
  line.technicianName = technician.name
  line.appointAt = req.appointAt
  if (!line.workOrderId) line.workOrderId = ids.next()
  next.status = 'IN_SERVICE'
  next.updatedAt = now
  return { ticket: next, schedules: nextSchedules }
}
