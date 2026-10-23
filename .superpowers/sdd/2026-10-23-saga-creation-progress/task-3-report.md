# Task 3 — durable FoundCivilization execution

## Status and commits

- Implementation commit: `bca51ef` (`feat: execute found civilization saga durably`).
- The report is committed separately so it can name the implementation commit.
- No PR or push was made. No spec, plan, ledger, or future-task files were changed.

## Scope

Created:

- `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationRequest.java`
- `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaService.java`
- `src/main/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaWorker.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaCompensationView.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaFailureView.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaIdempotencyConflictException.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaProgressPublisher.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaStatusResponse.java`
- `src/main/java/io/github/opencivilizationplatform/saga/SagaStepView.java`
- `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaServiceTest.java`
- `src/test/java/io/github/opencivilizationplatform/saga/FoundCivilizationSagaWorkerTest.java`

Modified:

- `src/main/java/io/github/opencivilizationplatform/saga/SagaOrchestrator.java` — exposes the three fixed steps to the durable worker.
- `src/main/java/io/github/opencivilizationplatform/saga/SagaException.java` — adds a message-only constructor for not-found/conflict paths.
- `src/main/java/io/github/opencivilizationplatform/saga/SagaController.java` — removes the duplicate package-local request record required to compile the standalone request type. Task 4 owns endpoint replacement.

## Implementation

- `submit` normalizes the name and default scale, hashes the normalized request, persists the saga and exactly three pending steps in one transaction, and returns the existing saga for an owner/key/fingerprint match. A different fingerprint raises `SagaIdempotencyConflictException`. A unique-constraint race is resolved by re-reading the committed owner/key row.
- `getStatus` scopes lookup by owner and returns the same not-found exception for absent and foreign-owned IDs.
- The worker polls due rows on a database lease, executes only the fixed three steps, and places the persisted saga UUID in `FoundCivilizationContext`. Every execution attempt is committed as `POTENTIALLY_APPLIED` before the effect call.
- Transient timeout, connectivity, and HTTP 5xx errors schedule exponential backoff after attempts one and two. Attempt three is terminal. Domain errors do not retry. Attempt counts and error classifications are persisted, including across an expired lease.
- On terminal failure, compensation includes the failed potentially applied step and prior attempted steps in reverse order. Each outcome is stored. The saga finishes as `FAILED` if no step effect was attempted, otherwise `COMPENSATED` or `COMPENSATION_FAILED`.
- `SagaProgressPublisher.publish` receives a status snapshot after each transition transaction returns. With no adapter bean, the worker uses a no-op publisher; Task 5 can provide Redis through this interface.

## TDD and verification

- Submission RED: `./mvnw.cmd -Dtest=FoundCivilizationSagaServiceTest test` failed during test compilation because the service and conflict exception did not yet exist.
- Submission GREEN: same command passed, 4 tests, 0 failures/errors.
- Worker RED: `./mvnw.cmd -Dtest=FoundCivilizationSagaWorkerTest test` failed during test compilation because the worker did not yet exist.
- Worker GREEN: worker tests passed after implementation, including retry limit, exponential timing, expired lease recovery, compensation order/failure, and committed progress publication.
- Final focused command: `./mvnw.cmd '-Dtest=FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest' test` passed, 13 tests, 0 failures/errors, Maven `BUILD SUCCESS`.
- Full `./mvnw.cmd test` ran 320 tests across 88 reports: **0 assertion failures, 17 errors, 1 skipped**. The errors are in seven existing integration/smoke classes: `CivilizationFlowIntegrationTest`, `RedisCacheIntegrationTest`, `RedisRateLimiterIntegrationTest`, `ShedLockIntegrationTest`, `CortexBusinessSmokeTest`, `DockerBuildSmokeTest`, and `RedisPubSubIntegrationTest`. The underlying environmental failures were unavailable Docker/Testcontainers and connection refused at `localhost:6379` for Redis. The Task 3 service and worker classes passed in that full run (4 and 9 tests respectively), as did `FoundCivilizationSagaStepsTest` (8 tests).
- `git diff --cached --check` passed before the implementation commit.

## Self-review and concerns

- Task 4 still needs to replace the legacy synchronous controller route with `submit`/`getStatus`; this task changed only the duplicate record in that file.
- The worker uses a five-minute lease and records heartbeat at step transitions. A single external effect taking longer than the lease may be picked up concurrently; Task 2's saga-keyed idempotent effects protect the business changes, but the worker does not currently renew a lease during a long blocking effect.
- The existing files in this repository are stored with CRLF while the active Git `core.autocrlf` setting normalizes staged content to LF. The three modified Java files consequently show line-ending churn in commit `bca51ef`, though their semantic edits are limited to those listed above.
