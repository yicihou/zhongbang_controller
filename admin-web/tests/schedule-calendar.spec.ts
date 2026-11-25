import { describe, expect, it } from 'vitest'
import { scheduleDays, shiftScheduleDate } from '../src/domain/schedule-calendar'

describe('师傅排期日期', () => {
  it('按周一到周日展示跨年周，周日仍属于同一周', () => {
    const days = [
      '2026-12-28',
      '2026-12-29',
      '2026-12-30',
      '2026-12-31',
      '2027-01-01',
      '2027-01-02',
      '2027-01-03',
    ]
    expect(scheduleDays('2027-01-01', 'week')).toEqual(days)
    expect(scheduleDays('2027-01-03', 'week')).toEqual(days)
  })

  it('月视图只展示所选日期所在月，并计入闰日', () => {
    const days = scheduleDays('2028-02-16', 'month')
    expect(days).toHaveLength(29)
    expect(days[0]).toBe('2028-02-01')
    expect(days.at(-1)).toBe('2028-02-29')
    expect(scheduleDays('2027-02-16', 'month')).toHaveLength(28)
  })

  it('按周翻页保留星期几，按月翻页时将不存在的日期调整到月底', () => {
    expect(shiftScheduleDate('2026-12-30', 'week', 1)).toBe('2027-01-06')
    expect(shiftScheduleDate('2027-01-06', 'week', -1)).toBe('2026-12-30')
    expect(shiftScheduleDate('2028-01-31', 'month', 1)).toBe('2028-02-29')
    expect(shiftScheduleDate('2027-03-31', 'month', -1)).toBe('2027-02-28')
    expect(shiftScheduleDate('2026-12-25', 'month', 1)).toBe('2027-01-25')
  })
})
