# Found Civilization Saga Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make civilization creation a durable, authenticated three-step saga with queryable status and owner-scoped real-time progress.

**Architecture:** Keep `POST /api/v1/sagas/found-civilization` as the canonical command. Persist only this saga's execution and three fixed steps in the relational database; workers claim pending/expired rows, perform idempotent effects, and publish typed progress through Redis to owner-matched `/ws/nexus` sessions. The React form submits a numeric region ID, displays status, and reconciles through the status endpoint after reconnects or missed events.

**Tech Stack:** Java 25, Spring Boot MVC/JPA, Flyway, PostgreSQL, Redis Pub/Sub; React 19, TypeScript, Apollo Client for region/civilization reads, Vitest and Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-23-saga-creation-progress-design.md`

## Global Constraints

- Use `POST /api/v1/sagas/found-civilization` as the canonical creation command.
- Return a durable saga ID and status, with `GET /api/v1/sagas/{id}` for reconciliation.
- Publish typed progress events over the existing `/ws/nexus` connection and let the frontend reconcile through GET after reconnecting.
- Update `CreateCivilizationForm` to select and submit a `regionId` rather than a region name, and show the three named steps, failures, and compensation outcomes.
- Persist saga state in the relational database so any application instance can serve status and publish progress.
- Derive ownership from the authenticated caller, enforce owner-scoped status access and WebSocket delivery, and remove the fixed `saga-user` identity.
- Make repeated submissions and step execution safe through idempotency.
- Persist a step as `POTENTIALLY_APPLIED` before invoking it; compensate that step and all earlier potentially applied steps in reverse order.
- Retry only timeout, connectivity, and 5xx infrastructure failures: up to three automatic attempts total, with exponential backoff. Do not retry domain/validation failures.
- The same owner, idempotency key, and payload always returns the same saga; a different payload with that key returns `409 Conflict`.
- Keep this implementation specific to the fixed steps create civilization → claim region → deploy Nexus node; do not add a general-purpose job or workflow framework.
- Changing the `/api/v1/sagas/found-civilization` v1 response contract is intentional; do not add a parallel legacy endpoint. Preserve and document `status`, `civilizationId`, and `name` when meaningful.
- Keep `/ws/voxtex` as the existing compatibility alias; use Nexus terminology for new event names and labels.

## Review Focus

- Repeated submissions with the same key must return the same saga; a changed payload with that key must return `409` — pin in Task 3's `submitReturnsExistingSagaForSameKeyAndPayload` and `submitRejectsSameKeyWithDifferentPayload` tests.
- A side effect that partially applies and then fails must remain discoverable and compensable; compensation includes the failing step before earlier steps in reverse order — pin in Task 2's `failedPartialEffectIsFindableForCompensation` test and Task 3's `compensatesFailedStepAndEarlierStepsInReverse` test.
- Infrastructure failures must stop after three total attempts, domain failures must not retry, and attempt counts must survive worker restart — pin in Task 3's `retriesTransientFailureAtMostThreeAttempts`, `doesNotRetryDomainFailure`, and `expiredLeaseContinuesPersistedAttemptCount` tests.
- Missing authentication and another owner's ID must not permit status access, and progress must not reach another user's socket — pin in Task 4's `getRequiresAuthentication` and `getReturnsNotFoundForForeignSaga` tests and Task 5's `sendsSagaEventOnlyToMatchingClientSessions` test.
- A reconnect or event sequence gap must reconcile from GET, and out-of-order/stale events must not regress displayed state — pin in Task 6's `reconcilesAfterReconnectAndIgnoresOlderSequence` test.

---

## File Map

### Backend persistence and fixed saga effects

- Create `src/main/resources/db/migration/V23__found_civilization_saga.sql`: saga execution and step rows, idempotency uniqueness, execution leases, step attempt/error fields, and nullable saga correlation columns/unique constraints needed to find civilization and Nexus-node effects after interruption.
- Create `src/main/java/io/github/opencivilizationplatform/saga/SagaExecution.java` and `SagaStepExecution.java`: JPA records for one found-civilization execution and its three fixed steps.
- Create `SagaStatus.java` and `SagaStepStatus.java`: the exact status values from the spec.
- Create `SagaExecutionRepository.java` and `SagaStepExecutionRepository.java`: owner/key lookups, status lookup, and atomic due-work claiming.
- Modify `src/main/java/io/github/opencivilizationplatform/modules/civilization/domain/Civilization.java`, `modules/civilization/infrastructure/CivilizationRepository.java`, and `modules/civilization/application/CivilizationService.java`: correlate civilization creation to saga ID and return the existing effect on replay.
- Modify `src/main/java/io/github/opencivilizationplatform/modules/region/application/ResourceRegionService.java`: make claim/unclaim replay-safe and avoid unclaiming a region now owned by another civilization.
- Modify `src/main/java/io/github/opencivilizationplatform/modules/nexus/domain/NexusNode.java`, `modules/nexus/infrastructure/NexusNodeRepository.java`, and `modules/nexus/application/NexusMeshService.java`: correlate node creation to saga ID and return the existing node on replay.
- Modify `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationContext.java` and `FoundCivilizationSagaSteps.java`: pass saga ID to effects and make each step and compensation safe after partial application.
- Create `src/test/java/io/github/opencivilizationplatform/saga/SagaExecutionRepositoryTest.java`, `FoundCivilizationSagaStepsTest.java`, and `FoundCivilizationSagaPersistenceIntegrationTest.java`.

### Durable execution and REST contract types

- Create `src/main/java/io/github/opencivilizationplatform/saga/SagaStatusResponse.java`, `SagaStepView.java`, `SagaFailureView.java`, `SagaCompensationView.java`, `FoundCivilizationRequest.java`, and `SagaIdempotencyConflictException.java` for shared service and REST contracts.
- Create `FoundCivilizationSagaService.java`: submit/retrieve an owner-scoped execution and persist lifecycle transitions.
- Create `SagaProgressPublisher.java`: worker-facing interface for publishing a committed status snapshot; Task 5 supplies its Redis implementation.
- Create `FoundCivilizationSagaWorker.java`: claim due saga rows and execute only the three fixed steps, with the persisted retry policy and lease recovery.
- Modify `src/main/java/io/github/opencivilizationplatform/saga/SagaController.java`: authenticated POST returning `202` and owner-scoped GET status.
- Modify `src/main/java/io/github/opencivilizationplatform/config/SecurityConfig.java`: make GET `/api/v1/sagas/{id}` authenticated before the current broad public GET matcher.
- Create `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaServiceTest.java`, `FoundCivilizationSagaWorkerTest.java`, and `SagaControllerTest.java`.

### WebSocket progress delivery

- Create `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressEvent.java`, `SagaProgressPayload.java`, `RedisSagaProgressPublisher.java`, and `SagaProgressRedisSubscriber.java`: publish committed progress via a dedicated Redis topic and deliver received messages to local matching sessions.
- Modify `src/main/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandler.java`: retain authenticated `X-Client-Id` per session and add owner-targeted event sending while preserving existing Nexus broadcasts.
- Modify `src/main/java/io/github/opencivilizationplatform/config/RedisEventConfig.java`: subscribe the saga progress listener without parsing saga events as Nexus message DTOs.
- Create `src/test/java/io/github/opencivilizationplatform/saga/SagaProgressRedisSubscriberTest.java` and `src/test/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandlerTest.java`.

### Frontend integration

- Create `frontend/src/api/sagas.ts`: typed submit/status requests with `Idempotency-Key` and `Authorization: Bearer` headers.
- Create `frontend/src/auth/session.ts`: obtain a JWT/client ID from existing `POST /api/v1/auth/connect` and cache that token for the browser session.
- Modify `frontend/src/hooks/useRealtimeUpdates.ts`: expose typed `SAGA_PROGRESS` events and reconnect callbacks without changing existing event callbacks.
- Modify `frontend/src/components/CreateCivilizationForm.tsx`: query available regions using existing `GET_REGIONS`, submit `regionId`, display the three-step lifecycle, errors/rollback, and reconcile status.
- Modify `frontend/src/pages/Dashboard.tsx` to initialize the browser session, pass its token to realtime and form flows, and pass connection state into the form.
- Create `frontend/src/test/session.test.ts`, `frontend/src/test/CreateCivilizationForm.test.tsx`, and `frontend/src/test/useRealtimeUpdates.test.tsx`.

## Task Interfaces

- Persistence produces `SagaExecutionRepository.findByOwnerIdAndIdempotencyKey(String ownerId, String key)`, `findByIdAndOwnerId(UUID sagaId, String ownerId)`, and an atomic `claimNextDue(Instant now, Duration lease)` operation. Each saga has exactly the ordered keys `CREATE_CIVILIZATION`, `CLAIM_REGION`, and `DEPLOY_NEXUS_NODE`.
- Step effects accept `UUID sagaId` and are replay-safe. `FoundCivilizationSagaSteps` consumes the saga ID plus request and owner data; it exposes the same three fixed step operations and compensations to the worker.
- Domain effect signatures: `CivilizationService.createCivilizationForSaga(UUID sagaId, String name, CivilizationScale scale, String region, String ownerId): Civilization`; `ResourceRegionService.claimRegionForSaga(UUID sagaId, Long regionId, Long civilizationId): ResourceRegion`; `ResourceRegionService.unclaimRegionForSaga(UUID sagaId, Long regionId, Long civilizationId): ResourceRegion`; `NexusMeshService.registerNodeForSaga(UUID sagaId, String name, NexusNodeType type, String region, Long civilizationId, String knowledgeBase): NexusNode`. The saga correlation on civilization/node plus existing `claimedByCivilizationId` makes each replay discoverable.
- DTO contract: `SagaStatusResponse(UUID sagaId, SagaStatus status, List<SagaStepView> steps, Long civilizationId, String name, long sequence, Instant createdAt, Instant updatedAt)`. Each `SagaStepView` has `key`, `label`, `status`, `attemptCount`, `errorCode`, and `errorMessage`. `SagaProgressEvent` has type `SAGA_PROGRESS` and a payload with `sagaId`, `status`, `currentStep`, `steps`, `failure`, `compensation`, `updatedAt`, and `sequence`. `failure` carries `stepKey`, `code`, and safe `message`; `compensation` carries status and failed step keys.
- `FoundCivilizationSagaService.submit(FoundCivilizationRequest request, String ownerId, String idempotencyKey): SagaStatusResponse`; `getStatus(UUID sagaId, String ownerId): SagaStatusResponse`. The worker calls `processNextDueSaga(): void` and publishes only after a status transaction commits.
- Task 3's `SagaProgressPublisher.publish(String ownerId, UUID sagaId, SagaStatusResponse snapshot, String currentStep, SagaFailureView failure, SagaCompensationView compensation): void` is the worker-facing interface; Task 5 adapts it to the typed WebSocket event envelope.
- `SagaController` derives `ownerId` from `Authentication.getName()`; caller input never contains owner identity. Both HTTP calls use the same JWT identity as the WebSocket handshake's `X-Client-Id`.
- Frontend `ensureSession(): Promise<{token: string; clientId: string}>` calls `POST /api/v1/auth/connect` once per browser session and reuses the returned JWT. `submitFoundCivilization(input, idempotencyKey, token)` and `getFoundCivilizationSaga(sagaId, token)` return `Promise<SagaStatusResponse>`. Pass the exact same JWT to REST as a bearer token and to `/ws/nexus?token=...`; do not add login/register or change issue #4.
- The current frontend has no auth token provider: `main.tsx` builds Apollo without an auth link, and Dashboard calls `useRealtimeUpdates` without a token. Add only browser-session bootstrap through `/api/v1/auth/connect` and share its JWT across saga REST and WebSocket calls.

## Implementation Tasks

### Task 1: Persist the fixed saga execution and step records

**Files:**
- Create: `src/main/resources/db/migration/V23__found_civilization_saga.sql`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaExecution.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStepExecution.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStatus.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStepStatus.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaExecutionRepository.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStepExecutionRepository.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/SagaExecutionRepositoryTest.java`

