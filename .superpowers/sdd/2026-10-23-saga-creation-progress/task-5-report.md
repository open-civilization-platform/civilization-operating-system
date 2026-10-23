# Task 5 report: owner-scoped saga progress

Implemented against base `af9caa1` on `feature/issue-12-saga-flow`.

## Result and scope

- Added `SagaProgressEvent`, `SagaProgressPayload`, `RedisSagaProgressPublisher`, and `SagaProgressRedisSubscriber`.
- The adapter implements the unchanged Task 3 `SagaProgressPublisher.publish(String ownerId, UUID sagaId, SagaStatusResponse snapshot, String currentStep, SagaFailureView failure, SagaCompensationView compensation)` signature.
- Redis publishes a focused transport object `{ownerId,event}` on `Nexus-saga-progress-events`. The nested `event` is exactly `{type:"SAGA_PROGRESS",payload:{sagaId,status,currentStep,steps,failure,compensation,updatedAt,sequence}}`.
- The subscriber parses that transport separately from `NexusMessageSyncDTO`, validates the routing identity/event, strips routing metadata, and invokes `NexusWebSocketHandler.sendToClient`.
- The handler captures the authenticated `X-Client-Id` at connection establishment. Private sends target every open local session for that owner; missing/blank owners, anonymous sockets, foreign owners, closed sockets, and disconnected sockets do not receive them. A failed socket does not throw into the subscriber or stop targeted iteration.
- Added a private per-session write helper shared by saga sends, existing Nexus broadcasts, and pong replies. This prevents concurrent writes to a Spring WebSocket session when Redis and incoming messages execute on different threads.
- `RedisEventConfig` retains its existing Nexus topic/listener and registers a separate saga topic/listener in the same container. Existing Nexus broadcast behavior remains covered. `WebSocketConfig` is unchanged, preserving both `/ws/nexus` and `/ws/voxtex`.
- Redis/serialization delivery exceptions are logged and contained by the adapter; they do not invalidate the committed transition or interrupt the worker's remaining steps. The worker remains responsible for publishing after its `TransactionTemplate` returns. No worker transaction logic was changed. GET remains authoritative; Pub/Sub delivery has no persistence or replay guarantee.

## TDD evidence

1. Added the required publisher/subscriber and privacy tests first, including the exact requested test names `publishesTypedEventAfterTransitionCommit`, `parsesSagaEventsSeparatelyFromNexusMessageDTO`, and `sendsSagaEventOnlyToMatchingClientSessions`.
2. Ran `.\mvnw.cmd '-Dtest=SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest' test`: compilation failed for the missing publisher, subscriber, topic constant, and targeted-send API. An initial test fixture method typo was corrected before recording the missing-feature RED run in `task-5-red.log`.
3. Added no-op API shells, reran the focused tests, corrected the existing Nexus broadcast fixture to include its required non-null hop count, and recorded behavioral RED: 9 tests, 4 failures, 0 errors. Failures were absent publisher output and absent targeted socket delivery (`task-5-red-behavior.log`).
4. Implemented the typed transport, adapter, subscriber, registration, and owner filtering. The focused command passed: 9 tests, 0 failures/errors (`task-5-green.log`).
5. Self-review identified the concurrent Nexus/pong write risk. Added a parameterized test that holds a saga socket write open, starts a second writer on another virtual thread, and requires that writer to wait. Both Nexus and pong cases failed RED: `Expected java.util.concurrent.TimeoutException to be thrown, but nothing was thrown` (7 WebSocket cases, 2 failures, 0 errors; `task-5-ws-serialization-red.log`).
6. Routed all three write paths through the shared helper and ran final focused verification below. Both concurrency cases passed.

The commit-visibility test uses a real H2-backed repository and real worker/transaction boundaries. At the Redis boundary it asserts there is no active transaction and reads the persisted sequence/status for comparison with the serialized event. The cross-instance path test uses a real publisher, Jackson mapper, subscriber, and handler with captured Redis output and socket-boundary doubles; it verifies exact envelope shape, saga ID, sequence, all three labels, step statuses, failure and compensation, and absence of delivery to a foreign owner. Redis and sockets are external boundaries; business state, serialization, filtering, and transaction behavior remain real.

## Verification

