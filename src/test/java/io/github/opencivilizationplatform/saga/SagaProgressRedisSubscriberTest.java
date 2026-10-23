package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.RedisEventConfig;
import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.web.handler.NexusWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "saga.found-civilization.initial-delay-ms=3600000")
@ActiveProfiles("test")
class SagaProgressRedisSubscriberTest {
    @Autowired SagaExecutionRepository executions;
    @Autowired TransactionTemplate transactions;
    @Autowired ObjectMapper mapper;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean FoundCivilizationSagaSteps steps;
    @MockitoBean(name = "container") RedisMessageListenerContainer listener;
    private RedisSagaProgressPublisher publisher;
    private final List<String> published = new ArrayList<>();

    @BeforeEach void setup() {
        executions.deleteAll();
        published.clear();
        publisher = new RedisSagaProgressPublisher(redis, mapper);
        when(redis.convertAndSend(eq(RedisEventConfig.SAGA_PROGRESS_CHANNEL_NAME), anyString()))
            .thenAnswer(invocation -> { published.add(invocation.getArgument(1)); return 1L; });
    }

    // Catches publishing inside the database transaction, or mapping stale sequence/status.
    @Test void publishesTypedEventAfterTransitionCommit() {
        UUID id = pending();
        when(redis.convertAndSend(eq(RedisEventConfig.SAGA_PROGRESS_CHANNEL_NAME), anyString()))
            .thenAnswer(invocation -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                String json = invocation.getArgument(1);
                JsonNode payload = mapper.readTree(json).path("event").path("payload");
                SagaExecution committed = executions.findById(id).orElseThrow();
                assertEquals(committed.getSequence(), payload.path("sequence").asLong());
                assertEquals(committed.getStatus().name(), payload.path("status").asText());
                published.add(json);
                return 1L;
            });
        installSuccessfulSteps();
        assertTrue(worker().processNextDue());
        assertFalse(published.isEmpty());
        assertEquals(SagaStatus.SUCCEEDED, executions.findById(id).orElseThrow().getStatus());
        JsonNode routed = mapper.readTree(published.getLast());
        assertEquals("owner", routed.path("ownerId").asText());
        assertEquals("SAGA_PROGRESS", routed.path("event").path("type").asText());
        assertEquals(id.toString(), routed.path("event").path("payload").path("sagaId").asText());
    }

    @Test void parsesSagaEventsSeparatelyFromNexusMessageDTO() throws Exception {
        NexusMeshService mesh = mock(NexusMeshService.class);
        NexusWebSocketHandler handler = new NexusWebSocketHandler(mesh, mapper);
        WebSocketSession owner = connect(handler, "local-owner", "owner");
        WebSocketSession foreign = connect(handler, "local-foreign", "other");
        var subscriber = new SagaProgressRedisSubscriber(handler, mapper);
        SagaFailureView failure = new SagaFailureView("DOMAIN", "Region unavailable");
        SagaCompensationView compensation = new SagaCompensationView(SagaStepStatus.COMPENSATED, 1, null);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000012");
        Instant now = Instant.parse("2026-10-23T12:00:00Z");
        var snapshot = new SagaStatusResponse(id, SagaStatus.COMPENSATING, "CLAIM_REGION", "CLAIM_REGION",
            7L, "New World", failure, List.of(
                new SagaStepView("CREATE_CIVILIZATION", SagaStepStatus.SUCCEEDED, 1, null, null, null),
                new SagaStepView("CLAIM_REGION", SagaStepStatus.FAILED, 1, null, failure, compensation),
                new SagaStepView("DEPLOY_NEXUS_NODE", SagaStepStatus.PENDING, 0, null, null, null)),
            12L, now, now);
        publisher.publish("owner", id, snapshot, "CLAIM_REGION", failure, compensation);
        assertEquals(1, published.size());
        subscriber.receiveMessage(published.getFirst());

        var sent = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(owner).sendMessage(sent.capture());
        JsonNode wire = mapper.readTree(sent.getValue().getPayload());
        assertEquals(2, wire.size());
        assertEquals("SAGA_PROGRESS", wire.path("type").asText());
        assertFalse(wire.has("ownerId"));
        JsonNode payload = wire.path("payload");
        assertEquals(8, payload.size());
        assertEquals(id.toString(), payload.path("sagaId").asText());
        assertEquals(12, payload.path("sequence").asLong());
        assertEquals("COMPENSATING", payload.path("status").asText());
        assertEquals("CLAIM_REGION", payload.path("currentStep").asText());
        assertEquals("DOMAIN", payload.path("failure").path("code").asText());
        assertEquals("COMPENSATED", payload.path("compensation").path("status").asText());
        assertEquals(now.toString(), payload.path("updatedAt").asText());
        assertEquals("Civilization created", payload.path("steps").get(0).path("label").asText());
        assertEquals("Region claimed", payload.path("steps").get(1).path("label").asText());
        assertEquals("Nexus node deployed", payload.path("steps").get(2).path("label").asText());
        assertEquals("FAILED", payload.path("steps").get(1).path("status").asText());
        assertEquals("COMPENSATED", payload.path("steps").get(1).path("compensation").path("status").asText());
        verify(foreign, never()).sendMessage(any());
        verifyNoInteractions(mesh);
    }

    @Test void redisFailureDoesNotChangeCommittedWorkerOutcome() {
        UUID id = pending();
        installSuccessfulSteps();
        when(redis.convertAndSend(anyString(), anyString()))
            .thenThrow(new RedisConnectionFailureException("Redis offline"));
        assertTrue(worker().processNextDue());
        SagaExecution saga = executions.findById(id).orElseThrow();
        assertEquals(SagaStatus.SUCCEEDED, saga.getStatus());
        assertTrue(saga.getSteps().stream().allMatch(step -> step.getStatus() == SagaStepStatus.SUCCEEDED));
    }

    @Test void malformedOrUnroutableEventsDoNotReachSockets() throws Exception {
        NexusWebSocketHandler handler = new NexusWebSocketHandler(mock(NexusMeshService.class), mapper);
        WebSocketSession owner = connect(handler, "owner", "owner");
        var subscriber = new SagaProgressRedisSubscriber(handler, mapper);
        for (String message : List.of("{", "null", "{}",
            "{\"ownerId\":\"owner\",\"event\":{\"type\":\"Nexus-message\",\"payload\":{}}}",
            "{\"ownerId\":\"owner\",\"event\":{\"type\":\"SAGA_PROGRESS\",\"payload\":null}}")) {
            assertDoesNotThrow(() -> subscriber.receiveMessage(message));
        }
        verify(owner, never()).sendMessage(any());
    }

    private UUID pending() {
        return executions.saveAndFlush(new SagaExecution("owner", UUID.randomUUID().toString(), "fingerprint",
            "New World", CivilizationScale.LOCAL, 7L)).getId();
    }

    private FoundCivilizationSagaWorker worker() {
        return new FoundCivilizationSagaWorker(executions, transactions, steps, publisher, Clock.systemUTC());
    }

    private void installSuccessfulSteps() {
        SagaStep<FoundCivilizationContext> noEffect = new SagaStep<>() {
            public void execute(FoundCivilizationContext context) {}
            public void compensate(FoundCivilizationContext context) {}
            public String getName() { return "test-effect"; }
        };
        when(steps.createCivilization()).thenReturn(noEffect);
        when(steps.claimRegion()).thenReturn(noEffect);
        when(steps.deployNexusNode()).thenReturn(noEffect);
    }

    private WebSocketSession connect(NexusWebSocketHandler handler, String id, String clientId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(Map.of("X-Client-Id", clientId));
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }
}
