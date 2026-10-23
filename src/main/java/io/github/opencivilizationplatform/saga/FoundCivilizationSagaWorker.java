package io.github.opencivilizationplatform.saga;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FoundCivilizationSagaWorker {
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;
    private final SagaExecutionRepository executions;
    private final TransactionTemplate transactions;
    private final FoundCivilizationSagaSteps steps;
    private final SagaProgressPublisher publisher;
    private final Clock clock;

    @Autowired
    public FoundCivilizationSagaWorker(SagaExecutionRepository executions, TransactionTemplate transactions,
                                       FoundCivilizationSagaSteps steps, ObjectProvider<SagaProgressPublisher> publisher) {
        this(executions, transactions, steps,
            publisher.getIfAvailable(() -> (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {}),
            Clock.systemUTC());
    }

    FoundCivilizationSagaWorker(SagaExecutionRepository executions, TransactionTemplate transactions,
                                FoundCivilizationSagaSteps steps, SagaProgressPublisher publisher, Clock clock) {
        this.executions = executions;
        this.transactions = transactions;
        this.steps = steps;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${saga.found-civilization.poll-ms:5000}",
               initialDelayString = "${saga.found-civilization.initial-delay-ms:5000}")
    public void poll() {
        while (processNextDue()) { /* drain due work */ }
    }

    public boolean processNextDue() {
        var claimed = executions.claimNextDue(clock.instant(), LEASE);
        if (claimed.isEmpty()) return false;
        Claim claim = new Claim(claimed.get().getId(), claimed.get().getLeaseUntil());
        try {
            advance(claim);
        } catch (LostLease ignored) {
            // Another worker owns the recovered execution. Its persisted state is authoritative.
        }
        return true;
    }

    private void advance(Claim claim) {
        UUID id = claim.id();
        SagaExecution saga = load(id);
        FoundCivilizationContext context = context(saga);
        List<SagaStep<FoundCivilizationContext>> effects = SagaOrchestrator.foundCivilizationSteps(steps);
        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            compensate(claim, context, effects);
            return;
        }
        if (saga.getSteps().stream().anyMatch(step -> step.getStatus() == SagaStepStatus.FAILED)) {
            failOrCompensate(claim, context, effects);
            return;
        }
        for (int i = 0; i < effects.size(); i++) {
            SagaStepExecution step = load(id).getSteps().get(i);
            if (step.getStatus() == SagaStepStatus.SUCCEEDED) continue;
            if (step.getStatus() == SagaStepStatus.POTENTIALLY_APPLIED
                && step.getExecutionAttempts() >= MAX_ATTEMPTS) {
                markExhausted(claim, i);
                failOrCompensate(claim, context, effects);
                return;
            }
            markPotentiallyApplied(claim, i);
            try {
                assertOwned(claim);
                effects.get(i).execute(context);
                markSucceeded(claim, i, context);
            } catch (Exception failure) {
                if (failure instanceof LostLease lost) throw lost;
                boolean transientFailure = transientFailure(failure);
                if (transientFailure && load(id).getSteps().get(i).getExecutionAttempts() < MAX_ATTEMPTS) {
                    scheduleRetry(claim, i, failure);
                    return;
                }
                markFailed(claim, i, failure, transientFailure);
                failOrCompensate(claim, context, effects);
                return;
            }
        }
        change(claim, sagaRow -> {
            sagaRow.setStatus(SagaStatus.SUCCEEDED);
            sagaRow.setCurrentStep(null);
            sagaRow.setLeaseUntil(null);
        });
    }

    private void markPotentiallyApplied(Claim claim, int index) {
        change(claim, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.POTENTIALLY_APPLIED);
            step.setExecutionAttempts(step.getExecutionAttempts() + 1);
            step.setNextAttemptAt(null);
            step.setUpdatedAt(clock.instant());
            saga.setStatus(SagaStatus.RUNNING);
            saga.setCurrentStep(step.getStepKey());
            saga.setNextAttemptAt(null);
            saga.setHeartbeatAt(clock.instant());
        });
    }

    private void markSucceeded(Claim claim, int index, FoundCivilizationContext context) {
        change(claim, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.SUCCEEDED);
            step.setLastErrorCode(null);
            step.setLastErrorMessage(null);
            step.setUpdatedAt(clock.instant());
            if (context.getCivilizationId() != null) saga.setCivilizationId(context.getCivilizationId());
        });
    }

    private void scheduleRetry(Claim claim, int index, Exception failure) {
        change(claim, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            Instant next = clock.instant().plusSeconds(1L << (step.getExecutionAttempts() - 1));
            step.setLastErrorCode("TRANSIENT");
            step.setLastErrorMessage(message(failure));
            step.setNextAttemptAt(next);
            step.setUpdatedAt(clock.instant());
            saga.setNextAttemptAt(next);
            saga.setLeaseUntil(null);
        });
    }

    private void markFailed(Claim claim, int index, Exception failure, boolean transientFailure) {
        change(claim, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.FAILED);
            step.setLastErrorCode(transientFailure ? "TRANSIENT" : "DOMAIN");
            step.setLastErrorMessage(message(failure));
            step.setUpdatedAt(clock.instant());
            saga.setFailedStep(step.getStepKey());
            saga.setFailureCode(step.getLastErrorCode());
            saga.setFailureMessage(step.getLastErrorMessage());
        });
    }

    private void markExhausted(Claim claim, int index) {
        change(claim, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.FAILED);
            saga.setFailedStep(step.getStepKey());
            saga.setFailureCode(step.getLastErrorCode() == null ? "TRANSIENT" : step.getLastErrorCode());
            saga.setFailureMessage(step.getLastErrorMessage() == null ? "Attempt limit reached" : step.getLastErrorMessage());
        });
    }

    private void failOrCompensate(Claim claim, FoundCivilizationContext context,
                                  List<SagaStep<FoundCivilizationContext>> effects) {
        SagaExecution saga = load(claim.id());
        boolean applied = saga.getSteps().stream().anyMatch(step -> step.getStatus() == SagaStepStatus.SUCCEEDED
            || step.getStatus() == SagaStepStatus.POTENTIALLY_APPLIED
            || (step.getStatus() == SagaStepStatus.FAILED && step.getExecutionAttempts() > 0));
        if (!applied) {
            change(claim, row -> {
                row.setStatus(SagaStatus.FAILED);
                row.setLeaseUntil(null);
            });
            return;
        }
        change(claim, row -> row.setStatus(SagaStatus.COMPENSATING));
        compensate(claim, context, effects);
    }

    private void compensate(Claim claim, FoundCivilizationContext context,
                            List<SagaStep<FoundCivilizationContext>> effects) {
        for (int i = effects.size() - 1; i >= 0; i--) {
            SagaStepExecution step = load(claim.id()).getSteps().get(i);
            if (step.getExecutionAttempts() == 0 || step.getCompensationStatus() == SagaStepStatus.COMPENSATED
                || step.getCompensationStatus() == SagaStepStatus.COMPENSATION_FAILED) continue;
            int index = i;
            change(claim, saga -> {
                SagaStepExecution current = saga.getSteps().get(index);
                saga.setCurrentStep(current.getStepKey());
                current.setCompensationStatus(SagaStepStatus.COMPENSATING);
                current.setCompensationAttempts(current.getCompensationAttempts() + 1);
                current.setUpdatedAt(clock.instant());
            });
            try {
                assertOwned(claim);
                effects.get(i).compensate(context);
                change(claim, saga -> {
                    SagaStepExecution current = saga.getSteps().get(index);
                    current.setCompensationStatus(SagaStepStatus.COMPENSATED);
                    current.setUpdatedAt(clock.instant());
                });
            } catch (Exception failure) {
                if (failure instanceof LostLease lost) throw lost;
                change(claim, saga -> {
                    SagaStepExecution current = saga.getSteps().get(index);
                    current.setCompensationStatus(SagaStepStatus.COMPENSATION_FAILED);
                    current.setCompensationErrorCode("COMPENSATION_ERROR");
                    current.setCompensationErrorMessage(message(failure));
                    current.setUpdatedAt(clock.instant());
                });
            }
        }
        change(claim, saga -> {
            boolean failed = saga.getSteps().stream()
                .anyMatch(step -> step.getCompensationStatus() == SagaStepStatus.COMPENSATION_FAILED);
            saga.setStatus(failed ? SagaStatus.COMPENSATION_FAILED : SagaStatus.COMPENSATED);
            saga.setLeaseUntil(null);
            saga.setCurrentStep(null);
        });
    }

    private SagaExecution load(UUID id) {
        return transactions.execute(ignored -> executions.findById(id).orElseThrow());
    }

    private void assertOwned(Claim claim) {
        transactions.executeWithoutResult(ignored -> {
            executions.lockForEffect(claim.id()).orElseThrow();
            requireClaim(executions.findById(claim.id()).orElseThrow(), claim);
        });
    }

    private void change(Claim claim, java.util.function.Consumer<SagaExecution> update) {
        Progress committed = transactions.execute(ignored -> {
            executions.lockForEffect(claim.id()).orElseThrow();
            SagaExecution current = executions.findById(claim.id()).orElseThrow();
            requireClaim(current, claim);
            executions.incrementSequence(claim.id());
            SagaExecution saga = executions.findById(claim.id()).orElseThrow();
            update.accept(saga);
            saga.setUpdatedAt(clock.instant());
            executions.saveAndFlush(saga);
            return new Progress(saga.getOwnerId(), SagaStatusResponse.from(saga));
        });
        SagaStatusResponse snapshot = committed.snapshot();
        SagaCompensationView compensation = snapshot.steps().stream()
            .filter(step -> step.key().equals(snapshot.currentStep()))
            .map(SagaStepView::compensation).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        publisher.publish(committed.ownerId(), snapshot.sagaId(), snapshot, snapshot.currentStep(),
            snapshot.failure(), compensation);
    }

    private void requireClaim(SagaExecution current, Claim claim) {
        if (!claim.leaseUntil().equals(current.getLeaseUntil())
            || !clock.instant().isBefore(claim.leaseUntil())) throw new LostLease();
    }

    private static FoundCivilizationContext context(SagaExecution saga) {
        FoundCivilizationContext ctx = new FoundCivilizationContext();
        ctx.setSagaId(saga.getId());
        ctx.setName(saga.getCivilizationName());
        ctx.setScale(saga.getCivilizationScale());
        ctx.setRegionId(saga.getRegionId());
        ctx.setOwnerToken(saga.getOwnerId());
        ctx.setCivilizationId(saga.getCivilizationId());
        return ctx;
    }

    private static boolean transientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketException || current instanceof SocketTimeoutException
                || current instanceof HttpTimeoutException
                || current instanceof UnknownHostException) return true;
            if (current instanceof HttpStatusCodeException http && http.getStatusCode().is5xxServerError()) return true;
        }
        return false;
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 1000));
    }

    private record Claim(UUID id, Instant leaseUntil) {}
    private record Progress(String ownerId, SagaStatusResponse snapshot) {}
    private static final class LostLease extends RuntimeException {}
}
