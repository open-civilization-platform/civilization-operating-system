package io.github.opencivilizationplatform.saga;

public record SagaCompensationView(SagaStepStatus status, int attempts, SagaFailureView failure) {}
