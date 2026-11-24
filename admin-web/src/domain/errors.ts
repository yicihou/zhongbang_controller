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
