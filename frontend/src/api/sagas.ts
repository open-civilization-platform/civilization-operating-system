export type SagaStatus = 'PENDING' | 'RUNNING' | 'COMPENSATING' | 'SUCCEEDED' | 'FAILED' | 'COMPENSATED' | 'COMPENSATION_FAILED'
export type StepStatus = 'PENDING' | 'POTENTIALLY_APPLIED' | 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED' | 'COMPENSATING' | 'COMPENSATED' | 'COMPENSATION_FAILED'
export interface SagaFailure { code: string | null; message: string | null }
export interface SagaCompensation { status: StepStatus; attempts: number; failure: SagaFailure | null }
export interface SagaStep { key: string; label: string; status: StepStatus; attempts: number; nextAttemptAt: string | null; failure: SagaFailure | null; compensation: SagaCompensation | null }
export interface SagaProgressPayload {
  sagaId: string; status: SagaStatus; currentStep: string | null; steps: SagaStep[]
  failure: SagaFailure | null; compensation: SagaCompensation | null; updatedAt: string; sequence: number
}
export interface SagaStatusResponse extends Omit<SagaProgressPayload, 'compensation'> {
  failedStep: string | null; civilizationId: number | null; name: string; createdAt: string
}
export interface FoundCivilizationInput { name: string; scale: 'LOCAL' | 'REGIONAL' | 'CONTINENTAL' | 'GLOBAL'; regionId: number }
export class SagaApiError extends Error {
  constructor(public status: number) {
    super(status === 409 ? 'This submission conflicts with an earlier request. Please close and try again.' : status === 400 ? 'Please check the civilization name, scale, and region.' : status === 401 || status === 403 ? 'Your session cannot access this request.' : 'Unable to retrieve civilization progress. Please retry.')
  }
}
async function read(response: Response): Promise<SagaStatusResponse> {
  if (!response.ok) throw new SagaApiError(response.status)
  return response.json()
}
export async function submitFoundCivilization(input: FoundCivilizationInput, idempotencyKey: string, token: string): Promise<SagaStatusResponse> {
  return read(await fetch('/api/v1/sagas/found-civilization', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey, Authorization: `Bearer ${token}` }, body: JSON.stringify(input),
  }))
}
export async function getFoundCivilizationSaga(sagaId: string, token: string): Promise<SagaStatusResponse> {
  return read(await fetch(`/api/v1/sagas/${encodeURIComponent(sagaId)}`, { headers: { Authorization: `Bearer ${token}` } }))
}
