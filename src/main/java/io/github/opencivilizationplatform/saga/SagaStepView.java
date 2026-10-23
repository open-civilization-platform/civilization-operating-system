package io.github.opencivilizationplatform.saga;

import java.time.Instant;

public record SagaStepView(String key, SagaStepStatus status, int attempts,
                           Instant nextAttemptAt, SagaFailureView failure,
                           SagaCompensationView compensation) {}
