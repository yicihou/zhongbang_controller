import type { PageResult } from '@/api/types'

/**
 * 把数组切成一页。page 小于 1 或不是数字时用 1。
 * size 缺省、为 0 或不是数字时用 20；大于 100 时改成 100。
 * 后端 PageResult.normalizeSize 把小于 1 的 size 收成 1，和这里的缺省 20 不一样。
 * 管理端列表自己传 10 或 20，不会踩到这个差别。
 */
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
