/** §5.3：SSE 连续失败后降级为 30 秒轮询 notices。 */

export const SSE_POLL_INTERVAL_MS = 30_000
export const SSE_FAIL_THRESHOLD = 3

export type StreamMode = 'sse' | 'poll'

export interface StreamState {
  mode: StreamMode
  failures: number
  delayMs: number
}

export function initialStreamState(): StreamState {
  return { mode: 'sse', failures: 0, delayMs: 1000 }
}

export function registerStreamOpen(): StreamState {
  return initialStreamState()
}

export function registerStreamFailure(state: StreamState): StreamState {
  const failures = state.failures + 1
  if (failures >= SSE_FAIL_THRESHOLD) {
    return { mode: 'poll', failures, delayMs: SSE_POLL_INTERVAL_MS }
  }
  const delayMs = Math.min(1000 * 2 ** (failures - 1), SSE_POLL_INTERVAL_MS)
  return { mode: 'sse', failures, delayMs }
}

export interface SseMessage {
  event: string
  data: string
  id?: string
}

/** 从增量缓冲里取出完整 SSE 帧（以空行分隔）。 */
export function consumeSseBuffer(buffer: string): { messages: SseMessage[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const parts = normalized.split('\n\n')
  const rest = parts.pop() ?? ''
  const messages: SseMessage[] = []
  for (const part of parts) {
    if (!part.trim() || part.startsWith(':')) continue
    let event = 'message'
    const dataLines: string[] = []
    let id: string | undefined
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      else if (line.startsWith('id:')) id = line.slice(3).trim()
    }
    if (dataLines.length) messages.push({ event, data: dataLines.join('\n'), id })
  }
  return { messages, rest }
}
