package io.github.opencivilizationplatform.saga;

public enum SagaStepStatus {
    PENDING, POTENTIALLY_APPLIED, IN_PROGRESS, SUCCEEDED, FAILED,
    COMPENSATING, COMPENSATED, COMPENSATION_FAILED
}