**Interfaces:**
- Produces: UUID saga ID; unique owner/idempotency-key mapping; fixed three-step rows; status, step attempt/error, sequence, and lease data; owner-scoped status lookup; atomic due-work claim.
- Consumes: existing JPA/Flyway setup and exact status/retry requirements in the spec.

- [ ] **Step 1: Write repository tests**

Add tests `savesThreeStepsInDeclaredOrder`, `enforcesOwnerAndIdempotencyKeyUniqueness`, `findsStatusOnlyForMatchingOwner`, and `claimsDueExecutionOnlyOnce`. Assert statuses and stable step keys exactly match the spec.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=SagaExecutionRepositoryTest test`
Expected: FAIL because saga persistence classes and migration do not exist.

- [ ] **Step 3: Add the migration and JPA model**

Create saga and step tables, unique `(owner_id, idempotency_key)`, step `(saga_id, step_key)`, due-work/lease columns, monotonic sequence, attempt/error fields, and nullable unique saga IDs on civilization and Nexus node rows. Use UUID IDs and UTC instants. Define only the fixed three ordered step keys; do not create a configurable workflow schema.

- [ ] **Step 4: Implement repository lookup and atomic claiming**

Implement `findByOwnerIdAndIdempotencyKey`, `findByIdAndOwnerId`, and `claimNextDue(Instant now, Duration lease)` with database-level atomicity so concurrent instances cannot claim the same active lease.

- [ ] **Step 5: Run the focused tests and verify they pass**

Run: `.\mvnw.cmd -Dtest=SagaExecutionRepositoryTest test`
Expected: PASS, including competing claimers and uniqueness behavior.

- [ ] **Step 6: Commit the persistence task**

```bash
git add src/main/resources/db/migration/V23__found_civilization_saga.sql src/main/java/io/github/opencivilizationplatform/saga src/test/java/io/github/opencivilizationplatform/saga/SagaExecutionRepositoryTest.java
git commit -m "feat: persist found civilization saga state"
```

### Task 2: Make the three domain effects and compensations idempotent

**Files:**
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/civilization/domain/Civilization.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/civilization/infrastructure/CivilizationRepository.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/civilization/application/CivilizationService.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/region/application/ResourceRegionService.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/nexus/domain/NexusNode.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/nexus/infrastructure/NexusNodeRepository.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/nexus/application/NexusMeshService.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationContext.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaSteps.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaStepsTest.java`

