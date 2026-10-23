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

## Review fix round 1

- The publisher now exposes the required Task 5 adapter contract: `publish(String ownerId, UUID sagaId, SagaStatusResponse snapshot, String currentStep, SagaFailureView failure, SagaCompensationView compensation)`. The worker passes values from the same committed snapshot and calls the publisher only after the status transaction returns. Tests compare event identity, status, sequence, current step, failure and compensation to the committed database row and cover non-null failure/compensation values.
- The atomically stored `leaseUntil` value from a successful claim serves as that claim's generation. Each state transition and pre-effect/pre-compensation guard takes the saga row lock and checks both generation equality and that the lease has not expired. A worker that loses its lease stops without further writes or compensation; a new claim retains the prior attempt count and can resume. This fences state changes while Task 2's saga-keyed idempotency covers an effect already in flight when a lease expires.
- `SocketException` (including `NoRouteToHostException`) and wrapped causes are retryable, alongside the existing timeout, connectivity and HTTP 5xx cases. Domain errors remain non-retryable.
- TDD RED: the updated publisher contract initially failed compilation (three incompatible lambda errors). The stale-between-persist-and-effect test then failed with `expected: <1> but was: <2>` before adding the pre-effect ownership guard. The earlier retry test exposed a transient classification regression for `SocketTimeoutException`, which was corrected.
- Final focused command: `.\mvnw.cmd '-Dtest=FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest' test`. Exact summary: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` (worker 13, service 4).
- Full command after the production fix: `.\mvnw.cmd test`. The process exited 1. Surefire XML summary: 88 reports, 323 tests, 0 failures, 17 errors, 1 skipped. The same seven existing integration/smoke classes had environment errors from unavailable Docker/Testcontainers or refused `localhost:6379` Redis. The Task 3 tests passed in that run (worker 12, service 4); the additional event-details test was added afterward and passed in the final focused run.
- Remaining limitation: an external call already in flight can finish after the five-minute lease expires; the stale holder is then fenced from persisting its result or beginning compensation. The newly claimed worker can safely reapply the Task 2 idempotent effect.

## Review fix round 2

- The round 1 pre-effect guard released the row lock before invoking the effect. Two tests using separate worker threads and latches proved the gap: with the first worker blocked inside execution or compensation, the second worker reclaimed the expired lease and returned before the first released its call. Both tests failed RED with `Expected java.util.concurrent.TimeoutException to be thrown, but nothing was thrown`.
- The worker now obtains the saga row lock and validates its claim inside the **same transaction** that invokes `SagaStep.execute` or `SagaStep.compensate`. It validates the claim again before committing the step outcome. The Task 2 saga methods in `CivilizationService`, `ResourceRegionService`, and `NexusMeshService` use default `@Transactional` (REQUIRED), so their database mutations join this worker transaction. A competing claim cannot update the row until the effect transaction commits or rolls back. `POTENTIALLY_APPLIED` remains committed in a prior transaction for recovery.
- On an exception after a partial database mutation, the effect transaction rolls back the joined database work. A terminal failure still compensates the persisted potentially applied step because nontransactional effects may have occurred and the outcome may be uncertain. A test mutates the saga row and throws inside the effect, then verifies the mutation rolled back and compensation proceeded.
- The status `SUCCEEDED` or `COMPENSATED` and its sequence are committed with the respective effect. `SagaProgressPublisher` is called only after the `TransactionTemplate` returns from that commit; no success/compensated event is emitted by a holder that loses its lease during the effect. Concurrent execute and compensate tests assert no overlapping call and no stale terminal event.
- Final focused command: `.\mvnw.cmd '-Dtest=FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest,FoundCivilizationSagaStepsTest' test`. Exact class results: service `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`; worker `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`; steps `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`. Maven exited 0 (`BUILD SUCCESS`), 27 tests total.
- Full `.\mvnw.cmd test` exited 1. Surefire XML summary: 88 reports, 326 tests, 0 failures, 17 errors, 1 skipped. The errors remain in the same seven Docker/Testcontainers or localhost Redis dependent classes named above; all Task 3 and existing saga step tests passed.
