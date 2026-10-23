# Found Civilization Saga Progress

## Purpose

Make the existing three-step Found Civilization saga the user-facing creation flow. The caller must be able to see durable progress, learn which step failed and whether rollback completed, and recover the current state after a WebSocket disconnect or retry.

## Goals

- Use `POST /api/v1/sagas/found-civilization` as the canonical creation command.
- Return a durable saga ID and status, with `GET /api/v1/sagas/{id}` for reconciliation.
- Publish typed progress events over the existing `/ws/nexus` connection and let the frontend reconcile through GET after reconnecting.
- Update `CreateCivilizationForm` to select and submit a `regionId` rather than a region name, and show the three named steps, failures, and compensation outcomes.
- Persist saga state in the relational database so any application instance can serve status and publish progress.
- Derive ownership from the authenticated caller, enforce owner-scoped status access and WebSocket delivery, and remove the fixed `saga-user` identity.
- Make repeated submissions and step execution safe through idempotency.

## Non-goals

- Replace the existing Nexus WebSocket endpoint or redesign its message protocol generally.
- Make GraphQL a second command path for this saga.
- Add a generic workflow engine, arbitrary saga definitions, or a user-facing saga administration console.
- Change the business meaning or order of the existing create-civilization, claim-region, and deploy-Nexus-node steps.

## Approaches

### REST canonical command (recommended)

Keep the existing saga controller as the sole creation command and extend it with a durable saga record, status lookup, progress events, and idempotency. This matches the current Saga controller and makes asynchronous progress and recovery explicit. It requires a persisted status model and adapting the React form from Apollo mutation input to the REST request/response contract.

### GraphQL canonical command

Route the existing `foundCivilization` mutation through the orchestrator and expose saga status through GraphQL, with progress still sent over Nexus WebSocket. This keeps the form on Apollo, but creates a second status surface and departs from the already exposed REST saga endpoint. It does not remove the need for durable state, idempotency, or authorization. This approach is not selected.

## Contract and data flow

1. The authenticated frontend submits `POST /api/v1/sagas/found-civilization` with `name`, `scale`, and numeric `regionId`, plus an `Idempotency-Key` unique to this user submission.
2. The backend validates the request, resolves the authenticated client identity, and atomically creates or reuses the owner's saga record. The record is the durable work queue: a worker on any application instance claims pending/expired work using a database lease or equivalent concurrency control. It records each step transition before/after its work, then runs create civilization → claim region → deploy Nexus node.
3. The command returns `202 Accepted` with `{ sagaId, status, steps, createdAt, updatedAt }`. A repeated key and identical request returns the existing saga representation; the same key with different request content returns `409 Conflict`.
4. `GET /api/v1/sagas/{id}` returns the same status representation only to its owner. Unknown IDs and IDs owned by another user both return `404` to avoid disclosing existence.
5. On each committed state transition, publish an event through the existing cross-instance Redis event infrastructure to the owner's connected `/ws/nexus` session(s), filtering by the authenticated session identity. Event envelope: `{ type: "SAGA_PROGRESS", payload: { sagaId, status, currentStep, steps, failure, compensation, updatedAt, sequence } }`. Each step entry identifies a stable step key, display label, and status. `sequence` increases monotonically per saga. Events are notifications; the GET representation is authoritative.
6. The frontend listens for matching saga IDs, renders step status, and refetches GET on a progress gap, reconnect, or ambiguous submit response. Reconnection does not replay events; GET restores the durable state.

