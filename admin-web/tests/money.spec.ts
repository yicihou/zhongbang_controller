import { describe, expect, it } from 'vitest'
import { fenToYuanNumber, formatYuan, yuanToFen } from '../src/domain/money'

describe('money', () => {
  it('formats fen as yuan with grouping', () => {
    expect(formatYuan(337000)).toBe('3,370.00')
    expect(formatYuan(5)).toBe('0.05')
    expect(formatYuan(-5000)).toBe('-50.00')
    expect(formatYuan(0)).toBe('0.00')
  })

  it('converts yuan to fen', () => {
    expect(yuanToFen(12.34)).toBe(1234)
    expect(yuanToFen(0.1 + 0.2)).toBe(30)
    expect(fenToYuanNumber(328000)).toBe(3280)
  })
})
