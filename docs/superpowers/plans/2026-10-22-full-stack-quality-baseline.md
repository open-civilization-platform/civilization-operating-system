# Full Stack Quality Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make pull requests validate the React frontend and make repository documentation match the application contributors actually run.

**Architecture:** Keep the monorepo and existing Spring Boot modular monolith. Add an independent frontend CI job alongside existing Java jobs, then update the README to describe the React app, backend, worker, Compose dependencies, and service scaffolding accurately.

**Tech Stack:** GitHub Actions, Node.js 24, npm, React, Vite, TypeScript, Vitest, ESLint, Java 25, Spring Boot, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-22-full-stack-quality-baseline-design.md`

## Global Constraints

- Keep frontend, backend, worker, and deployment configuration in this repository.
- Preserve existing backend job triggers, image tags, and GHCR permission scope.
- Use `frontend/package-lock.json` with `npm ci`.
- Run frontend lint, Vitest, and production build for pull requests to `main` and existing push branches.
- Do not change API behavior, frontend UX, deployment topology, or `services/` scaffolding.
- Do not add Playwright browser setup or E2E runs in this cycle.

## Review Focus

- A change to `frontend/package-lock.json` invalidates npm cache: set `cache-dependency-path` to `frontend/package-lock.json` and confirm the frontend job can install from a clean runner.
- Frontend checks must execute on PRs and pushes, while image publishing remains restricted to existing main/develop push conditions: inspect job-level conditions and verify the PR run skips Docker publishing.
- The current container builder uses Node 20 while the CI job will use Node 24: flag this toolchain difference for a follow-up rather than silently changing the Docker build stage outside the approved spec.
- The `services/` Maven reactor is not built by the root workflow: README text must distinguish the root application and worker from service scaffolding.
- The Makefile's `docker-up` and `docker-up-all` start different Compose service sets: document the actual commands and resulting services accurately.
- The initial frontend lint run fails on pre-existing warnings/errors: clear unused imports/state and hook dependency errors, then cap the remaining explicit `any` warnings at the measured baseline so new debt fails CI.

---

### Task 1: Add frontend quality checks to GitHub Actions

**Files:**
- Modify: `.github/workflows/ci.yml`
- Test: GitHub Actions run for the pull request

**Interfaces:**
- Consumes: `frontend/package-lock.json` and the existing `lint`, `test`, and `build` scripts in `frontend/package.json`.
- Produces: an independent `frontend` job that reports npm install, lint, unit-test, and build results on PRs and existing push events.

- [ ] **Step 1: Add an independent `frontend` job**

Configure `ubuntu-latest`, checkout the repository, then use `actions/setup-node@v7` with Node.js `24`, npm caching, and `cache-dependency-path: frontend/package-lock.json`.

- [ ] **Step 2: Run frontend checks from `frontend/`**

Run `npm ci`, `npm run lint`, `npm test`, and `npm run build` in that order. Give the job a clear name and set a finite timeout.

- [ ] **Step 3: Confirm the workflow diff preserves backend behavior**

Run: `git diff --check`

Inspect `.github/workflows/ci.yml` to confirm the new job inherits workflow triggers, does not depend on Maven completion, and does not broaden `docker-build` conditions or permissions.

- [ ] **Step 4: Commit the CI change**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: validate frontend in pull requests"
```

### Task 2: Align README with the running application

**Files:**
- Modify: `README.md`
- Test: manual documentation consistency review against `Makefile`, `docker-compose.yml`, `frontend/package.json`, and both Dockerfiles

**Interfaces:**
- Consumes: current service definitions and commands in the repository.
- Produces: a README whose stack, prerequisites, local commands, API entry points, and architecture describe the actual deployed application.

- [ ] **Step 1: Correct the frontend and backend stack description**

Describe React/Vite served by the frontend Nginx container, Spring Boot REST/GraphQL/WebSocket endpoints, and the Cortex worker built from the same repository. Remove the outdated Thymeleaf/HTMX description.

- [ ] **Step 2: Correct local setup and topology documentation**

Align prerequisites and commands with `Makefile` and `docker-compose.yml`. Distinguish the core `docker-up` stack from `docker-up-all`; describe PostgreSQL/PostGIS and Redis plus Kafka/Debezium as part of the full stack. Explain that `services/` contains a separate Maven scaffold and is not the application built by the root CI workflow.

- [ ] **Step 3: Review README claims against configuration**

Check every listed endpoint and command against the current controllers, Makefile targets, Compose services, and Dockerfiles. Remove or qualify claims that are not backed by these files.

- [ ] **Step 4: Commit the documentation change**

```bash
git add README.md
git commit -m "docs: align README with current application architecture"
```

### Task 3: Open and validate a draft pull request

**Files:**
- No additional repository files.

**Interfaces:**
- Consumes: the CI and README commits from Tasks 1 and 2.
- Produces: a draft PR with reviewable changes and completed CI results.

- [ ] **Step 1: Push the worktree branch and open a draft PR**

Use a title and body describing the frontend CI checks and architecture-documentation corrections. Include the spec and plan paths in the PR body.

- [ ] **Step 2: Wait for required checks and inspect failures**

Confirm the frontend job runs `npm ci`, lint, Vitest, and Vite build. Confirm existing backend checks still run and Docker publishing is skipped for the PR event. If any check fails, identify the failing command and fix only issues required by this spec.

- [ ] **Step 3: Report the PR and check results**

Leave the PR in draft for review. Do not merge it.
