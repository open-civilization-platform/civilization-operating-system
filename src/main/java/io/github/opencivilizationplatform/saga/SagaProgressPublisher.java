package io.github.opencivilizationplatform.saga;

import java.util.UUID;

@FunctionalInterface
public interface SagaProgressPublisher {
    void publish(String ownerId, UUID sagaId, SagaStatusResponse snapshot, String currentStep,
                 SagaFailureView failure, SagaCompensationView compensation);
}
