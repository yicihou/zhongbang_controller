/** 测分页：非法页码落到第 1 页，size 超过 100 会被截断。 */
import { describe, expect, it } from 'vitest'
import { paginate } from '../src/domain/page'

describe('page', () => {
  it('starts at page 1 and caps size at 100', () => {
    const items = Array.from({ length: 25 }, (_, index) => index + 1)
    expect(paginate(items, 0, 0)).toMatchObject({ page: 1, size: 20, total: 25, list: items.slice(0, 20) })
    expect(paginate(items, 2, 10).list).toEqual(items.slice(10, 20))
    expect(paginate(items, 1, 500).size).toBe(100)
  })
})
