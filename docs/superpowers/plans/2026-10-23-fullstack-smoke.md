# Full-Stack Smoke Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every pull request prove that the complete CivOS Docker Compose stack starts and that its key REST, GraphQL, WebSocket, frontend, and Debezium contracts work together.

**Architecture:** A Bash smoke runner owns the disposable Compose lifecycle, readiness polling, integration assertions, log capture, and cleanup. A Node.js 24 helper checks the authenticated WebSocket handshake. A dedicated CI job runs the same script used by developers and uploads its saved logs on failure.

**Tech Stack:** Docker Compose v2, Bash, curl, Node.js 24 built-in WebSocket API, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-10-23-fullstack-smoke-design.md

## Global Constraints

- Use Docker Compose v2 and start the complete stack with builds enabled.
- Local use requires Bash on Linux/macOS or WSL/Git Bash on Windows; native PowerShell is out of scope.
- Use Node.js 24 for the authenticated WebSocket client.
- Authenticate with POST /api/v1/auth/connect and pass the JWT in the /ws/nexus query parameter named token.
- Create a uniquely named civilization with POST /api/v1/civilizations and verify it through GraphQL; do not depend on seeded names or IDs.
- Verify Debezium connector registration only; CDC event delivery belongs to issue #13.
- Save pre-teardown service logs to artifacts/smoke-test/compose.log.
- Fixed Compose container names require a safe preflight; never stop or remove a pre-existing container or volume.
- A smoke failure must return non-zero and cleanup must remove only the disposable Compose project and its volumes.

## Review Focus

- Existing fixed container names: reject before Compose changes anything; pin with a fake-Docker preflight test.
- Slow service startup: use bounded readiness polling and identify the timed-out endpoint in the failure message; pin in the full-stack run.
- REST create succeeds but the wrong object is asserted: parse the returned ID/name and require the same name in GraphQL; pin in the full-stack run.
- WebSocket authentication or secret leakage: send the JWT only as the query token, redact it from output, and require a successful handshake; pin using the Node unit test with a sentinel JWT and a fake WebSocket.
- Debezium registration fails or points to a different connector: require a successful registration response and the configured connector name in Connect's registry; pin with a mocked-curl failure test and the full-stack run.

---

### Task 1: Build the reusable smoke runner

**Files:**
- Create: scripts/smoke-test.sh
- Create: scripts/smoke-test-websocket.mjs
- Create: scripts/tests/smoke-test-preflight.sh
- Create: scripts/tests/smoke-test-websocket.test.mjs
- Create: scripts/tests/setup-connector.test.sh
- Modify: debezium/setup-connector.sh

**Interfaces:**
- Consumes: Existing docker-compose.yml, debezium/connector.json, and REST/GraphQL/worker/WebSocket contracts in the approved spec.
- Produces: scripts/smoke-test.sh as the single local/CI entry point; connectNexusWebSocket(baseUrl, jwt, WebSocketImpl) returns Promise<void> and never logs the JWT; tests prove safe preflight, WebSocket redaction, and connector HTTP error handling.

- [ ] **Step 1: Write the failing fixed-name collision test**

Create scripts/tests/smoke-test-preflight.sh. Put a fake docker executable first in PATH; make docker ps report an existing fixed container name from docker-compose.yml; record every invocation. Assert the smoke runner exits non-zero and does not call docker compose up, down, or volume removal.

Run: bash scripts/tests/smoke-test-preflight.sh
Expected: FAIL because scripts/smoke-test.sh does not yet reject a pre-existing Compose container.

- [ ] **Step 2: Implement the safe Compose preflight and lifecycle**

Add strict Bash error handling, configurable base URLs/project name/readiness timeout, detection of all fixed container names, a unique Compose project name, full-stack docker compose up --build, and an exit trap. On failure the trap writes docker compose logs --no-color to artifacts/smoke-test/compose.log before bringing down only that project with its volumes.

Run: bash scripts/tests/smoke-test-preflight.sh
Expected: PASS; the fake existing container remains untouched and no Compose up/down command is issued.

- [ ] **Step 3: Write failing WebSocket and connector error tests**

Create scripts/tests/smoke-test-websocket.test.mjs with a fake WebSocket implementation. Cover successful open, handshake/error timeout, and that captured output never contains a sentinel JWT. Create scripts/tests/setup-connector.test.sh with a fake curl that returns a non-success registration status; assert setup-connector.sh exits non-zero.

Run: node --test scripts/tests/smoke-test-websocket.test.mjs
Expected: FAIL because connectNexusWebSocket does not exist yet.

