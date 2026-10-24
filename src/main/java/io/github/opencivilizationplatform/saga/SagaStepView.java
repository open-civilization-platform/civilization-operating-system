package io.github.opencivilizationplatform.saga;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SagaStepView(String key, SagaStepStatus status, int attempts,
                           Instant nextAttemptAt, SagaFailureView failure,
                           SagaCompensationView compensation) {
    @JsonProperty("label")
    public String label() {
        return switch (key) {
            case "CREATE_CIVILIZATION" -> "Civilization created";
            case "CLAIM_REGION" -> "Region claimed";
            case "DEPLOY_NEXUS_NODE" -> "Nexus node deployed";
            default -> key;
        };
    }
}
