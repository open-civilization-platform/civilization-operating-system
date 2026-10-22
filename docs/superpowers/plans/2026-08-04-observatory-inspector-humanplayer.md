# Planetary Observatory, Simulation Inspector & Human Player Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Planetary Observatory (Issue #62), Simulation Inspector API (Issue #59), and Human Player Engine (Issue #58).

---

### Task 1: Planetary Observatory & Simulation Inspector Engines (Issues #62 & #59)

**Files:**
- Create: `src/main/java/io/github/opencivilizationplatform/modules/universe/domain/PlanetaryTelemetry.java`
- Create: `src/main/java/io/github/opencivilizationplatform/modules/universe/application/PlanetaryObservatoryService.java`
- Create: `src/test/java/io/github/opencivilizationplatform/modules/universe/PlanetaryObservatoryServiceTest.java`
- Create: `src/main/java/io/github/opencivilizationplatform/modules/cortex/domain/AgentIntrospection.java`
- Create: `src/main/java/io/github/opencivilizationplatform/modules/cortex/application/SimulationInspectorService.java`
- Create: `src/test/java/io/github/opencivilizationplatform/modules/cortex/SimulationInspectorServiceTest.java`

- [x] **Step 1: Create `PlanetaryTelemetry.java` record and `PlanetaryObservatoryService.java` and unit tests**
- [x] **Step 2: Create `AgentIntrospection.java` record and `SimulationInspectorService.java` and unit tests**
- [ ] **Step 3: Run `./mvnw test` to verify unit tests pass**
- [ ] **Step 4: Commit Task 1**

---

### Task 2: Human Player Engine & SimulationEngine Integration (Issue #58)

**Files:**
- Create: `src/main/java/io/github/opencivilizationplatform/modules/social/domain/HumanPlayer.java`
- Create: `src/main/java/io/github/opencivilizationplatform/modules/social/application/HumanPlayerService.java`
- Create: `src/test/java/io/github/opencivilizationplatform/modules/social/HumanPlayerServiceTest.java`
- Modify: `src/main/java/io/github/opencivilizationplatform/modules/simulation/application/SimulationEngineService.java`
- Modify: `src/test/java/io/github/opencivilizationplatform/modules/simulation/application/SimulationEngineServiceTest.java`

- [x] **Step 1: Create `HumanPlayer.java` record and `HumanPlayerService.java` and unit tests**
- [x] **Step 2: Inject `PlanetaryObservatoryService`, `SimulationInspectorService`, and `HumanPlayerService` into `SimulationEngineService.java` with `@Autowired`**
- [ ] **Step 3: Run `./mvnw test` to verify 100% green build**
- [ ] **Step 4: Push branch `feat/observatory-inspector-humanplayer`, open a separate draft PR, and watch CI**

## Execution Notes

- PR #85 currently targets issue #26 (federated learning), so it is unrelated to this plan and remains untouched. This work will use its own draft PR.
- Focused verification: `./mvnw -Dtest=SimulationEngineServiceTest,PlanetaryObservatoryServiceTest,SimulationInspectorServiceTest,HumanPlayerServiceTest test` — 14 tests passed.
- Full-suite verification remains pending: integration contexts require Redis; attempts to start the shared Testcontainers Redis failed because the Docker daemon was unavailable.