Run: bash scripts/tests/setup-connector.test.sh


 Add bounded readiness checks and service assertions**

Poll with a bounded timeout for /actuator/health, the worker /api/v1/worker/health endpoint, the frontend root through Nginx, and the Debezium Connect /connectors endpoint. Fail with the endpoint name and last response/error when a timeout occurs. Check readiness before beginning the data-path assertions.

- [ ] **Step 5: Add the REST-to-GraphQL integration path**

Request a client token from /api/v1/auth/connect; POST a unique name and scale to /api/v1/civilizations with the returned bearer token; parse the response and require an ID; query /graphql for civilizations and assert the returned list contains that exact name.

- [ ] **Step 6: Add authenticated WebSocket and Debezium checks**

Implement connectNexusWebSocket(baseUrl, jwt, WebSocketImpl = globalThis.WebSocket) with the Node 24 built-in WebSocket API. Pass the JWT through SMOKE_TEST_JWT and connect to /ws/nexus?token=<JWT>; resolve only after open, reject on handshake/error timeout, and never print the tokenized URL. The Node test injects a fake WebSocket, checks open/error/timeout behavior, and asserts captured output does not contain a sentinel JWT. Harden debezium/setup-connector.sh so registration and status requests use curl failure handling and do not require Python; its mocked-curl test returns a failing HTTP status and expects a non-zero exit. Then register debezium/connector.json and assert civos-postgres-connector is present in the Connect registry.

- [ ] **Step 7: Verify the complete runner**

Run: bash -n scripts/smoke-test.sh scripts/tests/smoke-test-preflight.sh scripts/tests/setup-connector.test.sh debezium/setup-connector.sh
Expected: PASS with no syntax errors.

Run: bash scripts/tests/smoke-test-preflight.sh
Expected: PASS with collision failure occurring before Compose up/down and without cleanup side effects.

Run: node --check scripts/smoke-test-websocket.mjs
Expected: PASS with no syntax errors.

Run: node --test scripts/tests/smoke-test-websocket.test.mjs
Expected: PASS for open, error/timeout, and token-redaction cases.

Run: bash scripts/tests/setup-connector.test.sh
Expected: PASS when a mocked non-success registration response causes a non-zero exit.

Run: bash scripts/smoke-test.sh
Expected: PASS after the full stack is ready; output reports each check without exposing the JWT; the project is stopped and only its own volume is removed. artifacts/smoke-test/compose.log is retained if any check fails.

Run: git diff --check
Expected: PASS with no whitespace errors.

- [ ] **Step 8: Commit the smoke runner**

```bash
git add scripts/smoke-test.sh scripts/smoke-test-websocket.mjs scripts/tests/smoke-test-preflight.sh scripts/tests/smoke-test-websocket.test.mjs scripts/tests/setup-connector.test.sh debezium/setup-connector.sh
git commit -m "test: add full-stack smoke runner"
```

### Task 2: Run the smoke test in pull-request CI and document local use

**Files:**
- Modify: .github/workflows/ci.yml
- Modify: README.md

**Interfaces:**
- Consumes: Task 1 entry point scripts/smoke-test.sh and its log path artifacts/smoke-test/compose.log.
- Produces: a dedicated full-stack-smoke job on pull requests, with Node.js 24 and Docker Compose v2; README instructions for local prerequisites and invocation.

- [ ] **Step 1: Add the pull-request smoke job**

Add an independent full-stack-smoke job to .github/workflows/ci.yml. Check out the branch, set up Node.js 24, verify docker compose is available, invoke bash scripts/smoke-test.sh, and upload artifacts/smoke-test/compose.log with if: failure(). Do not make the existing build-and-test or frontend jobs depend on this new job.

- [ ] **Step 2: Document local invocation and failure logs**

Document Docker Compose v2 and Bash prerequisites, Windows WSL/Git Bash usage, the command bash scripts/smoke-test.sh, and the location of failure logs. Clarify that this smoke test checks connector registration while issue #13 covers CDC event delivery.

- [ ] **Step 3: Verify workflow and documentation changes**

Run: git diff --check
Expected: PASS.

Run: bash scripts/smoke-test.sh
Expected: PASS locally with default ports or documented overrides.

CI verification: open/update the issue branch PR and confirm full-stack-smoke passes on a pull_request event; on an intentional failure, confirm the Compose log artifact is uploaded and the job fails.

- [ ] **Step 4: Commit CI and documentation**

```bash
git add .github/workflows/ci.yml README.md
git commit -m "ci: run full-stack smoke test on pull requests"
```
