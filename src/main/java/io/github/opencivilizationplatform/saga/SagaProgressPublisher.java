package io.github.opencivilizationplatform.saga;

@FunctionalInterface
public interface SagaProgressPublisher {
    void publish(SagaStatusResponse status);
}
