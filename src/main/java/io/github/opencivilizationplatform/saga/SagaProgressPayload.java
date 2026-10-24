package io.github.opencivilizationplatform.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SagaProgressPayload(UUID sagaId, SagaStatus status, String currentStep,
                                  List<SagaStepView> steps, SagaFailureView failure,
                                  SagaCompensationView compensation, Instant updatedAt, long sequence) {}
