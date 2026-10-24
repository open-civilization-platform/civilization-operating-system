package io.github.opencivilizationplatform.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SagaStatusResponse(UUID sagaId, SagaStatus status, String currentStep,
                                 String failedStep, Long civilizationId, String name, SagaFailureView failure,
                                 List<SagaStepView> steps, long sequence,
                                 Instant createdAt, Instant updatedAt) {
    static SagaStatusResponse from(SagaExecution execution) {
        return new SagaStatusResponse(execution.getId(), execution.getStatus(),
            execution.getCurrentStep(), execution.getFailedStep(), execution.getCivilizationId(),
            execution.getCivilizationName(),
            failure(execution.getFailureCode(), execution.getFailureMessage()),
            execution.getSteps().stream().map(step -> new SagaStepView(step.getStepKey(),
                step.getStatus(), step.getExecutionAttempts(), step.getNextAttemptAt(),
                failure(step.getLastErrorCode(), step.getLastErrorMessage()),
                step.getCompensationStatus() == null ? null : new SagaCompensationView(
                    step.getCompensationStatus(), step.getCompensationAttempts(),
                    failure(step.getCompensationErrorCode(), step.getCompensationErrorMessage())))).toList(),
            execution.getSequence(), execution.getCreatedAt(), execution.getUpdatedAt());
    }

    private static SagaFailureView failure(String code, String message) {
        return code == null && message == null ? null : new SagaFailureView(code, message);
    }
}
