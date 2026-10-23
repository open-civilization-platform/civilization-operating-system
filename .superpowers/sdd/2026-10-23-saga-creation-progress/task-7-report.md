# Task 7 report: Found Civilization persistence, auth, and recovery

Base: `5d5cc7c`, branch `feature/issue-12-saga-flow`.

Status: backend verified and ready for review; frontend regression/fix verification remains pending. Task 7 is not complete yet.

## Scope and integration proof

Added `FoundCivilizationSagaPersistenceIntegrationTest`, using real H2/JPA repositories, transaction boundaries, the durable submission service, civilization/region/Nexus domain services, all fixed saga effects, JWT generation/filtering, controller mappings, and HTTP error handling. The durable service is never mocked. A repository spy delegates to the real repository except for the single explicitly injected unavailable-database scenario.

The test-only `@SpringJUnitWebConfig` configuration imports the flow and enables persistence/HTTP auto-configuration without scanning the production application. It has its own H2 database, no seed runners, no scheduling, no Redis listener, and a mocked `StringRedisTemplate` transport boundary. It deliberately retains the Reaktor starter's real auto-configured advice so HTTP error behavior is covered.

Ten executed scenarios cover:

- Accepted submission persisted as PENDING, followed by SUCCEEDED through the manually driven real worker; all three named steps persist as successful.
- Same-owner/key/payload HTTP replay before and after completion returns the same saga; a different payload returns 409 and never inserts another saga.
- Persisted civilization and node rows are unique by saga ID, the actual region row belongs to that civilization, and founder population is 101 after replay, catching a repeated creation/join effect.
- Authenticated owner status access, foreign/unknown status 404, unauthenticated POST/GET 401, and ignored spoofed `X-Client-Id` header.
- Typed events produced from committed worker transitions pass through two distinct subscriber/handler registries; only the owner's captured socket receives them. The terminal event matches the reloaded sequence/status and omits routing metadata.
- Three parameterized lease recovery cases, one for each fixed effect. The test commits a POTENTIALLY_APPLIED marker, applies the real effect, retains its durable rows without the success marker, clears/reloads JPA state, advances a controlled clock beyond the lease, and constructs new service/steps/worker instances. A live lease blocks recovery; the expired lease recovers with persisted attempts, preserved civilization/node identity, unique effects, and successful terminal state.
- A real region-already-claimed domain failure enters COMPENSATING. Expiring the lease immediately after publishing that committed transition interrupts the first worker. A reconstructed worker injects a failure only at the claim compensation boundary; real civilization compensation still persists FALLEN, foreign region ownership survives, no node is deployed, and COMPENSATION_FAILED plus its cleanup error remain visible through HTTP after another worker reconstruction.
- Oversized 256-character name/key returns 400 with no saga row; 255-character fields are accepted into PENDING persistence. The boundary test establishes submission storage acceptance, not a separate full-effect success guarantee for a 255-character generated node name.
- Malformed JSON, missing idempotency header, and malformed saga UUID remain 400 with the starter advice active.
- A concrete repository-boundary `DataAccessResourceFailureException` returns safe 500 without a saga ID or private database details; no row is inserted.

## Reproduced contract gaps and minimal fixes

1. Length validation: before the fix, a 256-character name/key reached actual VARCHAR(255) constraints (SQLState 22001). Added a pre-persistence check in `FoundCivilizationSagaService` for the trimmed name and exact idempotency key, each at most 255 characters.
2. HTTP advice precedence: real integration initially returned 200 for conflict and constraint failures. Inspection of installed `reaktor-spring-mvc-starter:0.1.1` using `javap -v` showed `ReaktorGlobalExceptionHandler` has `@Order(-2147483648)` and an Exception catch-all returning a body without a failure status. Added saga-controller-local handlers for 400/404/409 plus an unexpected-failure 500 fallback; the shared response helper reuses the existing `ErrorResponse` format. The 500 response uses a fixed message and logs the exception server-side. No global starter behavior changed.
3. Frontend cache regression/fix: pending coordinated RED execution at this draft point.

