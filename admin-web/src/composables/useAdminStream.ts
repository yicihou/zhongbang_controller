/**
 * 顶栏的实时通知。
 * Mock：订阅 mock/bus，并每 120 秒自动造一笔新订单广播出去。状态一开始就是 live。
 * 真实 API：用 fetch 读 /api/admin/stream（浏览器的 EventSource 不能加 Authorization 头），自己拆 SSE 帧。
 * 连续失败 3 次后改为每 30 秒调用 GET /api/admin/notices?unread=1。
 * 后端目前没有 stream 和 notices 控制器，关掉 Mock 后这里会一直重连再进入轮询，轮询同样 404。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { listNotices } from '@/api/admin'
import { readToken } from '@/api/http'
import { paths } from '@/api/paths'
import type { AdminNotice } from '@/api/types'
import { API_BASE, USE_MOCK } from '@/config'
import { consumeSseBuffer, initialStreamState, registerStreamFailure, registerStreamOpen, type StreamState } from '@/domain/sse'
import { publishMockStream, subscribeMockStream } from '@/mock/bus'
import { simulateIncomingOrder } from '@/mock/sim'
import { useInboxStore } from '@/stores/inbox'
import { playChime } from './sound'

const AUTO_MS = 120_000

export function useAdminStream() {
  const inbox = useInboxStore()
  const router = useRouter()

  function openNotice(notice: AdminNotice) {
    const fresh = inbox.ingest(notice, true)
    if (!fresh) return
    if (inbox.soundOn) {
      try {
        playChime()
      } catch {
        /* 浏览器可能拦截音频 */
      }
    }
    ElNotification({
      title: notice.title,
      message: notice.body,
      type: notice.eventType === 'ORDER_CREATED' ? 'warning' : 'info',
      duration: 8000,
      onClick: () => {
        if (notice.bizType === 'ORDER') void router.push(`/orders/${notice.bizId}`)
        else if (notice.bizType === 'REPAIR') void router.push(`/repairs/${notice.bizId}`)
      },
    })
  }

  async function pullUnread() {
    const page = await listNotices({ unread: 1, page: 1, size: 20 })
    for (const notice of page.list) openNotice(notice)
  }
  const status = ref<'live' | 'reconnecting' | 'polling'>(USE_MOCK ? 'live' : 'reconnecting')
  let stopped = false
  let policy: StreamState = initialStreamState()
  let pollTimer = 0
  let autoTimer = 0
  let unsubscribe = () => {}
  let abort: AbortController | null = null

  function setStatus(next: 'live' | 'reconnecting' | 'polling') {
    status.value = next
    inbox.linkMode = next
  }

  function stopPoll() {
    if (pollTimer) window.clearInterval(pollTimer)
    pollTimer = 0
  }

  function startPoll() {
    stopPoll()
    setStatus('polling')
    pollTimer = window.setInterval(() => {
      void pullUnread().catch(() => undefined)
    }, policy.delayMs)
  }

  function connectMock() {
    unsubscribe()
    stopPoll()
    abort?.abort()
    policy = registerStreamOpen()
    setStatus('live')
    unsubscribe = subscribeMockStream((notice) => openNotice(notice))
    if (autoTimer) window.clearInterval(autoTimer)
    autoTimer = window.setInterval(() => {
      if (inbox.linkMode === 'live') publishMockStream(simulateIncomingOrder())
    }, AUTO_MS)
  }

  async function connectReal() {
    unsubscribe()
    stopPoll()
    abort?.abort()
    if (policy.mode === 'poll') {
      startPoll()
      return
    }
    setStatus(policy.failures > 0 ? 'reconnecting' : 'reconnecting')
    const controller = new AbortController()
    abort = controller
    try {
      const token = readToken()
      const response = await fetch(`${API_BASE}${paths.stream}`, {
        headers: {
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        signal: controller.signal,
      })
      if (response.status === 401) {
        policy = registerStreamFailure(policy)
        if (policy.mode === 'poll') startPoll()
        else window.setTimeout(() => void connectReal(), policy.delayMs)
        return
      }
      if (!response.ok || !response.body) throw new Error('sse failed')
      policy = registerStreamOpen()
      setStatus('live')
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (!stopped) {
        const chunk = await reader.read()
        if (chunk.done) break
        buffer += decoder.decode(chunk.value, { stream: true })
        const parsed = consumeSseBuffer(buffer)
        buffer = parsed.rest
        for (const message of parsed.messages) {
          try {
            const notice = JSON.parse(message.data) as AdminNotice
            if (!notice.eventType) notice.eventType = message.event as AdminNotice['eventType']
            openNotice(notice)
          } catch {
            /* 忽略无法解析的帧 */
          }
        }
      }
      if (stopped) return
      throw new Error('sse closed')
    } catch {
      if (stopped || controller.signal.aborted) return
      policy = registerStreamFailure(policy)
      if (policy.mode === 'poll') {
        startPoll()
        return
      }
      setStatus('reconnecting')
      window.setTimeout(() => {
        if (!stopped) void connectReal()
      }, policy.delayMs)
    }
  }

  function reconnect() {
    policy = initialStreamState()
    if (USE_MOCK) connectMock()
    else void connectReal()
  }

  function simulateDrop() {
    if (!USE_MOCK) return
    unsubscribe()
    unsubscribe = () => {}
    if (autoTimer) window.clearInterval(autoTimer)
    autoTimer = 0
    policy = initialStreamState()
    policy = registerStreamFailure(policy)
    policy = registerStreamFailure(policy)
    policy = registerStreamFailure(policy)
    startPoll()
  }

  async function pollNow() {
    await pullUnread()
  }

  onMounted(async () => {
    try {
      const page = await listNotices({ page: 1, size: 20 })
      for (const notice of page.list) inbox.ingest(notice, false)
    } catch {
      /* 登录失效时由页面请求统一处理 */
    }
    if (USE_MOCK) connectMock()
    else void connectReal()
  })

  onUnmounted(() => {
    stopped = true
    unsubscribe()
    stopPoll()
    abort?.abort()
    if (autoTimer) window.clearInterval(autoTimer)
  })

  return { status, reconnect, simulateDrop, pollNow }
}
