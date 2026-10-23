package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "saga.found-civilization.initial-delay-ms=3600000")
@ActiveProfiles("test")
class FoundCivilizationSagaServiceTest {
    @Autowired FoundCivilizationSagaService service;
    @Autowired SagaExecutionRepository executions;
    @MockitoBean(name = "container") RedisMessageListenerContainer redisListener;
    @MockitoBean org.springframework.scheduling.TaskScheduler taskScheduler;

    @BeforeEach void clear() { executions.deleteAll(); }

    @Test void submitReturnsExistingSagaForSameKeyAndPayload() {
        var first = service.submit(new FoundCivilizationRequest("  New World  ", null, 7L), "owner-a", "key-a");
        var second = service.submit(new FoundCivilizationRequest("New World", CivilizationScale.LOCAL, 7L), "owner-a", "key-a");
        assertEquals(first.sagaId(), second.sagaId());
        assertEquals("New World", first.name());
        assertEquals("New World", second.name());
        assertEquals(1, executions.count());
    }

    @Test void submitRejectsSameKeyWithDifferentPayload() {
        service.submit(new FoundCivilizationRequest("New World", CivilizationScale.LOCAL, 7L), "owner-a", "key-a");
        assertThrows(SagaIdempotencyConflictException.class, () -> service.submit(
            new FoundCivilizationRequest("Different", CivilizationScale.LOCAL, 7L), "owner-a", "key-a"));
        assertEquals(1, executions.count());
    }

    @Test void newSubmissionPersistsPendingStepsBeforeReturning() {
        var response = service.submit(new FoundCivilizationRequest("New World", CivilizationScale.LOCAL, 7L), "owner-a", "key-a");
        var saved = executions.findById(response.sagaId()).orElseThrow();
        assertEquals(SagaStatus.PENDING, saved.getStatus());
        assertEquals(3, saved.getSteps().size());
        assertTrue(saved.getSteps().stream().allMatch(step -> step.getStatus() == SagaStepStatus.PENDING));
        assertEquals(3, response.steps().size());
    }

    @Test void statusIsOwnerScoped() {
        UUID id = service.submit(new FoundCivilizationRequest("New World", CivilizationScale.LOCAL, 7L), "owner-a", "key-a").sagaId();
        assertEquals(id, service.getStatus(id, "owner-a").sagaId());
        assertEquals("New World", service.getStatus(id, "owner-a").name());
        assertThrows(SagaException.class, () -> service.getStatus(id, "owner-b"));
        assertThrows(SagaException.class, () -> service.getStatus(UUID.randomUUID(), "owner-a"));
    }
}