- Final command: `.\mvnw.cmd '-Dtest=SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest,FoundCivilizationSagaWorkerTest' test`.
- Final result: **26 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; exit 0**. Worker: 15; publisher/subscriber: 4; WebSocket: 7. Log: `task-5-final-focused.log`.
- Before the final shared-write-helper change, the required full `.\mvnw.cmd test` completed with **353 tests, 0 failures, 17 errors, 1 skipped; exit 1** (`task-5-full-suite.log`). Every Task 5 case passed in that run. The broader suite was not repeated after the focused concurrency fix, per the parent instruction to avoid another broad infrastructure retry.
- The full-suite errors are causally separated below; they are not asserted to be 17 baseline errors.
- `git -c core.whitespace=cr-at-eol diff --check` passed. The explicit whitespace setting respects the existing tracked CRLF handler source. Plain `git diff --check` treats added carriage returns in that file as trailing whitespace.
- Existing Maven/JDK native-access/Unsafe warnings, Jackson deprecation warnings, and expected notification-failure log entries remain; no dependency or build configuration was changed.

## Full-suite environmental causes and retry outcome

Task 4's prior suite had 9 Docker-dependent errors. The 8 additional errors in this run were **7 in `CivilizationFlowIntegrationTest` and 1 in `ShedLockIntegrationTest`**. Their Surefire XML and full log contain the chain:

`ApplicationContextException: Failed to start bean 'container'` → `RedisListenerExecutionFailedException` → `RedisConnectionFailureException: Unable to connect to Redis` → `RedisConnectionException: Unable to connect to localhost/<unresolved>:6379` → `AnnotatedConnectException: Connection refused: getsockopt: localhost/127.0.0.1:6379`.

The first failure appears in `task-5-full-suite.log` around lines 259–416 and its context startup chain around lines 2437–2469. These contexts start the existing real Redis listener container and require a Redis service on port 6379. Parent work concurrently started and removed a Compose stack using that port, consistent with environmental variation between the 9-error and 17-error runs. No new test `@Configuration` was introduced here; Task 4's nested controller test configuration remains `@TestConfiguration`.

The remaining **9 Docker/Testcontainers errors** were `RedisCacheIntegrationTest` (3), `RedisRateLimiterIntegrationTest` (1), `CortexBusinessSmokeTest` (3), `DockerBuildSmokeTest` (1), and `RedisPubSubIntegrationTest` (1). Their initialization errors report an unavailable Docker environment (`Could not find a valid Docker environment` / failed shared container initialization).

As instructed by sandbox rules, requested an escalated rerun after observing the infrastructure failures. That tool call stopped at its approval request and returned **`aborted by user after 432.3s`**. No Java retry process ran and no retry log/result was produced. The parent then instructed that focused verification and causal evidence were sufficient and explicitly prohibited repeating DockerBuildSmokeTest or the broad suite. No production behavior was changed to hide unavailable infrastructure.

## Self-review and remaining limits

- Reviewed the publisher contract and exact wire fields against the spec; routing metadata stays outside the browser event. Existing `SagaStepView` derived labels are reused and verified through actual JSON serialization.
- Reviewed owner checks against the session identity captured during the authenticated handshake; no fixed owner identity or global progress broadcast is introduced.
- Reviewed the worker boundary: publication observes committed durable state, and a real worker test with Redis throwing still reaches `SUCCEEDED` with every step succeeded.
- Reviewed listener separation, existing Nexus delivery, compatibility routes, malformed inputs, socket lifecycle, and concurrent writes. The two unsynchronized-write regressions were demonstrated before fixing them.
- Actual Redis/network cross-instance transport was not verified in this environment because the existing Redis integration test could not initialize Docker. The focused test verifies the complete produced Redis message through a separate subscriber instance and local owner-filtered handler, but is not a live Redis integration test.
- Pub/Sub may drop progress events. GET reconciliation remains necessary by design. A slow socket or Redis operation can delay notification; this task adds no queue, timeout policy, replay service, or general workflow abstraction.
- No push, PR, merge, unrelated source edits, or subagents were used.

Coordinator clarification: the cancellation above followed the coordinator interrupting the silent agent; the tool's aborted-by-user label does not establish an explicit human rejection. No retry command ran. The later instruction to avoid duplicate broad/Docker builds remains in effect.
