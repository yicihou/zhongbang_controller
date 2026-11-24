/** 分 → 元，保留两位小数并加千分位。 */
export function formatYuan(fen: number): string {
  const sign = fen < 0 ? '-' : ''
  const abs = Math.abs(Math.trunc(fen))
  const yuan = Math.floor(abs / 100)
  const cents = abs % 100
  const grouped = yuan.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${sign}${grouped}.${cents.toString().padStart(2, '0')}`
}

/** 元 → 分，按分四舍五入，避免 0.1+0.2 的二进制误差。 */
export function yuanToFen(yuan: number): number {
  if (!Number.isFinite(yuan)) return 0
  return Math.round(yuan * 100)
}

export function fenToYuanNumber(fen: number): number {
  return Math.trunc(fen) / 100
}