**Interfaces:**
- Consumes: Task 1's nullable unique saga-correlation columns and the three fixed step keys.
- Produces: `createCivilization(UUID sagaId, ...)`, `claimRegion(UUID sagaId, regionId, civilizationId)`, `deployNexusNode(UUID sagaId, ...)` and matching idempotent compensation operations. A replay returns or verifies the same effect rather than creating another.

- [ ] **Step 1: Write tests for replay and partial effects**

Add `repeatedCreateReturnsSameCivilization`, `repeatedClaimKeepsSameOwner`, `unclaimDoesNotClearDifferentCivilizationClaim`, `repeatedNodeDeploymentReturnsSameNode`, `compensationIsSafeWhenEffectIsAbsent`, and `failedPartialEffectIsFindableForCompensation`. Simulate a side effect becoming durable before throwing, then assert a lookup by saga ID finds the effect so its compensation can run.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaStepsTest test`
Expected: FAIL because step operations currently lack saga IDs and replay-safe lookup.

- [ ] **Step 3: Add saga-scoped civilization and node lookups**

Add repository queries by saga ID. Add service methods that return an existing effect when the same saga ID is presented and persist new effects with that ID otherwise. Preserve existing service methods for non-saga callers.

- [ ] **Step 4: Make region claim and compensation owner-conditional**

Treat an existing claim by the same civilization as success. Reject a claim by another civilization as a domain failure. During compensation, unclaim only when `claimedByCivilizationId` still equals the saga's civilization ID.

- [ ] **Step 5: Pass saga ID through the context and fixed steps**

Update `FoundCivilizationContext` and `FoundCivilizationSagaSteps` to pass saga ID to each effect. Record the civilization ID as soon as it is discoverable. Make each compensation idempotent, including when a side effect partially applied before throwing.

- [ ] **Step 6: Run focused tests and verify they pass**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaStepsTest test`
Expected: PASS with duplicate effects avoided and compensation in reverse order.

