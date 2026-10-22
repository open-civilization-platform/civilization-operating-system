# Full Stack Quality Baseline

## Purpose

Make the repository's continuous integration and architecture documentation reflect the application that contributors actually build and run. The current workflow validates the Java application but does not execute the existing frontend checks, and the README still describes a Thymeleaf/HTMX frontend although the shipped frontend is React/Vite.

## Decision: Keep the Monorepo

Keep the React frontend, Spring Boot application, worker runtime, deployment configuration, and their contracts in this repository. Frontend and backend changes currently share a deployment topology and API surface, so a monorepo keeps coordinated changes and local integration straightforward.

Do not extract services into separate repositories in this change. The `services/` Maven reactor contains bootstrap applications and client scaffolding, while the root Maven project remains the application built by CI and Docker Compose. Document this structure accurately and leave the service scaffolding intact pending a separate architecture decision. Reconsider extraction when a component has an independently owned API and data boundary, independent release cadence, and an operational need to deploy without coordinating with the root application.

## First Cycle: CI and Documentation

### Frontend CI

Add a frontend job to `.github/workflows/ci.yml`. Run it for pull requests targeting `main` and pushes to the branches already covered by the workflow. The job uses the checked-in `frontend/package-lock.json` and runs, in order:

1. `npm ci` from `frontend/`.
2. `npm run lint`.
3. `npm test` (Vitest's one-shot script).
4. `npm run build` (TypeScript check and Vite production build).

The first lint run exposed pre-existing frontend debt: 61 errors, including 49 explicit `any` uses. Remove the unused imports/state and hook dependency issues. Keep the remaining explicit `any` findings visible as warnings with a maximum-warning budget equal to the current baseline (37) so new lint debt fails CI while API response types are added incrementally.

Keep the frontend job independent of the backend Maven job so GitHub can run both concurrently. Do not add a browser installation or run Playwright E2E tests in this first cycle; those require a separately sized browser-runner decision.

### Backend CI and Image Publishing

Keep the existing backend `build-and-test`, `lint`, and `docker-build` jobs and their trigger semantics. The frontend job must not change GHCR permissions or image tags. Image publishing remains scoped to the Docker job's existing package-write permission.

### Documentation

Update `README.md` so the stack, local development instructions, and architecture diagram describe the actual system:

- React/Vite frontend served by its Nginx container.
- Spring Boot modular monolith serving REST and GraphQL APIs and WebSocket endpoints.
- Cortex worker as a separate runtime built from the same repository.
- PostgreSQL/PostGIS, Redis, and optional Kafka/Debezium dependencies as represented by Compose.
- `services/` as a separate Maven scaffold whose relationship to the running root application is not yet an extracted production boundary.

Keep setup commands consistent with the existing Makefile and Docker Compose configuration. Do not claim the service scaffolds are deployed by the root CI unless the workflow actually builds them.

## Out of Scope

- Splitting the repository or creating new repositories.
- Moving, deleting, or implementing the service scaffolds under `services/`.
- Changing API contracts, selecting GraphQL versus REST, or generating shared client types.
- Redesigning frontend pages or changing backend business behavior.
- Adding Playwright browser tests to CI.
- Changing deployment topology, Docker image names, secrets, or package permissions.

## Success Criteria

- A pull request runs the existing backend checks and the new frontend lint, unit-test, and production-build checks.
- A frontend failure blocks the pull request in a visible frontend-specific job.
- A normal push to `main` runs the frontend checks alongside backend validation and existing image publishing.
- The README accurately describes the implemented frontend, backend, worker, and local runtime topology.
- No service extraction or API behavior change is introduced.

## Risks and Mitigations

- Frontend checks may expose existing lint, test, or TypeScript failures. Treat these as actionable CI findings and fix only failures needed to establish the stated gate.
- Existing explicit `any` warnings remain visible under a fixed budget and should be removed as frontend API response types are introduced.
- NPM package installation adds time to CI. Use the lockfile and the setup-node npm cache keyed by that lockfile if supported by the repository's existing Actions patterns.
- The service scaffolds can be mistaken for deployed production services. State which workflows and Compose services actually build and run, and avoid implying the scaffold has production ownership.