For focused legacy SpringBootTests, added a test-only mocked `TaskScheduler` in the repository, service, worker, and Redis-subscriber test classes. Spring still discovers scheduled methods, but the external scheduler boundary never starts automatic background jobs; these tests drive workers/transactions explicitly. Existing named Redis listener replacements remain intact. No production scheduling or infrastructure failure behavior was suppressed.

## RED/GREEN and commands

All paths below are relative to this worktree; raw logs are ignored local evidence.

- Initial test bootstrap experiment: `@SpringBootTest` containing only a nested `@TestConfiguration` still discovered `CivilizationOsApplication`, attempted the production localhost Redis listener, and failed context startup. This was a test setup failure, not a meaningful RED. Replaced it with the isolated configuration above; no live infrastructure retry was performed.
- Isolated meaningful RED (`task-7-backend-red.log`): 7 tests, 3 failures, 0 errors after correcting fixture name collisions. Conflict expected 409 but got 200; both oversized fields expected 400 but got 200, with actual VARCHAR violations in the log. Recovery/event/auth/compensation paths otherwise executed.
- After local typed handlers, bounds RED (`task-7-backend-bounds-red.log`): 7 tests, only the two oversized-input failures remain; conflict/owner status and all real effects pass.
- After bounds validation and expanded binding/boundary assertions, isolated GREEN (`task-7-backend-green.log`): 9 tests, 0 failures/errors. Final strengthened real-compensation version passed in 26.925s.
- Unexpected DB failure RED (`task-7-backend-fallback-red.log`): 10 tests, 1 failure, 0 errors; expected 500 but got 200. This preceded the local safe fallback.
- Final backend focus (`task-7-backend-focused.log`), exit 0, **72 tests, 0 failures, 0 errors, 0 skipped**, 61 seconds:

```powershell
.\mvnw.cmd '-Dtest=SagaExecutionRepositoryTest,FoundCivilizationSagaStepsTest,FoundCivilizationSagaServiceTest,FoundCivilizationSagaWorkerTest,SagaControllerTest,SagaProgressRedisSubscriberTest,NexusWebSocketHandlerTest,FoundCivilizationSagaPersistenceIntegrationTest' test
```

- No scheduling-thread/Cortex tick or actual localhost connection error appears in the final focused log. `Redis offline` stack traces are the existing deliberate publisher-failure injection. JVM dynamic-agent/native-access and existing deprecated-test-API warnings remain.
- Frontend initial sandbox attempt failed before tests because esbuild child-process launch returned `spawn EPERM`. The focused escalated tool request then remained pending and was canceled by the coordinator after about 670 seconds. That cancellation is not evidence of a human refusal; no frontend RED result came from that invocation.
- Frontend focused tests/build: pending coordinated execution at this draft point.
- `git diff --check`: exit 0; Git emitted line-ending conversion warnings only.

## Self-review and exact limits

- Verified the integration assertions catch missing durable submission, changed owner identity, lost saga IDs, non-idempotent fixed effects, skipped lease gating, lost attempt counts, missing earlier compensation, stale status/events, malformed binding statuses, and false-success/error-detail leakage.
- Recovery here means reconstructed Java worker/service objects against cleared/reloaded rows in the same H2 database and JVM. It does **not** establish process termination/restart, external database survival, PostgreSQL locking/constraints, V23 migration execution, or real distributed-process contention. The potentially-applied fixtures deliberately commit effects before success markers to exercise recovery of an ambiguous effect boundary; the production relational worker normally commits an effect and its success transition together.
- Redis messages and WebSocket frames are captured transport boundaries. There is no live Redis connection, TCP WebSocket handshake, or network cross-instance delivery proof in the final focused integration. Existing separate owner-handshake tests are included in the focus.
- Automatic polling/simulation and full-application event listeners/seed effects are excluded from the new isolated flow proof. The relevant fixed domain services, database mutations, and saga transaction sequencing run for real.
- Preserved V23 unchanged. No V24, baseline-schema, naming-strategy, root #8 infrastructure, or generic workflow changes were made. The root reserves Compose infrastructure for #8 and will validate combined production boot after reconciliation; no broad Java/Docker/Compose rerun was attempted in Task 7.
- Preserved the coordinator's factual correction in tracked `task-5-report.md` and include it with this task's documentation.
- No subagents, push, PR, or merge used.