The three step keys are `CREATE_CIVILIZATION`, `CLAIM_REGION`, and `DEPLOY_NEXUS_NODE`, with labels “Civilization created”, “Region claimed”, and “Nexus node deployed”. Step statuses are `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `COMPENSATING`, `COMPENSATED`, and `COMPENSATION_FAILED`.

## Saga states and errors

Saga status values are `PENDING`, `RUNNING`, `COMPENSATING`, `SUCCEEDED`, `FAILED`, `COMPENSATED`, and `COMPENSATION_FAILED`. The command persists the request for worker execution and returns `202 Accepted`; the UI uses the saga ID for event correlation and GET reconciliation. Invalid input returns `400 Bad Request`; unauthenticated requests return `401 Unauthorized`.

On a step failure, persist the failed step and a safe error code/message, then compensate successful earlier steps in reverse order. A fully compensated execution ends `COMPENSATED`; a compensation failure ends `COMPENSATION_FAILED` and identifies each affected step. `FAILED` is reserved for a failure before any side effect requiring compensation. Do not expose stack traces or secret/token values in REST or WebSocket errors. A terminal saga remains queryable and is not silently restarted by retrying the same idempotency key. Retries caused by worker interruption or a transient step/compensation error resume from persisted state and must be safe; retrying the POST only retrieves the existing saga and never restarts a terminal saga.

## Persistence and idempotency

Store saga ID, owner identity, idempotency key, normalized request fingerprint, overall status, current/failed step, per-step execution and compensation status, associated civilization ID, safe failure details, monotonic event sequence, worker lease/heartbeat, and timestamps in the existing relational database. Add a Flyway migration and repository-backed application service. Enforce a unique `(owner_id, idempotency_key)` constraint. Workers must claim work atomically so multiple instances cannot execute the same transition concurrently; expired leases make interrupted work recoverable. Do not use process-local maps as the source of truth.

Persist transitions transactionally around each step boundary. Side effects must be discoverable by the owning saga so a retry after a process restart can safely continue or compensate without creating duplicate civilizations, claims, or Nexus nodes. Claim and node creation must either use saga-scoped idempotency keys/unique constraints or verify-and-reuse the effect before applying it again. A compensation retry must be safe to repeat and must retain its result. The persisted record is the recovery journal; a worker must resume incomplete work after lease expiry and must not mark an operation successful until all three steps are confirmed. Redis events may be lost; GET reconciliation remains sufficient because each state is committed before it is published.

## Authentication and authorization

Require the same authenticated caller for POST and GET. Use the server-authenticated principal/client ID as saga owner and civilization owner; ignore caller-supplied owner fields. Enforce owner checks in both status lookup and event fan-out. The current WebSocket handshake stores `X-Client-Id` in session attributes; use that identity to deliver saga events only to the owning client's sessions. Do not broadcast private saga progress to every Nexus connection. Continue requiring the existing JWT-authenticated `/ws/nexus` handshake.

## Compatibility

- Keep `/api/v1/sagas/found-civilization` and `/ws/nexus`; add GET status and typed saga event support.
- Keep `/ws/voxtex` as its existing compatibility alias; event names and node labels use current Nexus terminology.
- The form switches from `CREATE_CIVILIZATION` to the REST saga command and uses region IDs. The GraphQL `foundCivilization` mutation may remain for existing API consumers but must not be used by this form or represented as the orchestrated saga.
- Preserve existing successful civilization and Nexus node semantics. Existing clients that call the REST saga endpoint receive the expanded `202` response and must tolerate progress being asynchronous.

## Verification and acceptance criteria

- Form submits `name`, `scale`, `regionId`, and an idempotency key; it cannot submit a display name as the region identifier.
- Creation progresses through all three named steps and produces a durable `SUCCEEDED` status.
- The form displays live typed progress and reconciles correct status after reload, WebSocket reconnect, missed event, and repeated POST with the same key.
- Failure at each step reports the failed step and the final compensation result; retrying execution or compensation does not duplicate effects.
- Status and WebSocket events are visible only to the saga owner; fixed `saga-user` is removed from the flow.
- Saga state and idempotency survive application restart and are readable through any application instance.
- The documented REST contract, event envelope, statuses, conflict behavior, authorization, and recovery behavior are covered by implementation verification before release.

## Open implementation choices

The builder may choose JPA entity/repository shape, lease duration, worker polling cadence, and transient-error backoff. Redis is the cross-instance progress transport. These choices must preserve the contracts and guarantees above; a process restart must never leave the UI with an unqueryable or falsely successful operation.
