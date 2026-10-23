import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery } from '@apollo/client'
import { GET_REGIONS } from '../graphql/queries'
import { ensureSession } from '../auth/session'
import { getFoundCivilizationSaga, SagaApiError, submitFoundCivilization } from '../api/sagas'
import type { FoundCivilizationInput, SagaProgressPayload, SagaStatusResponse } from '../api/sagas'

interface PendingSubmission { key: string; input: FoundCivilizationInput; sagaId?: string }
const STORAGE_KEY = 'found-civilization-submission'
function restore(): PendingSubmission | null {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null') } catch { return null }
}
const terminal = (status: string) => ['SUCCEEDED', 'FAILED', 'COMPENSATED', 'COMPENSATION_FAILED'].includes(status)
const labels: Record<string, string> = {
  PENDING: 'Pending', RUNNING: 'In progress', IN_PROGRESS: 'In progress', POTENTIALLY_APPLIED: 'In progress',
  SUCCEEDED: 'Succeeded', FAILED: 'Failed', COMPENSATING: 'Compensating', COMPENSATED: 'Compensated', COMPENSATION_FAILED: 'Compensation failed',
}
interface CreateCivilizationFormProps {
  onClose: () => void
  onSucceeded?: () => void
  sagaProgress?: SagaProgressPayload
  connected?: boolean
}

