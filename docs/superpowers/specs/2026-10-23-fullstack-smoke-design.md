# Full-Stack Smoke Test Design

## Intent

Give each pull request a reliable signal that the runnable CivOS stack works across service boundaries. The current CI checks Java and frontend independently; it does not start the Compose stack or exercise the REST, GraphQL, WebSocket, and Debezium paths together.

## Goals

- Start the services declared by docker-compose.yml using a disposable Compose project.
- Provide one reusable smoke-test command for local development and CI.
- Wait for readiness with bounded retries rather than fixed sleeps.
- Verify backend and worker health, the frontend through Nginx, civilization creation through REST and visibility through GraphQL, an authenticated Nexus WebSocket handshake, and Debezium connector registration.
- Capture service logs on failure and always stop the stack and remove its test volume.

## Non-goals

- Test every business rule or CRUD operation.
- Verify Debezium INSERT/UPDATE/DELETE event delivery; that is the narrower scope of issue #13.
- Change production application behavior or add new health endpoints unless the smoke flow proves the existing contract insufficient.
- Deploy the stack or publish containers as part of this smoke job.

## Approaches considered

1. **Manual smoke script only.** Lowest CI cost, but regressions would not block pull requests.
2. **Full Compose smoke on every pull request — recommended.** One script owns startup, readiness, assertions, log collection, and teardown; a dedicated CI job runs it on pull requests. This covers the integration gap identified by issue #8 and remains available to developers locally. It costs more runner time than the current unit-level jobs.
3. **Fast startup checks on pull requests and deep checks on a schedule.** Lower per-PR cost, but the important cross-service path could regress between scheduled runs and the script would have two behavioral modes to maintain.

The selected direction is approach 2, as approved in chat.

## Scope and flow

Create scripts/smoke-test.sh and a dedicated full-stack-smoke job in .github/workflows/ci.yml. The script uses Docker Compose v2 and a unique project name, starts the complete stack with builds enabled, and guarantees cleanup on success, failure, and interruption. The Compose file currently assigns fixed container names, so local preflight must fail clearly if those names are already in use; it must never stop or remove a pre-existing stack. The script writes its pre-teardown Compose logs to artifacts/smoke-test/compose.log; CI uploads that file on failure.

The script waits, with explicit timeouts, for:

- The application health endpoint at /actuator/health.
- The worker endpoint at /api/v1/worker/health on its published port.
- The frontend through the Nginx root route.
- Debezium Connect's connector API.

Then it obtains a client token from /api/v1/auth/connect, creates a uniquely named civilization through POST /api/v1/civilizations, and queries GraphQL to confirm the same name is present. It opens an authenticated connection to /ws/nexus?token=<JWT> using the returned JWT, matching WebSocketAuthInterceptor, and asserts the WebSocket handshake succeeds. Do not print the token or tokenized URL in logs. It registers the checked-in Debezium connector configuration using the existing setup script and verifies that the named connector is present in the Connect registry.

The test must not depend on seeded civilization names or assume fixed database IDs. Its unique Compose project and generated civilization name make repeated local runs distinguishable. All assertions report the failed endpoint or service and preserve logs before teardown.

## Failure and cleanup behavior

Any readiness timeout, non-success HTTP response, missing GraphQL result, failed WebSocket handshake, or connector registration error makes the script exit non-zero. Cleanup runs even after a failed assertion. On failure, the script saves logs from its Compose project to artifacts/smoke-test/compose.log before teardown; CI uploads that file. Remove only the disposable project and its volumes.

## Compatibility and constraints

- Use the existing REST, GraphQL, worker, WebSocket, and Debezium contracts.
- The CI job runs independently from the existing Java and frontend checks and does not replace them.
- CI uses the Compose v2 plugin, curl, and Node.js 24 for an authenticated WebSocket client. Local use requires Bash on Linux/macOS or WSL/Git Bash on Windows, plus Docker Compose v2; native PowerShell is out of scope for this .sh script. Keep the script inputs configurable so default ports work locally while CI can override them.
- Keep issue #13's CDC data-event assertions out of this job; this issue verifies connector registration and health only.

## Acceptance criteria

- A developer can run the smoke script against Docker Compose and receive a non-zero exit code if any required integration check fails.
- The script starts and tears down an isolated full stack without deleting unrelated Docker volumes or containers.
- The script proves REST-created civilization data is visible in GraphQL.
- The script verifies the frontend, worker, authenticated WebSocket handshake, and Debezium connector health.
- A dedicated pull-request CI job runs the script, preserves logs on failure, and reports failures clearly.
