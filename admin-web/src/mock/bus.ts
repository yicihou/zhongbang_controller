/**
 * Mock 里代替 SSE 的内存广播。模拟新订单时 publish，顶栏的 useAdminStream 在 Mock 模式下 subscribe。
 * 没有订阅者时 publish 返回 false，调用方可以改走 inbox，避免通知丢在没人听的时候。
 */
import type { AdminNotice } from '@/api/types'

type Listener = (notice: AdminNotice) => void

const listeners = new Set<Listener>()

export function subscribeMockStream(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function publishMockStream(notice: AdminNotice): boolean {
  if (listeners.size === 0) return false
  for (const listener of listeners) listener(notice)
  return true
}

export function mockListenerCount(): number {
  return listeners.size
}
