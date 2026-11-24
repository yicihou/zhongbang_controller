import { describe, expect, it } from 'vitest'
import {
  SSE_FAIL_THRESHOLD,
  SSE_POLL_INTERVAL_MS,
  consumeSseBuffer,
  initialStreamState,
  registerStreamFailure,
  registerStreamOpen,
} from '../src/domain/sse'

describe('sse fallback', () => {
  it('falls back to 30s polling after three failures', () => {
    let state = initialStreamState()
    state = registerStreamFailure(state)
    expect(state.mode).toBe('sse')
    expect(state.delayMs).toBe(1000)
    state = registerStreamFailure(state)
    expect(state.mode).toBe('sse')
    expect(state.delayMs).toBe(2000)
    state = registerStreamFailure(state)
    expect(state.failures).toBe(SSE_FAIL_THRESHOLD)
    expect(state.mode).toBe('poll')
    expect(state.delayMs).toBe(SSE_POLL_INTERVAL_MS)
    expect(registerStreamOpen().mode).toBe('sse')
    expect(registerStreamOpen().failures).toBe(0)
  })

  it('parses complete SSE frames and keeps the remainder', () => {
    const chunk = 'event: ORDER_CREATED\ndata: {"id":1}\n\ndata: {"id":2}\n\n'
    const parsed = consumeSseBuffer(chunk)
    expect(parsed.messages).toEqual([
      { event: 'ORDER_CREATED', data: '{"id":1}', id: undefined },
      { event: 'message', data: '{"id":2}', id: undefined },
    ])
    expect(parsed.rest).toBe('')
    const partial = consumeSseBuffer('event: REPAIR_CREATED\ndata: {"id":3}')
    expect(partial.messages).toEqual([])
    expect(partial.rest).toContain('REPAIR_CREATED')
  })
})
