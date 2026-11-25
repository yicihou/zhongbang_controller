/**
 * 所有管理端请求的出口。页面不要自己 fetch。
 * USE_MOCK 为真时调用 mock/handle.ts，在内存里算出和后端一样的 {code,msg,data}。
 * 为假时用 fetch 访问 API_BASE + 路径，并带上 localStorage 里的 Bearer。
 * code===0 才把 data 交回调用方，否则抛 ApiError。业务码 2001 表示未登录。
 */
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

/**
 * auth 默认 true，会附带令牌。登录接口要传 auth:false，否则空令牌没意义，Mock 里登录本身也不校验令牌。
 * GET 不发送 body。网络失败时业务码是 -1，和后端错误码不是同一套。
 */
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

/**
 * 上传图片。不要自己设 Content-Type，否则浏览器不会带上 multipart 的边界，后端收不到文件。
 * Mock 不走这里，上传函数会把文件读成 data URL 再调用普通 request。
 */
export async function requestForm<T>(path: string, form: FormData, query?: object): Promise<T> {
  const token = readToken()
  const params = new URLSearchParams()
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value != null && value !== '') params.set(key, String(value))
    }
  }
  const qs = params.toString()
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}${qs ? `?${qs}` : ''}`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
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
  return unwrap(payload, response.status)
}