- [ ] **Step 7: Commit the domain-effect task**

```bash
git add src/main/java/io/github/opencivilizationplatform/modules/civilization src/main/java/io/github/opencivilizationplatform/modules/region/application/ResourceRegionService.java src/main/java/io/github/opencivilizationplatform/modules/nexus src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationContext.java src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaSteps.java src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaStepsTest.java
git commit -m "feat: make civilization saga effects idempotent"
```

### Task 3: Implement durable submission, worker recovery, and retry policy

**Files:**
- Create: `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaService.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationRequest.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStatusResponse.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaStepView.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaFailureView.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaCompensationView.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaIdempotencyConflictException.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressPublisher.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaWorker.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/saga/SagaOrchestrator.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/saga/SagaException.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaServiceTest.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaWorkerTest.java`

**Interfaces:**
- Consumes: Task 1 repositories/statuses and Task 2 idempotent fixed step effects.
- Produces: `submit(FoundCivilizationRequest request, String ownerId, String idempotencyKey): SagaStatusResponse`; `getStatus(UUID sagaId, String ownerId): SagaStatusResponse`; a worker that claims due rows and advances only the three fixed steps.
- The Task 3 worker calls `SagaProgressPublisher.publish(...)` only after the matching status transaction commits; Task 5 supplies the Redis adapter without changing that interface.

