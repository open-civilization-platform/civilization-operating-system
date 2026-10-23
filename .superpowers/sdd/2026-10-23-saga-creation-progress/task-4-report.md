# Task 4: authenticated saga REST submission and status

Status: implemented and self-reviewed on `feature/issue-12-saga-flow`, based on `a2f1c1cae007fc2fda0bebd250294a6bdccbcb20`. No push, PR, merge, or subagents.

## Delivered contract

- POST `/api/v1/sagas/found-civilization` requires authentication, name, scale, regionId, and a nonblank `Idempotency-Key`; it delegates to the existing durable service and returns HTTP 202 with its status representation.
- GET `/api/v1/sagas/{id}` accepts a UUID and delegates to the owner-scoped status lookup. Both endpoints derive the owner exclusively from `Authentication.getName()`.
- The specific authenticated saga GET matcher precedes the existing public API GET matcher. Existing JWT filtering and `/ws/**` rules are unchanged.
- Idempotency conflicts return 409; malformed JSON, invalid enum/type/UUID, missing fields/header, and blank fields/key return 400; unauthenticated access returns 401; foreign and absent sagas receive the same safe 404 response. New binding/error handlers return fixed messages rather than exception contents.
- Status retains `status`, `civilizationId`, and `name`, together with saga ID, steps, sequence, failure details, and timestamps. Preserved the inherited uncommitted name mapping and its service assertions. Restored the original relative order of existing DTO constructor components rather than keeping the interrupted edit's sequence reordering.
- Following the coordinator's explicit clarification, each `SagaStepView` now serializes a derived `label`: `CREATE_CIVILIZATION` → `Civilization created`, `CLAIM_REGION` → `Region claimed`, and `DEPLOY_NEXUS_NODE` → `Nexus node deployed`. Existing record components/constructor and worker state logic are unchanged.
- The POST operation documentation describes asynchronous acceptance and the new status values.

## TDD and verification

1. Inspected and completed the inherited untracked `SagaControllerTest`. Its initial harness could not compile because the project does not include the Boot `WebMvcTest` module. Replaced that harness with existing Spring `SpringJUnitWebConfig`, real MVC and the actual security/JWT filter chain, mocking only the service and JWT validation boundary. No dependencies were added.
2. REST RED: `.\mvnw.cmd -Dtest=SagaControllerTest test` ran 18 cases: **17 failures, 0 errors, 0 skipped**. Failures covered missing async POST/GET, owner-safe lookup, authenticated GET, and request/error mappings; unauthenticated POST already passed. Legacy synchronous POST produced 500 with the mocked orchestration dependencies, and missing GET routes were converted to 500 by the preexisting catch-all advice.
3. Added the minimum controller, security matcher, and error mappings. REST GREEN: the same focused command ran **18 tests, 0 failures, 0 errors, 0 skipped**.
4. Initial full suite: `.\mvnw.cmd test` ran **344 tests, 0 failures, 17 errors, 1 skipped**. During self-review found the nested MVC configuration used `@Configuration`, which application scanning could discover. Changed it to `@TestConfiguration`; this removed eight Redis startup/context errors. Tightened service stubbing to require the exact submitted request, authenticated owner, and key, including an inconsistent caller-supplied identity header.
5. Reverified focused controller tests: **18 tests, 0 failures, 0 errors, 0 skipped**. Full suite after configuration isolation: **344 tests, 0 failures, 9 errors, 1 skipped**.
6. Label RED: added all three literal step-key/display-label JSON assertions to the controller response test. Focused command ran **18 tests, 1 failure, 0 errors, 0 skipped**, specifically `postReturnsAcceptedWithSagaIdAndLegacyFields`: no value at `$.steps[0].label`.
7. Added the derived JSON label mapping. Final focused GREEN: `.\mvnw.cmd -Dtest=SagaControllerTest test` ran **18 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (23.967 seconds).
8. Final full suite after all production/test changes: `.\mvnw.cmd test` ran **344 tests, 0 failures, 9 errors, 1 skipped**, BUILD FAILURE, exit 1 (56.959 seconds). Thus 334 tests passed; the suite is not green.
9. `git -c core.whitespace=cr-at-eol diff --cached --check` passed. Existing tracked CRLF formatting in the configuration files is preserved.

The final full suite's saga tests all passed: controller 18, service 4, worker 15, steps 8, repository 6 (**51 total**). The real service tests retain and verify normalized `name` for new/reused submissions and owner-scoped GET.

Local evidence logs, intentionally ignored rather than committed: `task-4-red.log`, `task-4-label-red.log`, `task-4-green.log`, and `task-4-full.log` in this report's directory. The green/full files contain the final runs.

## Full-suite errors and skipped test

The final nine errors originate from unavailable Docker/Testcontainers infrastructure; dependent Redis tests cannot start their shared Redis container. There are no assertion failures. Every affected test is listed below:

- `RedisCacheIntegrationTest.shouldCacheResourcePageAndSucceedSerialization`
- `RedisCacheIntegrationTest.shouldVerifyRedisCacheManagerActive`
- `RedisCacheIntegrationTest.shouldCacheBalanceReport`
- `RedisRateLimiterIntegrationTest.shouldAllowRequestsUnderLimitAndBlockOverLimit`
- `CortexBusinessSmokeTest.testPersonnelDrainLoophole`
- `CortexBusinessSmokeTest.testSecurityBotReputationMaskingExploit`
- `CortexBusinessSmokeTest.testTechnologyPrerequisitesLock`
- `DockerBuildSmokeTest` (class/container initialization error, before a test method)
- `RedisPubSubIntegrationTest.shouldPropagateEventThroughRedisPubSub`

`PostgresIntegrationTest.contextLoadsAndTablesExist` was skipped.

For completeness, the first full run additionally errored during Redis connection/context startup in the following eight tests; they passed after isolating the test configuration:

- `CivilizationFlowIntegrationTest.testFullCivilizationLifecycle`
- `CivilizationFlowIntegrationTest.testUnauthenticatedAccess`
- `CivilizationFlowIntegrationTest.testPublicEndpointsAreAccessible`
- `CivilizationFlowIntegrationTest.testAuthTokenGeneration`
- `CivilizationFlowIntegrationTest.testCortexEngineTick`
- `CivilizationFlowIntegrationTest.testResourceBalance`
- `CivilizationFlowIntegrationTest.testJwtValidationWithWebSocketFormat`
- `ShedLockIntegrationTest.shouldAcquireAndReleaseLockUsingProvider`

## Self-review

Checked the final changes against the task brief and auth/compatibility specification: exact route/status codes; principal-derived ownership; foreign/unknown equivalence; validation before service submission; existing JWT/WebSocket rules; retained DTO data and constructor component order; exact stable keys and display labels. Reviewed realistic mutations: removing the GET authentication matcher, using the wrong owner/request/key, dropping validation/conflict handling, omitting name, or changing a label is caught by the corresponding controller/service assertions. Used self-review as explicitly requested; no reviewer subagent was spawned.

Remaining concern: complete infrastructure integration verification requires a working Docker environment. The final full-suite failure is reported rather than suppressed or excluded.
