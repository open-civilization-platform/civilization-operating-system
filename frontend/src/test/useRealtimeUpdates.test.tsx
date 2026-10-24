import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useRealtimeUpdates } from '../hooks/useRealtimeUpdates'

class Socket {
  static OPEN = 1
  static instances: Socket[] = []
  readyState = 1
  onopen?: () => void
  onclose?: () => void
  onmessage?: (event: { data: string }) => void
  constructor(public url: string) { Socket.instances.push(this) }
  close() { this.onclose?.() }
  send = vi.fn()
}
beforeEach(() => { Socket.instances = []; sessionStorage.clear(); vi.stubGlobal('WebSocket', Socket) })
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals() })

it('parsesOnlySagaProgressEvents and preserves existing callbacks', async () => {
  const progress = vi.fn(), resources = vi.fn()
  const { unmount } = renderHook(() => useRealtimeUpdates({ token: 'shared jwt', onSagaProgress: progress, onResourceTick: resources }))
  await waitFor(() => expect(Socket.instances).toHaveLength(1))
  const socket = Socket.instances[0]
  expect(socket.url).toContain('?token=shared%20jwt')
  const payload = { sagaId: 'saga-1', status: 'RUNNING', currentStep: null, steps: [], failure: null, compensation: null, updatedAt: '2026-10-23T00:00:00Z', sequence: 1 }
  for (const data of ['broken', JSON.stringify({ type: 'saga_progress', payload }), JSON.stringify({ type: 'OTHER', payload }), JSON.stringify({ type: 'SAGA_PROGRESS', payload: {} })]) socket.onmessage?.({ data })
  expect(progress).not.toHaveBeenCalled()
  socket.onmessage?.({ data: JSON.stringify({ type: 'SAGA_PROGRESS', payload }) })
  expect(progress).toHaveBeenCalledWith(payload)
  socket.onmessage?.({ data: JSON.stringify({ type: 'RESOURCE_TICK', payload: { tick: 2 } }) })
  expect(resources).toHaveBeenCalledWith({ tick: 2 })
  unmount()
  socket.onmessage?.({ data: JSON.stringify({ type: 'SAGA_PROGRESS', payload }) })
  expect(progress).toHaveBeenCalledTimes(1)
})
it('awaits shared session for existing consumers and reconnects only while mounted', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ token: 'jwt', clientId: 'owner', expiresIn: 3600 })))
  vi.stubGlobal('fetch', fetcher)
  const status = vi.fn()
  const first = renderHook(() => useRealtimeUpdates({ onStatusChange: status }))
  const second = renderHook(() => useRealtimeUpdates({}))
  expect(Socket.instances).toHaveLength(0)
  await waitFor(() => expect(Socket.instances).toHaveLength(2))
  expect(fetcher).toHaveBeenCalledTimes(1)
  expect(Socket.instances.every(socket => socket.url.endsWith('?token=jwt'))).toBe(true)
  vi.useFakeTimers()
  act(() => { Socket.instances[0].onopen?.(); Socket.instances[0].onclose?.() })
  expect(status.mock.calls).toEqual([[true], [false]])
  await act(async () => { await vi.advanceTimersByTimeAsync(3000) })
  expect(Socket.instances).toHaveLength(3)
  first.unmount(); second.unmount()
  await act(async () => { await vi.advanceTimersByTimeAsync(9000) })
  expect(Socket.instances).toHaveLength(3)
})
