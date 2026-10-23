package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "saga.found-civilization.initial-delay-ms=3600000")
@ActiveProfiles("test")
class FoundCivilizationSagaWorkerTest {
    @Autowired SagaExecutionRepository executions;
    @Autowired TransactionTemplate transactions;
    @MockitoBean FoundCivilizationSagaSteps productionSteps;
    @MockitoBean(name = "container") RedisMessageListenerContainer redisListener;
    private MutableClock clock;
    private FoundCivilizationSagaWorker worker;
    private final List<SagaStatusResponse> published = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();

    @BeforeEach void setup() {
        executions.deleteAll();
        clock = new MutableClock();
        published.clear();
        actions.clear();
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps, published::add, clock);
        installSteps(null, null, null);
    }

    @Test void persistsPotentiallyAppliedBeforeCallingStep() {
        UUID id = pending();
        installSteps(ctx -> {
            SagaExecution current = executions.findById(ctx.getSagaId()).orElseThrow();
            assertEquals(id, ctx.getSagaId());
            assertEquals(SagaStepStatus.POTENTIALLY_APPLIED, current.getSteps().get(0).getStatus());
            assertEquals(1, current.getSteps().get(0).getExecutionAttempts());
        }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
        assertEquals(3, published.getLast().steps().size());
    }

    @Test void publishesOnlyCommittedProgress() {
        UUID id = pending();
        List<Long> observedSequences = new ArrayList<>();
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps, status -> {
            SagaExecution committed = executions.findById(status.sagaId()).orElseThrow();
            assertEquals(status.status(), committed.getStatus());
            assertEquals(status.sequence(), committed.getSequence());
            observedSequences.add(status.sequence());
        }, clock);
        assertTrue(worker.processNextDue());
        assertFalse(observedSequences.isEmpty());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
    }

    @Test void retriesTransientFailureAtMostThreeAttempts() {
        UUID id = pending();
        installSteps(ctx -> { throw new RuntimeException(new ConnectException("offline")); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals(1, first(id).getExecutionAttempts());
        assertEquals(clock.instant().plusSeconds(1), first(id).getNextAttemptAt());
        assertFalse(worker.processNextDue());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(worker.processNextDue());
        assertEquals(2, first(id).getExecutionAttempts());
        assertEquals(clock.instant().plusSeconds(2), first(id).getNextAttemptAt());
        clock.advance(Duration.ofSeconds(2));
        assertTrue(worker.processNextDue());
        assertEquals(3, first(id).getExecutionAttempts());
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
        assertFalse(worker.processNextDue());
    }

    @Test void retriesTimeoutAndHttpServerFailure() {
        UUID timeoutId = pending();
        installSteps(ctx -> { throw new RuntimeException(new SocketTimeoutException("slow")); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals("TRANSIENT", first(timeoutId).getLastErrorCode());
        UUID serverId = pending();
        installSteps(ctx -> { throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals("TRANSIENT", first(serverId).getLastErrorCode());
    }

    @Test void doesNotRetryDomainFailure() {
        UUID id = pending();
        installSteps(ctx -> { throw new IllegalArgumentException("bad region"); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals(1, first(id).getExecutionAttempts());
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
        assertEquals("DOMAIN", first(id).getLastErrorCode());
    }

    @Test void failsWithoutCompensationWhenNoEffectWasApplied() {
        UUID id = pending();
        transactions.executeWithoutResult(ignored -> {
            SagaExecution saga = executions.findById(id).orElseThrow();
            saga.getSteps().get(0).setStatus(SagaStepStatus.FAILED);
            saga.setStatus(SagaStatus.RUNNING);
        });
        assertTrue(worker.processNextDue());
        assertEquals(SagaStatus.FAILED, reload(id).getStatus());
        assertTrue(actions.isEmpty());
    }

    @Test void compensatesFailedStepAndEarlierStepsInReverse() {
        UUID id = pending();
        installSteps(null, null, ctx -> { throw new IllegalStateException("domain failure"); });
        assertTrue(worker.processNextDue());
        assertEquals(List.of("execute-0", "execute-1", "execute-2", "compensate-2", "compensate-1", "compensate-0"), actions);
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
        assertTrue(reload(id).getSteps().stream().allMatch(s -> s.getCompensationStatus() == SagaStepStatus.COMPENSATED));
    }

    @Test void persistsCompensationFailure() {
        UUID id = pending();
        installSteps(null, ctx -> { throw new IllegalStateException("domain failure"); }, null);
        when(productionSteps.claimRegion()).thenReturn(step(1, ctx -> { throw new IllegalStateException("domain failure"); },
            ctx -> { actions.add("compensate-1"); throw new IllegalStateException("undo failed"); }));
        assertTrue(worker.processNextDue());
        assertEquals(SagaStatus.COMPENSATION_FAILED, reload(id).getStatus());
        assertEquals(SagaStepStatus.COMPENSATION_FAILED, reload(id).getSteps().get(1).getCompensationStatus());
        assertEquals("undo failed", reload(id).getSteps().get(1).getCompensationErrorMessage());
        assertEquals(SagaStepStatus.COMPENSATED, reload(id).getSteps().get(0).getCompensationStatus());
    }

    @Test void expiredLeaseContinuesPersistedAttemptCount() {
        UUID id = pending();
        transactions.executeWithoutResult(ignored -> {
            SagaExecution saga = executions.findById(id).orElseThrow();
            saga.setStatus(SagaStatus.RUNNING);
            saga.setLeaseUntil(clock.instant().minusSeconds(1));
            saga.getSteps().get(0).setStatus(SagaStepStatus.POTENTIALLY_APPLIED);
            saga.getSteps().get(0).setExecutionAttempts(2);
        });
        installSteps(ctx -> { throw new RuntimeException(new ConnectException("offline")); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals(3, first(id).getExecutionAttempts());
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
    }

    private UUID pending() {
        return executions.saveAndFlush(new SagaExecution("owner", UUID.randomUUID().toString(), "fingerprint",
            "New World", CivilizationScale.LOCAL, 7L)).getId();
    }
    private SagaExecution reload(UUID id) { return executions.findById(id).orElseThrow(); }
    private SagaStepExecution first(UUID id) { return reload(id).getSteps().get(0); }

    private void installSteps(java.util.function.Consumer<FoundCivilizationContext> first,
                              java.util.function.Consumer<FoundCivilizationContext> second,
                              java.util.function.Consumer<FoundCivilizationContext> third) {
        when(productionSteps.createCivilization()).thenReturn(step(0, first, null));
        when(productionSteps.claimRegion()).thenReturn(step(1, second, null));
        when(productionSteps.deployNexusNode()).thenReturn(step(2, third, null));
    }

    private SagaStep<FoundCivilizationContext> step(int index,
            java.util.function.Consumer<FoundCivilizationContext> execute,
            java.util.function.Consumer<FoundCivilizationContext> compensate) {
        return new SagaStep<>() {
            public void execute(FoundCivilizationContext ctx) {
                actions.add("execute-" + index);
                if (execute != null) execute.accept(ctx);
            }
            public void compensate(FoundCivilizationContext ctx) {
                actions.add("compensate-" + index);
                if (compensate != null) compensate.accept(ctx);
            }
            public String getName() { return "step-" + index; }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T12:00:00Z");
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }
}
