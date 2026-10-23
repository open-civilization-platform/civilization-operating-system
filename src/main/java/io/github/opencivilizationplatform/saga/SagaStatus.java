package io.github.opencivilizationplatform.saga;

public enum SagaStatus {
    PENDING, RUNNING, COMPENSATING, SUCCEEDED, FAILED, COMPENSATED, COMPENSATION_FAILED
}