- [ ] **Step 1: Write submission idempotency tests**

Add `submitReturnsExistingSagaForSameKeyAndPayload`, `submitRejectsSameKeyWithDifferentPayload`, and `newSubmissionPersistsPendingStepsBeforeReturning`. Assert duplicate requests return the same UUID and do not enqueue twice.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaServiceTest test`
Expected: FAIL because durable submission and idempotency are not implemented.

- [ ] **Step 3: Implement owner-scoped submit and status service**

Normalize the request for fingerprinting. Persist saga plus exactly three pending steps in one transaction. Return the existing row for the same owner/key/fingerprint; raise a conflict for a different fingerprint. Return not-found for unknown or foreign-owned IDs.

- [ ] **Step 4: Write worker state-machine and retry tests**

Add `persistsPotentiallyAppliedBeforeCallingStep`, `retriesTransientFailureAtMostThreeAttempts`, `doesNotRetryDomainFailure`, `failsWithoutCompensationWhenNoEffectWasApplied`, `compensatesFailedStepAndEarlierStepsInReverse`, `persistsCompensationFailure`, and `expiredLeaseContinuesPersistedAttemptCount`. Verify total attempts never exceed three and backoff grows exponentially without sleeping by injecting a clock/scheduler.

- [ ] **Step 5: Run worker tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaWorkerTest test`
Expected: FAIL because there is no persisted worker state machine.

- [ ] **Step 6: Implement fixed worker execution and recovery**

Claim with a database lease; persist each step as `POTENTIALLY_APPLIED` before invoking it. Retry only timeouts, connectivity failures, and HTTP 5xx, at most three attempts total with exponential backoff; persist attempt count/classification. Do not retry domain failures. On failure, compensate the potentially applied failed step and earlier potentially applied steps in reverse order. Persist all compensation outcomes and finish as `COMPENSATED` or `COMPENSATION_FAILED` as specified.

