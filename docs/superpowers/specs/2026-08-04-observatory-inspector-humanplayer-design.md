# Planetary Observatory, Simulation Inspector & Human Player Engine Design Specification (Issues #62, #59, #58)

## Objective
Implement three core backend simulation engines:
1. **Planetary Observatory & Global Telemetry Engine (Issue #62)**: Planetary atmospheric metrics, orbital telemetry, and biosphere health scores.
2. **Simulation Inspector & Agent Introspection API (Issue #59)**: Live agent brain state inspection, decision trees, and debug telemetry.
3. **Human Player Engine (Issue #58)**: Human player session management, direct action execution, and hybrid human-AI voting.

## Component Architecture

### 1. Planetary Observatory Service (`io.github.opencivilizationplatform.modules.universe.application.PlanetaryObservatoryService`)
- Tracks planetary metrics:
  - `PlanetaryTelemetry(globalTemp, atmosphericCarbon, orbitalSatellites, biosphereHealthScore)`
  - Broadcasts global planetary observation reports.

### 2. Simulation Inspector Service (`io.github.opencivilizationplatform.modules.cortex.application.SimulationInspectorService`)
- Live introspection API:
  - `AgentIntrospection(agentId, activeGoal, memoryState, recentDecisions)`
  - Debug event stream for real-time visual inspection.

### 3. Human Player Service (`io.github.opencivilizationplatform.modules.social.application.HumanPlayerService`)
- Human-AI co-existence:
  - `HumanPlayer(playerId, username, assignedCivilizationId, influenceTokens)`
  - Processes player actions and merges votes with AI agent decisions.

## Verification Plan
1. Unit tests for `PlanetaryObservatoryServiceTest`, `SimulationInspectorServiceTest`, and `HumanPlayerServiceTest`.
2. `./mvnw test` ensuring 100% green build.
