const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

export function formatDateTime(date = new Date()): string {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const pick = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? ''
  let hour = pick('hour')
  if (hour === '24') hour = '00'
  return `${pick('year')}-${pick('month')}-${pick('day')} ${hour}:${pick('minute')}:${pick('second')}`
}

export function todayShanghai(date = new Date()): string {
  return formatDateTime(date).slice(0, 10)
}

/** 按公历日期加减，不涉及时区偏移。 */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const utc = new Date(Date.UTC(y, (m || 1) - 1, (d || 1) + days))
  return utc.toISOString().slice(0, 10)
}

export function weekdayLabel(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  return WEEKDAYS[new Date(Date.UTC(y, (m || 1) - 1, d || 1)).getUTCDay()] ?? ''
}

export function slotFromAppoint(appointAt: string): { date: string; slot: string } {
  const date = appointAt.slice(0, 10)
  const hour = Number(appointAt.slice(11, 13))
  const slot = Number.isFinite(hour) ? (hour < 12 ? '上午' : hour < 18 ? '下午' : '晚上') : '上午'
  return { date, slot }
}