- [ ] **Step 7: Run both focused test classes and verify they pass**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest test`
Expected: PASS, including worker recovery with an already-used attempt count.

- [ ] **Step 8: Commit durable execution**

```bash
git add src/main/java/io/github/opencivilizationplatform/saga src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaServiceTest.java src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaWorkerTest.java
git commit -m "feat: execute found civilization saga durably"
```

### Task 4: Expose authenticated REST submission and status

**Files:**
- Modify: `src/main/java/io/github/opencivilizationplatform/saga/SagaController.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/config/SecurityConfig.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/config/GlobalExceptionHandler.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/SagaControllerTest.java`

**Interfaces:**
- Consumes: Task 3 service methods and `Authentication.getName()` as the owner ID.
- Produces: `POST /api/v1/sagas/found-civilization` returns `202` and the status DTO; `GET /api/v1/sagas/{id}` returns the same DTO scoped to the principal. DTO retains `status`, `civilizationId`, and `name`, with `sagaId`, steps, sequence, and timestamps.

- [ ] **Step 1: Write MVC contract and authorization tests**

Add `postReturnsAcceptedWithSagaIdAndLegacyFields`, `getReturnsOwnedSaga`, `getReturnsNotFoundForForeignSaga`, `postRequiresAuthentication`, `getRequiresAuthentication`, and `sameKeyDifferentPayloadReturnsConflict`. Use MockMvc and mocked service calls to assert HTTP codes and JSON field names.

- [ ] **Step 2: Run controller tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=SagaControllerTest test`
Expected: FAIL because GET and the new async DTO contract do not exist and GET is currently matched by a public rule.

- [ ] **Step 3: Add status DTOs and adapt controller**

Make POST validate `name`, `scale`, `regionId`, and `Idempotency-Key`; derive owner from `Authentication`; delegate to `submit`; return `202`. Add owner-scoped UUID GET and preserve existing response field names when values are available.

- [ ] **Step 4: Enforce authentication for saga GET**

Add the specific GET `/api/v1/sagas/{id}` matcher before the broad public GET `/api/v1/**` matcher. Keep the existing JWT filter and `/ws/**` security configuration intact.

- [ ] **Step 5: Map conflict and validation errors safely**

Map idempotency conflicts to `409`, request validation to `400`, unauthenticated requests to `401`, and owner-mismatched/unknown IDs to `404`. Never expose stack traces or token values.

- [ ] **Step 6: Run controller tests and verify they pass**

Run: `.\mvnw.cmd -Dtest=SagaControllerTest test`
Expected: PASS, including no status disclosure across owners.

- [ ] **Step 7: Commit REST contract**

```bash
git add src/main/java/io/github/opencivilizationplatform/saga src/main/java/io/github/opencivilizationplatform/config/SecurityConfig.java src/main/java/io/github/opencivilizationplatform/config/GlobalExceptionHandler.java src/test/java/io/github/opencivilizationplatform/saga/SagaControllerTest.java
git commit -m "feat: expose authenticated saga status API"
```

### Task 5: Publish owner-scoped saga progress across instances

