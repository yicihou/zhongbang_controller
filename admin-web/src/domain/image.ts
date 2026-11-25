/**
 * 判断一段文字是不是图片地址，以及上传前的类型和大小。
 * 老的收款凭证可能是「对公 8891」这种纯文字，那些不要当成图片去渲染。
 */
export const IMAGE_MAX_BYTES = 10 * 1024 * 1024

const IMAGE_EXT = /\.(jpe?g|png|webp)(\?.*)?$/i

export function isImageUrl(value: string | undefined | null): boolean {
  if (!value) return false
  const text = value.trim()
  if (!text) return false
  if (text.toLowerCase().startsWith('data:image/')) return true
  return IMAGE_EXT.test(text) || text.includes('/uploads/')
}

/** 上传前给页面用。通过时返回空字符串。 */
export function imageFileError(name: string, type: string, size: number): string {
  const lower = (name || '').toLowerCase()
  const okName = /\.(jpe?g|png|webp)$/.test(lower)
  const okType = !type || type === 'application/octet-stream' || /image\/(jpe?g|png|webp|pjpeg)/.test(type)
  if (!okName || !okType) return '只支持 jpg、png、webp'
  if (size > IMAGE_MAX_BYTES) return '图片不能超过 10MB'
  if (size <= 0) return '请选择图片'
  return ''
}
