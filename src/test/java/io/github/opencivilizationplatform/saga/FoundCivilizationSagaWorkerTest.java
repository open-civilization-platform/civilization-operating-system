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
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "saga.found-civilization.initial-delay-ms=3600000")
@ActiveProfiles("test")
class FoundCivilizationSagaWorkerTest {
    @Autowired SagaExecutionRepository executions;
    @Autowired TransactionTemplate transactions;
    @MockitoBean FoundCivilizationSagaSteps productionSteps;
    @MockitoBean(name = "container") RedisMessageListenerContainer redisListener;
    @MockitoBean org.springframework.scheduling.TaskScheduler taskScheduler;
    private MutableClock clock;
    private FoundCivilizationSagaWorker worker;
    private final List<SagaStatusResponse> published = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();

    @BeforeEach void setup() {
        executions.deleteAll();
        clock = new MutableClock();
        published.clear();
        actions.clear();
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> published.add(snapshot), clock);
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
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {
            SagaExecution committed = executions.findById(sagaId).orElseThrow();
            assertEquals("owner", ownerId);
            assertEquals(id, sagaId);
            assertEquals(snapshot.status(), committed.getStatus());
            assertEquals(snapshot.sequence(), committed.getSequence());
            assertEquals(snapshot.currentStep(), currentStep);
            assertEquals(snapshot.failure(), failure);
            SagaCompensationView expectedCompensation = snapshot.steps().stream()
                .filter(step -> step.key().equals(currentStep))
                .map(SagaStepView::compensation).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            assertEquals(expectedCompensation, compensation);
            observedSequences.add(snapshot.sequence());
        }, clock);
        assertTrue(worker.processNextDue());
        assertFalse(observedSequences.isEmpty());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
    }

    @Test void publishesFailureAndCompensationDetails() {
        UUID id = pending();
        List<SagaFailureView> failures = new ArrayList<>();
        List<SagaCompensationView> compensations = new ArrayList<>();
        installSteps(ctx -> { throw new IllegalArgumentException("bad region"); }, null, null);
        when(productionSteps.createCivilization()).thenReturn(step(0,
            ctx -> { throw new IllegalArgumentException("bad region"); },
            ctx -> { throw new IllegalStateException("undo failed"); }));
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {
                assertEquals("owner", ownerId);
                assertEquals(id, sagaId);
                assertEquals(snapshot.sequence(), reload(id).getSequence());
                assertEquals(snapshot.failure(), failure);
                if (failure != null) failures.add(failure);
                if (compensation != null) compensations.add(compensation);
            }, clock);
        assertTrue(worker.processNextDue());
        assertTrue(failures.stream().anyMatch(failure -> "DOMAIN".equals(failure.code())));
        assertTrue(compensations.stream().anyMatch(compensation ->
            compensation.status() == SagaStepStatus.COMPENSATION_FAILED
                && "undo failed".equals(compensation.failure().message())));
        assertEquals(SagaStatus.COMPENSATION_FAILED, reload(id).getStatus());
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

    @Test void failedEffectRollsBackItsDatabaseMutationBeforeCompensation() {
        UUID id = pending();
        installSteps(ctx -> {
            executions.findById(ctx.getSagaId()).orElseThrow().setCivilizationId(99L);
            executions.flush();
            throw new IllegalStateException("partial effect");
        }, null, null);
        assertTrue(worker.processNextDue());
        assertNull(reload(id).getCivilizationId());
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
        assertTrue(actions.contains("compensate-0"));
    }

    @Test void retriesWrappedSocketConnectivityFailures() {
        UUID socketId = pending();
        installSteps(ctx -> { throw new RuntimeException(new SocketException("reset")); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals("TRANSIENT", first(socketId).getLastErrorCode());
        UUID routeId = pending();
        installSteps(ctx -> { throw new RuntimeException(new NoRouteToHostException("unreachable")); }, null, null);
        assertTrue(worker.processNextDue());
        assertEquals("TRANSIENT", first(routeId).getLastErrorCode());
    }

    @Test void staleLeaseHolderDoesNotStartEffectAfterReclaim() {
        UUID id = pending();
        AtomicInteger executionsOfFirstStep = new AtomicInteger();
        AtomicBoolean reclaimed = new AtomicBoolean();
        FoundCivilizationSagaWorker recovering = new FoundCivilizationSagaWorker(executions, transactions,
            productionSteps, (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {}, clock);
        installSteps(ctx -> executionsOfFirstStep.incrementAndGet(), null, null);
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {
                if (snapshot.status() == SagaStatus.RUNNING && reclaimed.compareAndSet(false, true)) {
                    clock.advance(Duration.ofMinutes(5).plusSeconds(1));
                    assertTrue(recovering.processNextDue());
                }
            }, clock);
        assertTrue(worker.processNextDue());
        assertEquals(1, executionsOfFirstStep.get());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
    }

    @Test void reclaimCannotInterleaveWithExecutingEffect() throws Exception {
        UUID id = pending();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inEffect = new AtomicInteger();
        AtomicInteger peakConcurrency = new AtomicInteger();
        List<SagaStatusResponse> firstWorkerEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        installSteps(ctx -> {
            int active = inEffect.incrementAndGet();
            peakConcurrency.accumulateAndGet(active, Math::max);
            entered.countDown();
            if (active == 1 && release.getCount() > 0) await(release);
            inEffect.decrementAndGet();
        }, null, null);
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> firstWorkerEvents.add(snapshot), clock);
        FoundCivilizationSagaWorker recovering = new FoundCivilizationSagaWorker(executions, transactions,
            productionSteps, (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {}, clock);
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(worker::processNextDue);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(recovering::processNextDue);
        try {
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            assertEquals(1, peakConcurrency.get());
        } finally { release.countDown(); }
        assertTrue(first.get(10, TimeUnit.SECONDS));
        assertTrue(second.get(10, TimeUnit.SECONDS));
        assertEquals(1, peakConcurrency.get());
        assertTrue(firstWorkerEvents.stream().noneMatch(event -> event.status() == SagaStatus.SUCCEEDED));
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
    }

    @Test void reclaimCannotInterleaveWithCompensation() throws Exception {
        UUID id = pending();
        transactions.executeWithoutResult(ignored -> {
            SagaExecution saga = reload(id);
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.getSteps().get(0).setStatus(SagaStepStatus.POTENTIALLY_APPLIED);
            saga.getSteps().get(0).setExecutionAttempts(1);
        });
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger inCompensation = new AtomicInteger();
        AtomicInteger peakConcurrency = new AtomicInteger();
        List<SagaStatusResponse> firstWorkerEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(productionSteps.createCivilization()).thenReturn(step(0, null, ctx -> {
            int active = inCompensation.incrementAndGet();
            peakConcurrency.accumulateAndGet(active, Math::max);
            entered.countDown();
            if (active == 1 && release.getCount() > 0) await(release);
            inCompensation.decrementAndGet();
        }));
        worker = new FoundCivilizationSagaWorker(executions, transactions, productionSteps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> firstWorkerEvents.add(snapshot), clock);
        FoundCivilizationSagaWorker recovering = new FoundCivilizationSagaWorker(executions, transactions,
            productionSteps, (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {}, clock);
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(worker::processNextDue);
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(recovering::processNextDue);
        try {
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));
            assertEquals(1, peakConcurrency.get());
        } finally { release.countDown(); }
        assertTrue(first.get(10, TimeUnit.SECONDS));
        assertTrue(second.get(10, TimeUnit.SECONDS));
        assertEquals(1, peakConcurrency.get());
        assertTrue(firstWorkerEvents.stream().noneMatch(event -> event.status() == SagaStatus.COMPENSATED));
        assertEquals(SagaStatus.COMPENSATED, reload(id).getStatus());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
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
        private volatile Instant now = Instant.parse("2026-10-23T12:00:00Z");
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }
}