**Files:**
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressEvent.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressPayload.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/RedisSagaProgressPublisher.java`
- Create: `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressRedisSubscriber.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/config/RedisEventConfig.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandler.java`
- Test: `src/test/java/io/github/opencivilizationplatform/saga/SagaProgressRedisSubscriberTest.java`
- Test: `src/test/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandlerTest.java`

**Interfaces:**
- Consumes: Task 3 `SagaProgressPublisher` contract; session identity is `X-Client-Id` set during the existing JWT handshake.
- Produces: publish `SAGA_PROGRESS` on a saga-specific Redis topic after the database transition commits; each app instance forwards it only to local sessions whose client ID matches `ownerId`.
- Redis adapter implements the exact Task 3 publisher signature and converts its arguments into `SagaProgressEvent`; subscriber routes that typed event to `NexusWebSocketHandler.sendToClient(String clientId, String json): void`.

- [ ] **Step 1: Write publisher/subscriber and session privacy tests**

Add `publishesTypedEventAfterTransitionCommit`, `parsesSagaEventsSeparatelyFromNexusMessageDTO`, and `sendsSagaEventOnlyToMatchingClientSessions`. Assert the event contains saga ID, sequence, step statuses, failure, and compensation; assert no foreign socket receives it.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: `.\mvnw.cmd -Dtest=SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest test`
Expected: FAIL because WebSocket broadcast is currently global and Redis listener accepts only Nexus-message events.

- [ ] **Step 3: Add event DTOs and committed-transition publisher**

Create the exact `SAGA_PROGRESS` envelope from the spec. Publish only after each state transition commits; keep GET as source of truth if Redis Pub/Sub drops an event.

- [ ] **Step 4: Add a dedicated Redis topic and subscriber**

Subscribe to saga progress separately from `Nexus-mesh-events`; deserialize the saga event type and route to the local WebSocket handler without passing it through `NexusMessageSyncDTO` parsing.

- [ ] **Step 5: Add owner-targeted WebSocket sends**

Capture the authenticated client ID from session attributes and add a targeted send method. Preserve existing Nexus broadcast behavior and both `/ws/nexus` and `/ws/voxtex` routes.

- [ ] **Step 6: Run focused tests and verify they pass**

Run: `.\mvnw.cmd -Dtest=SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest test`
Expected: PASS, including cross-instance delivery through the subscriber and per-owner filtering.

- [ ] **Step 7: Commit progress delivery**

```bash
git add src/main/java/io/github/opencivilizationplatform/saga src/main/java/io/github/opencivilizationplatform/config/RedisEventConfig.java src/main/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandler.java src/test/java/io/github/opencivilizationplatform/saga/SagaProgressRedisSubscriberTest.java src/test/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandlerTest.java
git commit -m "feat: publish owner-scoped saga progress"
```

### Task 6: Integrate the form with REST, regions, and realtime reconciliation

**Files:**
- Create: `frontend/src/api/sagas.ts`
- Create: `frontend/src/auth/session.ts`
- Modify: `frontend/src/hooks/useRealtimeUpdates.ts`
- Modify: `frontend/src/components/CreateCivilizationForm.tsx`
- Modify: `frontend/src/pages/Dashboard.tsx`
- Test: `frontend/src/test/session.test.ts`
- Test: `frontend/src/test/CreateCivilizationForm.test.tsx`
- Test: `frontend/src/test/useRealtimeUpdates.test.tsx`

**Interfaces:**
- Consumes: Task 4 REST response/GET contract, Task 3 publisher interface, and Task 5 event envelope.
- Produces: `ensureSession(): Promise<{token: string; clientId: string}>` using `POST /api/v1/auth/connect`; `submitFoundCivilization(input, key, token): Promise<SagaStatusResponse>`; `getFoundCivilizationSaga(id, token): Promise<SagaStatusResponse>`; a typed `onSagaProgress` realtime callback. Reuse one session JWT for POST, GET, and WebSocket. Form submits `regionId`, retains saga ID, and ignores events for other IDs or older sequence numbers.

- [ ] **Step 1: Write failing API and form tests**

Test `connectsOnceAndCachesTokenForBrowserSession` in `session.test.ts`, `parsesOnlySagaProgressEvents` in the hook tests, plus `postsRegionIdAndIdempotencyKey`, `rendersThreeNamedStepsFromAcceptedResponse`, `showsFailedStepAndCompensationOutcome`, `reconcilesAfterReconnectAndIgnoresOlderSequence`, and `refetchesStatusAfterReconnectOrSequenceGap` in the form tests. Assert `POST /api/v1/auth/connect` is called once, the returned JWT is reused for REST and WebSocket, and the form sends a numeric region ID rather than its display name.

- [ ] **Step 2: Run the focused frontend tests and verify they fail**

Run: `cd frontend && npm test -- --run src/test/session.test.ts src/test/CreateCivilizationForm.test.tsx`
Expected: FAIL because the session helper is absent and the form currently uses `CREATE_CIVILIZATION` with a free-text region.

- [ ] **Step 3: Add typed REST client**

Implement `submitFoundCivilization(input, idempotencyKey, token)` and `getFoundCivilizationSaga(sagaId, token)` with `Idempotency-Key` and `Authorization: Bearer ${token}` headers. Parse `202`/JSON and surface safe API errors; do not generate a new key for a network retry of the same submit.

- [ ] **Step 4: Add typed realtime event handling**

Extend `RealtimeConfig` with `onSagaProgress`; parse only `type === "SAGA_PROGRESS"` into the typed payload. Preserve all existing WebSocket callbacks and notify the consumer on reconnect/status change. Do not connect until the session token is loaded; supply the same token obtained by `ensureSession()`.

- [ ] **Step 5: Replace free-text region and GraphQL create in the form**

Use existing `GET_REGIONS` to render available regions keyed by ID. Submit the selected region ID and stable idempotency key; render pending/in-progress/succeeded/failed and compensation states using the approved labels. Fetch GET after reconnect, sequence gap, or ambiguous submit. Prevent older events from regressing displayed sequence/status. Keep progress visible until a terminal status; on `SUCCEEDED`, notify Dashboard to refetch civilization data.

- [ ] **Step 6: Bootstrap and wire the shared auth token**

Implement `ensureSession()` in `frontend/src/auth/session.ts`: call existing `POST /api/v1/auth/connect` once per browser session and cache returned `token` and `clientId` in `sessionStorage`. The endpoint has no request body and returns `{token, clientId, expiresIn}`. Pass the same token to saga POST/GET and `/ws/nexus?token=...`, and pass reconnect state into the form. Do not add login/register or change issue #4.

- [ ] **Step 7: Run focused frontend tests and verify they pass**

Run: `cd frontend && npm test -- --run src/test/session.test.ts src/test/CreateCivilizationForm.test.tsx src/test/useRealtimeUpdates.test.tsx`
Expected: PASS, including reconnect reconciliation and stale sequence handling.

- [ ] **Step 8: Run frontend typecheck/build**

Run: `cd frontend && npm run build`
Expected: TypeScript and Vite production build pass.

- [ ] **Step 9: Commit frontend integration**

```bash
git add frontend/src/api/sagas.ts frontend/src/auth/session.ts frontend/src/hooks/useRealtimeUpdates.ts frontend/src/components/CreateCivilizationForm.tsx frontend/src/pages/Dashboard.tsx frontend/src/test/session.test.ts frontend/src/test/CreateCivilizationForm.test.tsx frontend/src/test/useRealtimeUpdates.test.tsx
git commit -m "feat: show found civilization saga progress"
```

### Task 7: Verify complete persistence, auth, and recovery flow

**Files:**
- Test: `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaPersistenceIntegrationTest.java`
- Modify only if required to pass: the files owned by Tasks 1–6.

**Interfaces:**
- Consumes: Task 1–6 persisted schema, API DTOs, worker, owner-scoped events, and form behavior.
- Produces: one integration proof that submission, restart/lease recovery, owner isolation, terminal status, and frontend reconciliation match the approved spec.

- [ ] **Step 1: Write integration scenarios**

Cover accepted submission followed by `SUCCEEDED`, same-key replay returning the same saga, different-payload conflict, interruption after potential application followed by lease recovery, compensation failure persistence, and status/event owner isolation.

- [ ] **Step 2: Run the integration test and verify failures are meaningful**

Run: `.\mvnw.cmd -Dtest=FoundCivilizationSagaPersistenceIntegrationTest test`
Expected: failures identify any gap between in-memory collaborators, database persistence, and cross-instance behavior.

- [ ] **Step 3: Fix only contract gaps and rerun focused integration**

Keep changes within the fixed Found Civilization flow. Do not add generic workflow abstractions or a legacy endpoint.

- [ ] **Step 4: Run relevant backend and frontend verification**

Run: `.\mvnw.cmd -Dtest=SagaExecutionRepositoryTest,FoundCivilizationSagaStepsTest,FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest,SagaControllerTest,SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest,FoundCivilizationSagaPersistenceIntegrationTest test`
Expected: PASS.

Run: `cd frontend && npm test -- --run src/test/session.test.ts src/test/CreateCivilizationForm.test.tsx src/test/useRealtimeUpdates.test.tsx && npm run build`
Expected: PASS for saga UI/realtime tests and frontend build.

- [ ] **Step 5: Commit final verification fixes**

```bash
git add src/main/java/io/github/opencivilizationplatform/saga src/main/java/io/github/opencivilizationplatform/modules/civilization src/main/java/io/github/opencivilizationplatform/modules/region/application/ResourceRegionService.java src/main/java/io/github/opencivilizationplatform/modules/nexus src/main/java/io/github/opencivilizationplatform/config src/main/java/io/github/opencivilizationplatform/web/handler/NexusWebSocketHandler.java src/test/java/io/github/opencivilizationplatform/saga frontend/src
git commit -m "test: verify found civilization saga recovery"
```
