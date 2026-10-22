package io.github.opencivilizationplatform.modules.cortex.application;

import io.github.opencivilizationplatform.modules.cortex.domain.AgentIntrospection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulationInspectorService {

    private static final Logger log = LoggerFactory.getLogger(SimulationInspectorService.class);

    private final Map<String, AgentIntrospection> introspectionRegistry = new ConcurrentHashMap<>();

    public void registerIntrospection(AgentIntrospection introspection) {
        introspectionRegistry.put(introspection.agentId(), introspection);
        log.info("[SIMULATION INSPECTOR] Registered introspection state for agent: {}", introspection.agentId());
    }

    public Optional<AgentIntrospection> getIntrospection(String agentId) {
        return Optional.ofNullable(introspectionRegistry.get(agentId));
    }

    public Collection<AgentIntrospection> getAllIntrospections() {
        return introspectionRegistry.values();
    }

    public void processInspectorTick() {
        log.info("[SIMULATION INSPECTOR TICK] Monitored agents: {}", introspectionRegistry.size());
    }
}
