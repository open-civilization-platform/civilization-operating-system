import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import CreateCivilizationForm from '../components/CreateCivilizationForm'
import { GET_REGIONS } from '../graphql/queries'

const accepted = {
  sagaId: 'saga-1', status: 'RUNNING', currentStep: 'CLAIM_REGION', failedStep: null,
  civilizationId: 42, name: 'Aurora', failure: null, sequence: 2, createdAt: '2026-10-23T00:00:00Z', updatedAt: '2026-10-23T00:00:01Z',
  steps: [
    { key: 'CREATE_CIVILIZATION', label: 'Civilization created', status: 'SUCCEEDED', attempts: 1, nextAttemptAt: null, failure: null, compensation: null },
    { key: 'CLAIM_REGION', label: 'Region claimed', status: 'IN_PROGRESS', attempts: 1, nextAttemptAt: null, failure: null, compensation: null },
    { key: 'DEPLOY_NEXUS_NODE', label: 'Nexus node deployed', status: 'PENDING', attempts: 0, nextAttemptAt: null, failure: null, compensation: null },
  ],
}
const region = { id: '7', name: 'Riverlands', description: '', scale: 'LOCAL', foodAvailability: 1, waterAvailability: 1, mineralAvailability: 1, energyAvailability: 1, housingAvailability: 1, dominantResource: 'WATER', claimed: false, claimedByCivilizationId: null }
const fetcher = vi.fn()
const onSucceeded = vi.fn()
function view(props = {}) {
  return <MockedProvider mocks={[{ request: { query: GET_REGIONS, variables: { claimed: false } }, result: { data: { regions: [region] } }, delay: 0 }]}><CreateCivilizationForm onClose={() => {}} onSucceeded={onSucceeded} {...props} /></MockedProvider>
}
async function submit() {
  await screen.findByRole('option', { name: 'Riverlands' })
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Aurora' } })
  fireEvent.change(screen.getByLabelText('Region'), { target: { value: '7' } })
  fireEvent.click(screen.getByRole('button', { name: 'Found Civilization' }))
  await screen.findByText('Civilization created')
}
beforeEach(() => {
  sessionStorage.clear(); onSucceeded.mockReset(); fetcher.mockReset(); vi.stubGlobal('fetch', fetcher)
  fetcher.mockImplementation(async (url: string) => new Response(JSON.stringify(url === '/api/v1/auth/connect' ? { token: 'jwt', clientId: 'owner', expiresIn: 3600 } : accepted), { status: url.endsWith('found-civilization') ? 202 : 200 }))
})
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals() })

it('postsRegionIdAndIdempotencyKey', async () => {
  render(view()); await submit()
  const [, request] = fetcher.mock.calls.find(([url]) => url.endsWith('found-civilization'))!
  expect(JSON.parse(request.body)).toEqual({ name: 'Aurora', scale: 'LOCAL', regionId: 7 })
  expect(request.headers.Authorization).toBe('Bearer jwt')
  expect(request.headers['Idempotency-Key']).toEqual(expect.any(String))
  expect(request.headers['Idempotency-Key'].length).toBeGreaterThan(10)
})
it('rendersThreeNamedStepsFromAcceptedResponse', async () => {
  render(view()); await submit()
  expect(screen.getByText('Region claimed')).toBeInTheDocument()
  expect(screen.getByText('Nexus node deployed')).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('In progress')
})
it('showsFailedStepAndCompensationOutcome', async () => {
  const { rerender } = render(view()); await submit()
  rerender(view({ sagaProgress: { ...accepted, status: 'COMPENSATION_FAILED', currentStep: 'CLAIM_REGION', sequence: 4, failure: { code: 'REGION_BUSY', message: 'Region unavailable' }, steps: accepted.steps.map(step => ({ ...step, status: 'FAILED', compensation: { status: 'COMPENSATION_FAILED', attempts: 3, failure: { code: 'TIMEOUT', message: 'Cleanup timed out' } } })) } }))
  expect(screen.getByRole('status')).toHaveTextContent('Compensation failed')
  expect(screen.getByText('Region unavailable')).toBeInTheDocument()
  expect(screen.getAllByText(/Cleanup timed out/)).toHaveLength(3)
})
it('reconcilesAfterReconnectAndIgnoresOlderSequence', async () => {
  const { rerender } = render(view({ connected: false })); await submit()
  fetcher.mockResolvedValue(new Response(JSON.stringify({ ...accepted, sequence: 5, status: 'SUCCEEDED' })))
  rerender(view({ connected: true }))
  await screen.findByText('Succeeded')
  rerender(view({ connected: true, sagaProgress: { ...accepted, sequence: 3 } }))
  expect(screen.getByRole('status')).toHaveTextContent('Succeeded')
  expect(onSucceeded).toHaveBeenCalledTimes(1)
  expect(fetcher.mock.calls.find(([url]) => url === '/api/v1/sagas/saga-1')?.[1].headers.Authorization).toBe('Bearer jwt')
})
it('refetchesStatusAfterReconnectOrSequenceGap without accepting an older GET', async () => {
  const { rerender } = render(view()); await submit()
  let finish!: (response: Response) => void
  fetcher.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  rerender(view({ sagaProgress: { ...accepted, sequence: 5, status: 'COMPENSATING' } }))
  await waitFor(() => expect(fetcher.mock.calls.some(([url]) => url === '/api/v1/sagas/saga-1')).toBe(true))
  await act(async () => finish(new Response(JSON.stringify(accepted))))
  expect(screen.getByRole('status')).toHaveTextContent('Compensating')
})
it('recovers ambiguous POST with the same key and payload after reload', async () => {
  fetcher.mockImplementation(async (url: string) => {
    if (url.endsWith('connect')) return new Response(JSON.stringify({ token: 'jwt', clientId: 'owner', expiresIn: 3600 }))
    throw new TypeError('network lost')
  })
  const first = render(view())
  await screen.findByRole('option', { name: 'Riverlands' })
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Aurora' } })
  fireEvent.change(screen.getByLabelText('Region'), { target: { value: '7' } })
  fireEvent.click(screen.getByRole('button', { name: 'Found Civilization' }))
  await screen.findByRole('alert')
  const original = fetcher.mock.calls.find(([url]) => url.endsWith('found-civilization'))![1]
  first.unmount()
  fetcher.mockResolvedValue(new Response(JSON.stringify(accepted), { status: 202 }))
  render(view()); await screen.findByText('Civilization created')
  const replays = fetcher.mock.calls.filter(([url]) => url.endsWith('found-civilization'))
  expect(replays[replays.length - 1][1]).toEqual(original)
  await waitFor(() => expect(fetcher.mock.calls.some(([url]) => url === '/api/v1/sagas/saga-1')).toBe(true))
})
it('restores an accepted saga and polls for a lost final event then stops', async () => {
  const first = render(view()); await submit(); first.unmount()
  vi.useFakeTimers()
  render(view())
  await act(async () => { await vi.advanceTimersByTimeAsync(1) })
  expect(screen.getByText('Civilization created')).toBeInTheDocument()
  fetcher.mockImplementation(async () => new Response(JSON.stringify({ ...accepted, sequence: 6, status: 'SUCCEEDED' })))
  await act(async () => { await vi.advanceTimersByTimeAsync(5000) })
  expect(screen.getByRole('status')).toHaveTextContent('Succeeded')
  const count = fetcher.mock.calls.length
  await act(async () => { await vi.advanceTimersByTimeAsync(15000) })
  expect(fetcher).toHaveBeenCalledTimes(count)
})
