package io.github.opencivilizationplatform.modules.cortex.domain;

public record AgentIntrospection(
        String agentId,
        String activeGoal,
        String memoryState,
        String recentDecisions
) {
}
