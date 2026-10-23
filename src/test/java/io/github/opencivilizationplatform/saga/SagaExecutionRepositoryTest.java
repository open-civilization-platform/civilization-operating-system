package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SagaExecutionRepositoryTest {
    @Autowired SagaExecutionRepository executions;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @MockitoBean(name = "container") RedisMessageListenerContainer redisListener;

    @BeforeEach
    void clearExecutions() { executions.deleteAll(); }

    private SagaExecution newExecution(String owner, String key) {
        return new SagaExecution(owner, key, "fingerprint", "New World", CivilizationScale.LOCAL, 7L);
    }

    @Test
    void savesThreeStepsInDeclaredOrder() {
        SagaExecution reloaded = transactions.execute(ignored -> {
            UUID id = executions.saveAndFlush(newExecution("owner-a", "key-a")).getId();
            entityManager.clear();
            return executions.findById(id).orElseThrow();
        });
        assertNotNull(reloaded);
        assertNotNull(reloaded.getId());
        assertEquals(SagaStatus.PENDING, reloaded.getStatus());
        assertEquals(0L, reloaded.getSequence());
        assertEquals(3, reloaded.getSteps().size());
        assertEquals(List.of("CREATE_CIVILIZATION", "CLAIM_REGION", "DEPLOY_NEXUS_NODE"),
            reloaded.getSteps().stream().map(SagaStepExecution::getStepKey).toList());
        assertTrue(reloaded.getSteps().stream().allMatch(step -> step.getStatus() == SagaStepStatus.PENDING));
    }

    @Test
    void enforcesOwnerAndIdempotencyKeyUniqueness() {
        executions.saveAndFlush(newExecution("owner-a", "key-a"));
        assertThrows(DataIntegrityViolationException.class,
            () -> executions.saveAndFlush(newExecution("owner-a", "key-a")));
    }

    @Test
    void findsStatusOnlyForMatchingOwner() {
        SagaExecution saved = executions.saveAndFlush(newExecution("owner-a", "key-a"));
        UUID id = saved.getId();
        assertEquals(SagaStatus.PENDING, executions.findByIdAndOwnerId(id, "owner-a").orElseThrow().getStatus());
        assertTrue(executions.findByIdAndOwnerId(id, "owner-b").isEmpty());
        assertEquals(id, executions.findByOwnerIdAndIdempotencyKey("owner-a", "key-a").orElseThrow().getId());
        assertTrue(executions.findByOwnerIdAndIdempotencyKey("owner-b", "key-a").isEmpty());
    }

    @Test
    void claimsDueExecutionOnlyOnce() throws Exception {
        SagaExecution saved = executions.saveAndFlush(newExecution("owner-a", "key-a"));
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        CountDownLatch start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> {
            await(start);
            return executions.claimNextDue(now, Duration.ofMinutes(1));
        });
        var second = CompletableFuture.supplyAsync(() -> {
            await(start);
            return executions.claimNextDue(now, Duration.ofMinutes(1));
        });
        start.countDown();
        assertEquals(1, List.of(first.get(), second.get()).stream().filter(java.util.Optional::isPresent).count());
        assertEquals(now.plusSeconds(60), executions.findById(saved.getId()).orElseThrow().getLeaseUntil());
        assertTrue(executions.claimNextDue(now.plusSeconds(30), Duration.ofMinutes(1)).isEmpty());
        assertTrue(executions.claimNextDue(now.plusSeconds(61), Duration.ofMinutes(1)).isPresent());
    }

    @Test
    void sequenceIsMonotonicUnderConcurrentIncrement() {
        UUID id = executions.saveAndFlush(newExecution("owner-a", "key-a")).getId();
        int increments = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> updates = java.util.stream.IntStream.range(0, increments)
            .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                await(start);
                return executions.incrementSequence(id);
            })).toList();
        start.countDown();
        assertTrue(updates.stream().allMatch(update -> update.join() == 1));
        assertEquals(increments, executions.findById(id).orElseThrow().getSequence());
    }

    @Test
    void claimReadbackReflectsLeaseInsideExistingTransaction() {
        UUID id = executions.saveAndFlush(newExecution("owner-a", "key-a")).getId();
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        transactions.executeWithoutResult(ignored -> {
            SagaExecution cached = executions.findById(id).orElseThrow();
            assertNull(cached.getLeaseUntil());
            SagaExecution claimed = executions.claimNextDue(now, Duration.ofMinutes(1)).orElseThrow();
            assertEquals(now.plusSeconds(60), claimed.getLeaseUntil());
        });
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }
}
