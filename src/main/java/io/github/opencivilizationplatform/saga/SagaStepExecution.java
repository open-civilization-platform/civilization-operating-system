package io.github.opencivilizationplatform.saga;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step_executions", uniqueConstraints = @UniqueConstraint(columnNames = {"saga_id", "step_key"}))
public class SagaStepExecution {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaExecution saga;

    @Column(name = "step_key", nullable = false, length = 40)
    private String stepKey;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaStepStatus status = SagaStepStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_status", length = 32)
    private SagaStepStatus compensationStatus;

    @Column(name = "execution_attempts", nullable = false)
    private int executionAttempts;

    @Column(name = "compensation_attempts", nullable = false)
    private int compensationAttempts;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "compensation_error_code", length = 80)
    private String compensationErrorCode;

    @Column(name = "compensation_error_message", length = 1000)
    private String compensationErrorMessage;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SagaStepExecution() {}

    SagaStepExecution(SagaExecution saga, String stepKey, int stepOrder) {
        this.saga = saga;
        this.stepKey = stepKey;
        this.stepOrder = stepOrder;
    }

    public UUID getId() { return id; }
    public SagaExecution getSaga() { return saga; }
    public String getStepKey() { return stepKey; }
    public int getStepOrder() { return stepOrder; }
    public SagaStepStatus getStatus() { return status; }
    public void setStatus(SagaStepStatus status) { this.status = status; }
    public SagaStepStatus getCompensationStatus() { return compensationStatus; }
    public void setCompensationStatus(SagaStepStatus compensationStatus) { this.compensationStatus = compensationStatus; }
    public int getExecutionAttempts() { return executionAttempts; }
    public void setExecutionAttempts(int executionAttempts) { this.executionAttempts = executionAttempts; }
    public int getCompensationAttempts() { return compensationAttempts; }
    public void setCompensationAttempts(int compensationAttempts) { this.compensationAttempts = compensationAttempts; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public String getCompensationErrorCode() { return compensationErrorCode; }
    public void setCompensationErrorCode(String compensationErrorCode) { this.compensationErrorCode = compensationErrorCode; }
    public String getCompensationErrorMessage() { return compensationErrorMessage; }
    public void setCompensationErrorMessage(String compensationErrorMessage) { this.compensationErrorMessage = compensationErrorMessage; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
