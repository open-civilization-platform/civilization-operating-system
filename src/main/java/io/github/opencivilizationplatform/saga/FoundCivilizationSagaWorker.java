package io.github.opencivilizationplatform.saga;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.ConnectException;
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
        this(executions, transactions, steps, publisher.getIfAvailable(() -> status -> {}), Clock.systemUTC());
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
        UUID id = claimed.get().getId();
        advance(id);
        return true;
    }

    private void advance(UUID id) {
        SagaExecution saga = load(id);
        FoundCivilizationContext context = context(saga);
        List<SagaStep<FoundCivilizationContext>> effects = SagaOrchestrator.foundCivilizationSteps(steps);
        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            compensate(id, context, effects);
            return;
        }
        if (saga.getSteps().stream().anyMatch(step -> step.getStatus() == SagaStepStatus.FAILED)) {
            failOrCompensate(id, context, effects);
            return;
        }
        for (int i = 0; i < effects.size(); i++) {
            SagaStepExecution step = load(id).getSteps().get(i);
            if (step.getStatus() == SagaStepStatus.SUCCEEDED) continue;
            if (step.getStatus() == SagaStepStatus.POTENTIALLY_APPLIED
                && step.getExecutionAttempts() >= MAX_ATTEMPTS) {
                markExhausted(id, i);
                failOrCompensate(id, context, effects);
                return;
            }
            markPotentiallyApplied(id, i);
            try {
                effects.get(i).execute(context);
                markSucceeded(id, i, context);
            } catch (Exception failure) {
                boolean transientFailure = transientFailure(failure);
                if (transientFailure && load(id).getSteps().get(i).getExecutionAttempts() < MAX_ATTEMPTS) {
                    scheduleRetry(id, i, failure);
                    return;
                }
                markFailed(id, i, failure, transientFailure);
                failOrCompensate(id, context, effects);
                return;
            }
        }
        change(id, sagaRow -> {
            sagaRow.setStatus(SagaStatus.SUCCEEDED);
            sagaRow.setCurrentStep(null);
            sagaRow.setLeaseUntil(null);
        });
    }

    private void markPotentiallyApplied(UUID id, int index) {
        change(id, saga -> {
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

    private void markSucceeded(UUID id, int index, FoundCivilizationContext context) {
        change(id, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.SUCCEEDED);
            step.setLastErrorCode(null);
            step.setLastErrorMessage(null);
            step.setUpdatedAt(clock.instant());
            if (context.getCivilizationId() != null) saga.setCivilizationId(context.getCivilizationId());
        });
    }

    private void scheduleRetry(UUID id, int index, Exception failure) {
        change(id, saga -> {
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

    private void markFailed(UUID id, int index, Exception failure, boolean transientFailure) {
        change(id, saga -> {
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

    private void markExhausted(UUID id, int index) {
        change(id, saga -> {
            SagaStepExecution step = saga.getSteps().get(index);
            step.setStatus(SagaStepStatus.FAILED);
            saga.setFailedStep(step.getStepKey());
            saga.setFailureCode(step.getLastErrorCode() == null ? "TRANSIENT" : step.getLastErrorCode());
            saga.setFailureMessage(step.getLastErrorMessage() == null ? "Attempt limit reached" : step.getLastErrorMessage());
        });
    }

    private void failOrCompensate(UUID id, FoundCivilizationContext context,
                                  List<SagaStep<FoundCivilizationContext>> effects) {
        SagaExecution saga = load(id);
        boolean applied = saga.getSteps().stream().anyMatch(step -> step.getStatus() == SagaStepStatus.SUCCEEDED
            || step.getStatus() == SagaStepStatus.POTENTIALLY_APPLIED
            || (step.getStatus() == SagaStepStatus.FAILED && step.getExecutionAttempts() > 0));
        if (!applied) {
            change(id, row -> {
                row.setStatus(SagaStatus.FAILED);
                row.setLeaseUntil(null);
            });
            return;
        }
        change(id, row -> row.setStatus(SagaStatus.COMPENSATING));
        compensate(id, context, effects);
    }

    private void compensate(UUID id, FoundCivilizationContext context,
                            List<SagaStep<FoundCivilizationContext>> effects) {
        for (int i = effects.size() - 1; i >= 0; i--) {
            SagaStepExecution step = load(id).getSteps().get(i);
            if (step.getExecutionAttempts() == 0 || step.getCompensationStatus() == SagaStepStatus.COMPENSATED
                || step.getCompensationStatus() == SagaStepStatus.COMPENSATION_FAILED) continue;
            int index = i;
            change(id, saga -> {
                SagaStepExecution current = saga.getSteps().get(index);
                current.setCompensationStatus(SagaStepStatus.COMPENSATING);
                current.setCompensationAttempts(current.getCompensationAttempts() + 1);
                current.setUpdatedAt(clock.instant());
            });
            try {
                effects.get(i).compensate(context);
                change(id, saga -> {
                    SagaStepExecution current = saga.getSteps().get(index);
                    current.setCompensationStatus(SagaStepStatus.COMPENSATED);
                    current.setUpdatedAt(clock.instant());
                });
            } catch (Exception failure) {
                change(id, saga -> {
                    SagaStepExecution current = saga.getSteps().get(index);
                    current.setCompensationStatus(SagaStepStatus.COMPENSATION_FAILED);
                    current.setCompensationErrorCode("COMPENSATION_ERROR");
                    current.setCompensationErrorMessage(message(failure));
                    current.setUpdatedAt(clock.instant());
                });
            }
        }
        change(id, saga -> {
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

    private void change(UUID id, java.util.function.Consumer<SagaExecution> update) {
        SagaStatusResponse committed = transactions.execute(ignored -> {
            executions.incrementSequence(id);
            SagaExecution saga = executions.findById(id).orElseThrow();
            update.accept(saga);
            saga.setUpdatedAt(clock.instant());
            executions.saveAndFlush(saga);
            return SagaStatusResponse.from(saga);
        });
        publisher.publish(committed);
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
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException
                || current instanceof ConnectException || current instanceof UnknownHostException) return true;
            if (current instanceof HttpStatusCodeException http && http.getStatusCode().is5xxServerError()) return true;
        }
        return false;
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 1000));
    }
}