export default function CreateCivilizationForm({ onClose, onSucceeded, sagaProgress, connected = false }: CreateCivilizationFormProps) {
  const [pending, setPending] = useState<PendingSubmission | null>(restore)
  const pendingRef = useRef(pending)
  const [name, setName] = useState(pending?.input.name || '')
  const [scale, setScale] = useState<FoundCivilizationInput['scale']>(pending?.input.scale || 'LOCAL')
  const [region, setRegion] = useState(String(pending?.input.regionId || ''))
  const [snapshot, setSnapshot] = useState<SagaStatusResponse | null>(null)
  const snapshotRef = useRef(snapshot)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const mounted = useRef(false)
  const succeeded = useRef(false)
  const successCallback = useRef(onSucceeded)
  successCallback.current = onSucceeded
  const { data, loading: regionsLoading, error: regionsError } = useQuery<{ regions: { id: string; name: string }[] }>(GET_REGIONS, { variables: { claimed: false } })

  const retain = useCallback((value: PendingSubmission | null) => {
    pendingRef.current = value
    setPending(value)
    if (value) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    else sessionStorage.removeItem(STORAGE_KEY)
  }, [])

  const accept = useCallback((value: SagaStatusResponse | SagaProgressPayload) => {
    if (!mounted.current || pendingRef.current?.sagaId !== value.sagaId) return
    const previous = snapshotRef.current
    if (previous && value.sequence <= previous.sequence) return
    const next: SagaStatusResponse = {
      failedStep: null, civilizationId: null, name: pendingRef.current.input.name, createdAt: value.updatedAt,
      ...previous, ...value,
    }
    snapshotRef.current = next
    setSnapshot(next)
    setError('')
    if (terminal(next.status)) {
      sessionStorage.removeItem(STORAGE_KEY)
      if (next.status === 'SUCCEEDED' && !succeeded.current) {
        succeeded.current = true
        successCallback.current?.()
      }
    }
  }, [])

  const reconcile = useCallback(async () => {
    const id = pendingRef.current?.sagaId
    if (!id) return
    try {
      const { token } = await ensureSession()
      accept(await getFoundCivilizationSaga(id, token))
    } catch {
      if (mounted.current) setError('Unable to refresh progress. Your submission is retained; retry to reconnect.')
    }
  }, [accept])

  const submit = useCallback(async (submission: PendingSubmission, recovering = false) => {
    setBusy(true); setError('')
    try {
      const { token } = await ensureSession()
      const result = await submitFoundCivilization(submission.input, submission.key, token)
      if (!mounted.current) return
      retain({ ...submission, sagaId: result.sagaId })
      accept(result)
      if (recovering) await reconcile()
    } catch (failure) {
      if (!mounted.current) return
      if (failure instanceof SagaApiError && failure.status >= 400 && failure.status < 500) retain(null)
      setError(failure instanceof SagaApiError ? failure.message : 'Connection interrupted. Retry to recover this same submission.')
    } finally {
      if (mounted.current) setBusy(false)
    }
  }, [accept, reconcile, retain])

  useEffect(() => {
    mounted.current = true
    const saved = pendingRef.current
    if (saved?.sagaId) void reconcile()
    else if (saved) void submit(saved, true)
    return () => { mounted.current = false }
  }, [reconcile, submit])

  useEffect(() => {
    if (!sagaProgress || sagaProgress.sagaId !== pendingRef.current?.sagaId) return
    const sequence = snapshotRef.current?.sequence
    const gap = sequence !== undefined && sagaProgress.sequence > sequence + 1
    accept(sagaProgress)
    if (gap) void reconcile()
  }, [sagaProgress, accept, reconcile])

  useEffect(() => { if (connected) void reconcile() }, [connected, reconcile])
  const active = Boolean(pending?.sagaId && (!snapshot || !terminal(snapshot.status)))
  useEffect(() => {
    if (!active) return
    const timer = setInterval(() => { void reconcile() }, 5000)
    return () => clearInterval(timer)
  }, [active, reconcile])

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (busy || pendingRef.current) return
    const submission = { key: crypto.randomUUID(), input: { name: name.trim(), scale, regionId: Number(region) } }
    retain(submission)
    void submit(submission)
  }
  const fieldStyle = { width: '100%', padding: '0.5rem 0.75rem', borderRadius: '0.5rem', background: '#0f172a', border: '1px solid #334155', color: '#e2e8f0' }
  return (
    <div role="dialog" aria-modal="true" aria-labelledby="found-civilization-title" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      <form onSubmit={handleSubmit} style={{ background: '#1e293b', border: '1px solid #334155', borderRadius: '0.75rem', padding: '2rem', width: 440, maxHeight: '90vh', overflowY: 'auto' }}>
        <h2 id="found-civilization-title" style={{ fontWeight: 600, marginBottom: '1.5rem' }}>Found New Civilization</h2>
        <fieldset disabled={Boolean(pending) || busy} style={{ border: 0, padding: 0 }}>
          <label htmlFor="civilization-name">Name</label>
          <input id="civilization-name" value={name} onChange={event => setName(event.target.value)} required style={fieldStyle} />
          <label htmlFor="civilization-scale">Scale</label>
          <select id="civilization-scale" value={scale} onChange={event => setScale(event.target.value as FoundCivilizationInput['scale'])} style={fieldStyle}>
            <option value="LOCAL">Local</option><option value="REGIONAL">Regional</option><option value="CONTINENTAL">Continental</option><option value="GLOBAL">Global</option>
          </select>
          <label htmlFor="civilization-region">Region</label>
          <select id="civilization-region" value={region} onChange={event => setRegion(event.target.value)} required style={fieldStyle}>
            <option value="">{regionsLoading ? 'Loading regions...' : 'Select an available region'}</option>
            {data?.regions.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
        </fieldset>
        {regionsError && <p role="alert">Unable to load available regions.</p>}
        {snapshot && <section aria-label="Civilization progress" aria-live="polite">
          <p role="status">{labels[snapshot.status]}</p>
          {snapshot.failure?.message && <p>{snapshot.failure.message}</p>}
          <ol>{snapshot.steps.map(step => <li key={step.key}>
            <strong>{step.label}</strong>: {labels[step.status]}
            {step.failure?.message && <p>{step.failure.message}</p>}
            {step.compensation && <p>Compensation: {labels[step.compensation.status]}{step.compensation.failure?.message && ` — ${step.compensation.failure.message}`}</p>}
          </li>)}</ol>
        </section>}
        {error && <p role="alert" style={{ color: '#f87171' }}>{error}</p>}
        {error && pending && <button type="button" disabled={busy} onClick={() => { if (pending.sagaId) void reconcile(); else void submit(pending, true) }}>Retry connection</button>}
        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
          <button type="button" onClick={onClose}>{pending ? 'Close' : 'Cancel'}</button>
          {!pending && <button type="submit" disabled={busy || regionsLoading || Boolean(regionsError)}>{busy ? 'Founding...' : 'Found Civilization'}</button>}
        </div>
      </form>
    </div>
  )
}
