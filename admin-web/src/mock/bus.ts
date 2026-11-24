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
