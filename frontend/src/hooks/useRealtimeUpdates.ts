import { useEffect, useCallback, useRef } from 'react'
import { ensureSession } from '../auth/session'
import type { SagaProgressPayload } from '../api/sagas'

interface RealtimeConfig {
  token?: string
  onResourceTick?: (data: unknown) => void
  onNexusMessage?: (data: unknown) => void
  onVoxtexMessage?: (data: unknown) => void
  onCivilizationEvent?: (data: unknown) => void
  onSimulationTick?: (data: unknown) => void
  onStatusChange?: (connected: boolean) => void
  onSagaProgress?: (data: SagaProgressPayload) => void
}

export function useRealtimeUpdates({
  token,
  onResourceTick,
  onNexusMessage,
  onVoxtexMessage,
  onCivilizationEvent,
  onSimulationTick,
  onStatusChange,
  onSagaProgress,
}: RealtimeConfig) {
  const ws = useRef<WebSocket | null>(null)
  const nexusMsgHandler = onNexusMessage || onVoxtexMessage
  const handlers = useRef({ onResourceTick, onNexusMessage: nexusMsgHandler, onCivilizationEvent, onSimulationTick, onSagaProgress, onStatusChange })

  useEffect(() => {
    handlers.current = { onResourceTick, onNexusMessage: nexusMsgHandler, onCivilizationEvent, onSimulationTick, onSagaProgress, onStatusChange }
  })

  useEffect(() => {
    let disposed = false
    let reconnectTimeout: ReturnType<typeof setTimeout> | undefined
    const connect = async () => {
      try {
        const sessionToken = token || (await ensureSession()).token
        if (disposed) return
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
        const url = `${protocol}//${location.host}/ws/nexus?token=${encodeURIComponent(sessionToken)}`

        ws.current = new WebSocket(url)

        ws.current.onopen = () => { if (!disposed) handlers.current.onStatusChange?.(true) }

        ws.current.onclose = () => {
          if (disposed) return
          handlers.current.onStatusChange?.(false)
          reconnectTimeout = setTimeout(connect, 3000)
        }

        ws.current.onmessage = (event) => {
          if (disposed) return
          try {
            const msg = JSON.parse(event.data)
            const type = msg.type || ''
            const payload = msg.payload || msg

            switch (type) {
              case 'SAGA_PROGRESS':
                if (typeof payload.sagaId === 'string' && Number.isSafeInteger(payload.sequence) && payload.sequence >= 0 &&
                    ['PENDING', 'RUNNING', 'COMPENSATING', 'SUCCEEDED', 'FAILED', 'COMPENSATED', 'COMPENSATION_FAILED'].includes(payload.status) && Array.isArray(payload.steps)) {
                  handlers.current.onSagaProgress?.(payload)
                }
                break
              case 'RESOURCE_TICK':
              case 'resource_tick':
                handlers.current.onResourceTick?.(payload)
                break
              case 'NEXUS_MESSAGE':
              case 'nexus_message':
              case 'VOXTEX_MESSAGE':
              case 'voxtex_message':
                handlers.current.onNexusMessage?.(payload)
                break
              case 'CIVILIZATION_EVENT':
              case 'civilization_event':
                handlers.current.onCivilizationEvent?.(payload)
                break
              case 'SIMULATION_TICK':
              case 'simulation_tick':
                handlers.current.onSimulationTick?.(payload)
                break
            }
          } catch {
            // ignore parse errors
          }
        }
      } catch {
        if (!disposed) {
          handlers.current.onStatusChange?.(false)
          reconnectTimeout = setTimeout(connect, 3000)
        }
      }
    }
    void connect()
    return () => {
      disposed = true
      clearTimeout(reconnectTimeout)
      ws.current?.close()
    }
  }, [token])

  const send = useCallback((type: string, payload?: unknown) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify({ type, payload }))
    }
  }, [])

  return { send }
}
