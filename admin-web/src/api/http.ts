import { API_BASE, USE_MOCK } from '@/config'
import { ApiError } from '@/domain/errors'
import { handle } from '@/mock/handle'
import type { ApiResponse } from './types'

const TOKEN_KEY = 'zb_admin_token'

export function readToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function writeToken(token: string): void {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export interface RequestOptions {
  query?: object
  body?: unknown
  auth?: boolean
}

function queryString(query?: RequestOptions['query']): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value == null || value === '') continue
    params.set(key, String(value))
  }
  const text = params.toString()
  return text ? `?${text}` : ''
}

function unwrap<T>(body: ApiResponse<T> | null | undefined, status: number): T {
  if (!body || typeof body !== 'object') {
    throw new ApiError(status === 401 ? 2001 : 5000, status === 401 ? '未登录或 token 已过期' : '响应格式错误')
  }
  if (body.code === 0) return body.data
  throw new ApiError(body.code || status, body.msg || '请求失败')
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const auth = options.auth !== false
  const token = auth ? readToken() : ''
  const query: Record<string, string> = {}
  if (options.query) {
    for (const [key, value] of Object.entries(options.query as Record<string, unknown>)) {
      if (value != null && value !== '') query[key] = String(value)
    }
  }

  if (USE_MOCK) {
    const result = handle(method, path, query, options.body, token)
    return unwrap<T>(result.body as ApiResponse<T>, result.status)
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}${queryString(options.query)}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: options.body == null || method === 'GET' ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(-1, '网络异常，请检查网络后重试')
  }

  let payload: ApiResponse<T> | null = null
  try {
    payload = (await response.json()) as ApiResponse<T>
  } catch {
    if (response.status === 401) throw new ApiError(2001, '未登录或 token 已过期')
    throw new ApiError(5000, '响应格式错误')
  }
  if (response.status === 401 && (!payload || payload.code == null)) {
    throw new ApiError(2001, '未登录或 token 已过期')
  }
  return unwrap(payload, response.status)
}
