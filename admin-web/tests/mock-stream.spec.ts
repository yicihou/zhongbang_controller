import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mounted: Array<() => unknown> = []
const notify = vi.fn()
const chime = vi.fn()

vi.mock('vue', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue')>()),
  onMounted: (fn: () => unknown) => mounted.push(fn),
  onUnmounted: () => undefined,
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElNotification: (...args: unknown[]) => notify(...args) }))
vi.mock('../src/composables/sound', () => ({ playChime: () => chime() }))
vi.mock('../src/api/admin', () => ({
  listNotices: vi.fn(async () => ({ list: [], total: 0, page: 1, size: 20 })),
}))

const storage = new Map<string, string>()
vi.stubGlobal('window', globalThis)
vi.stubGlobal('localStorage', {
  getItem: (key: string) => storage.get(key) ?? null,
  setItem: (key: string, value: string) => storage.set(key, value),
})

const { useAdminStream } = await import('../src/composables/useAdminStream')
const { useInboxStore } = await import('../src/stores/inbox')
const mockDb = await import('../src/mock/db')
const { publishMockStream } = await import('../src/mock/bus')
const { simulateIncomingOrder } = await import('../src/mock/sim')

describe('mock new-order stream', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    mockDb.resetDb()
    mounted.length = 0
    notify.mockClear()
    chime.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not create orders on a timer, only when the button simulates one', async () => {
    const inbox = useInboxStore()
    const stream = useAdminStream()
    for (const fn of mounted) await fn()
    expect(stream.status.value).toBe('live')

    const orders = mockDb.db.orders.length
    const notices = mockDb.db.notices.length
    await vi.advanceTimersByTimeAsync(60 * 60 * 1000)
    expect(mockDb.db.orders.length).toBe(orders)
    expect(mockDb.db.notices.length).toBe(notices)
    expect(notify).not.toHaveBeenCalled()
    expect(inbox.orderFlashes).toBe(0)

    expect(publishMockStream(simulateIncomingOrder())).toBe(true)
    expect(mockDb.db.orders.length).toBe(orders + 1)
    expect(notify).toHaveBeenCalledTimes(1)
    expect(chime).toHaveBeenCalledTimes(1)
    expect(inbox.orderFlashes).toBe(1)
    expect(inbox.unreadCount).toBe(1)
  })
})
