export interface JwtPayload {
  sub?: string
  type?: string
  name?: string
  role?: string
  exp?: number
}

function fromBase64Url(segment: string): string {
  const pad = segment.length % 4 === 0 ? '' : '='.repeat(4 - (segment.length % 4))
  const b64 = segment.replace(/-/g, '+').replace(/_/g, '/') + pad
  const binary = atob(b64)
  const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

function toBase64Url(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  bytes.forEach((b) => {
    binary += String.fromCharCode(b)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

/** 只解 payload，不校验签名。真实请求仍由后端 JwtService 验签。 */
export function decodeJwtPayload(token: string | null | undefined): JwtPayload | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2 || !parts[1]) return null
  try {
    const json = JSON.parse(fromBase64Url(parts[1])) as JwtPayload
    return json && typeof json === 'object' ? json : null
  } catch {
    return null
  }
}

export function isExpired(payload: JwtPayload, nowSec: number): boolean {
  if (payload.exp == null) return false
  return payload.exp <= nowSec
}

/** 后端 UserType.STAFF → GrantedAuthority ROLE_STAFF。 */
export function isStaffPayload(payload: JwtPayload | null, nowSec = Date.now() / 1000): boolean {
  if (!payload || isExpired(payload, nowSec)) return false
  return payload.type === 'STAFF' || payload.role === 'ROLE_STAFF' || payload.role === 'STAFF'
}

export function issueMockStaffToken(user: { id: number; name: string }, nowSec = Date.now() / 1000): string {
  const header = toBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }))
  const payload = toBase64Url(
    JSON.stringify({
      sub: String(user.id),
      type: 'STAFF',
      role: 'ROLE_STAFF',
      name: user.name,
      exp: Math.floor(nowSec) + 7 * 24 * 3600,
    }),
  )
  return `${header}.${payload}.mock`
}
