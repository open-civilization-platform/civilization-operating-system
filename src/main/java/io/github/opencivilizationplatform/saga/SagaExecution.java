package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "saga_executions", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "idempotency_key"}))
public class SagaExecution {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 128)
    private String requestFingerprint;

    @Column(name = "civilization_name", nullable = false)
    private String civilizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "civilization_scale", nullable = false, length = 32)
    private CivilizationScale civilizationScale;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SagaStatus status = SagaStatus.PENDING;

    @Column(name = "current_step", length = 40)
    private String currentStep;

    @Column(name = "failed_step", length = 40)
    private String failedStep;

    @Column(name = "civilization_id")
    private Long civilizationId;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(nullable = false)
    private long sequence;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepOrder ASC")
    private List<SagaStepExecution> steps = new ArrayList<>();

    protected SagaExecution() {}

    public SagaExecution(String ownerId, String idempotencyKey, String requestFingerprint,
                         String civilizationName, CivilizationScale civilizationScale, Long regionId) {
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.civilizationName = civilizationName;
        this.civilizationScale = civilizationScale;
        this.regionId = regionId;
        steps.add(new SagaStepExecution(this, "CREATE_CIVILIZATION", 0));
        steps.add(new SagaStepExecution(this, "CLAIM_REGION", 1));
        steps.add(new SagaStepExecution(this, "DEPLOY_NEXUS_NODE", 2));
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getCivilizationName() { return civilizationName; }
    public CivilizationScale getCivilizationScale() { return civilizationScale; }
    public Long getRegionId() { return regionId; }
    public SagaStatus getStatus() { return status; }
    public void setStatus(SagaStatus status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }
    public Long getCivilizationId() { return civilizationId; }
    public void setCivilizationId(Long civilizationId) { this.civilizationId = civilizationId; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public long getSequence() { return sequence; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<SagaStepExecution> getSteps() { return Collections.unmodifiableList(steps); }
}
