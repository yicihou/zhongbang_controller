/**
 * 接口失败。code 用后端那套业务码（2001 未登录、2003 无权限、3002 档期冲突等）。
 * httpStatusFor 只在 Mock 里把业务码映射成 HTTP 状态，真实请求的 HTTP 状态由后端决定。
 * 网络不通时 http.ts 使用 -1，这里会映射成 400。
 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function httpStatusFor(code: number): number {
  switch (code) {
    case 2001:
      return 401
    case 2003:
      return 403
    case 4040:
      return 404
    case 4090:
    case 4001:
      return 409
    case 5000:
      return 500
    default:
      return 400
  }
}
