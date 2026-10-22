package io.github.opencivilizationplatform.modules.cortex;

import io.github.opencivilizationplatform.modules.cortex.application.SimulationInspectorService;
import io.github.opencivilizationplatform.modules.cortex.domain.AgentIntrospection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SimulationInspectorServiceTest {

    private SimulationInspectorService service;

    @BeforeEach
    void setUp() {
        service = new SimulationInspectorService();
    }

    @Test
    void shouldRegisterAndRetrieveIntrospection() {
        AgentIntrospection introspection = new AgentIntrospection("agent-101", "EXPAND_RESOURCE", "STABLE", "MINED_IRON");
        service.registerIntrospection(introspection);

        Optional<AgentIntrospection> retrieved = service.getIntrospection("agent-101");
        assertTrue(retrieved.isPresent());
        assertEquals("EXPAND_RESOURCE", retrieved.get().activeGoal());
        assertEquals("MINED_IRON", retrieved.get().recentDecisions());
    }

    @Test
    void shouldReturnEmptyForUnknownAgent() {
        Optional<AgentIntrospection> retrieved = service.getIntrospection("agent-999");
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void shouldReturnAllIntrospections() {
        service.registerIntrospection(new AgentIntrospection("agent-1", "GOAL_1", "MEM_1", "DEC_1"));
        service.registerIntrospection(new AgentIntrospection("agent-2", "GOAL_2", "MEM_2", "DEC_2"));

        assertEquals(2, service.getAllIntrospections().size());
    }

    @Test
    void shouldProcessInspectorTick() {
        assertDoesNotThrow(() -> service.processInspectorTick());
    }
}
