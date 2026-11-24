import type { PageResult } from '@/api/types'

/** 与后端 PageResult.normalizePage / normalizeSize 一致：page≥1，size 1–100。 */
export function paginate<T>(items: T[], page?: number, size?: number): PageResult<T> {
  const p = Math.max(1, Math.trunc(Number(page) || 1))
  let s = Math.trunc(Number(size) || 20)
  if (s < 1) s = 20
  if (s > 100) s = 100
  const start = (p - 1) * s
  return {
    list: items.slice(start, start + s),
    total: items.length,
    page: p,
    size: s,
  }
}
