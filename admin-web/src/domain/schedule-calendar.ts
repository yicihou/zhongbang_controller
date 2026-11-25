import { addDays } from './clock'

export type ScheduleView = 'week' | 'month'

export function scheduleDays(selectedDate: string, view: ScheduleView): string[] {
  const date = new Date(`${selectedDate}T00:00:00Z`)
  if (view === 'week') {
    const daysSinceMonday = (date.getUTCDay() + 6) % 7
    return Array.from({ length: 7 }, (_, index) => addDays(selectedDate, index - daysSinceMonday))
  }

  date.setUTCMonth(date.getUTCMonth() + 1, 0)
  const daysInMonth = date.getUTCDate()
  const monthStart = selectedDate.slice(0, 8) + '01'
  return Array.from({ length: daysInMonth }, (_, index) => addDays(monthStart, index))
}

export function shiftScheduleDate(selectedDate: string, view: ScheduleView, direction: -1 | 1): string {
  if (view === 'week') return addDays(selectedDate, direction * 7)

  const date = new Date(`${selectedDate}T00:00:00Z`)
  const selectedDay = date.getUTCDate()
  date.setUTCMonth(date.getUTCMonth() + direction + 1, 0)
  date.setUTCDate(Math.min(selectedDay, date.getUTCDate()))
  return date.toISOString().slice(0, 10)
}
