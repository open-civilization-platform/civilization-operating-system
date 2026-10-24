package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.*;
import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.core.eventbus.SpringEventBus;
import io.github.opencivilizationplatform.modules.civilization.application.CivilizationService;
import io.github.opencivilizationplatform.modules.civilization.domain.CivilizationStatus;
import io.github.opencivilizationplatform.modules.civilization.infrastructure.CivilizationRepository;
import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.modules.nexus.infrastructure.NexusNodeRepository;
import io.github.opencivilizationplatform.modules.region.application.ResourceRegionService;
import io.github.opencivilizationplatform.modules.region.domain.ResourceRegion;
import io.github.opencivilizationplatform.modules.region.infrastructure.ResourceRegionRepository;
import io.github.opencivilizationplatform.web.handler.NexusWebSocketHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

// No component scan, scheduler, seed runners, or Redis listener: only the real flow under test.
@SpringJUnitWebConfig(FoundCivilizationSagaPersistenceIntegrationTest.FlowConfiguration.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:saga-flow-isolated;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
class FoundCivilizationSagaPersistenceIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("io.github.opencivilizationplatform")
    @EnableJpaRepositories("io.github.opencivilizationplatform")
    @Import({FoundCivilizationSagaService.class, FoundCivilizationSagaSteps.class,
        CivilizationService.class, ResourceRegionService.class, NexusMeshService.class,
        SpringEventBus.class, SagaController.class, SecurityConfig.class, JwtAuthFilter.class,
        JwtService.class, GlobalExceptionHandler.class})
    static class FlowConfiguration {}

    @MockitoSpyBean SagaExecutionRepository executions;
    @Autowired CivilizationRepository civilizations;
    @Autowired ResourceRegionRepository regions;
    @Autowired NexusNodeRepository nodes;
    @Autowired CivilizationService civilizationService;
    @Autowired ResourceRegionService regionService;
    @Autowired NexusMeshService mesh;
    @Autowired FoundCivilizationSagaSteps steps;
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @MockitoBean StringRedisTemplate redis;
    private MockMvc mvc;
    private ResourceRegion region;
    private MutableClock clock;
    private final List<String> transport = new ArrayList<>();

    @BeforeEach void setup() {
        mvc = webAppContextSetup(context).addFilters(security).build();
        executions.deleteAll();
        region = new ResourceRegion();
        region.setName("Riverlands-" + UUID.randomUUID());
        region.setScale(CivilizationScale.LOCAL);
        region = regions.saveAndFlush(region);
        clock = new MutableClock();
        transport.clear();
        when(redis.convertAndSend(eq(RedisEventConfig.SAGA_PROGRESS_CHANNEL_NAME), anyString()))
            .thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                transport.add(call.getArgument(1));
                return 1L;
            });
    }

    // Removing durable idempotency, authenticated ownership, or any fixed effect breaks this flow.
    @Test void acceptedCommandCompletesWithUniqueEffectsAndOwnerOnlyStatusAndEvents() throws Exception {
        String owner = "alice-" + UUID.randomUUID();
        UUID id = submit(owner, "create", "Arcadia");
        assertEquals(SagaStatus.PENDING, reload(id).getStatus());
        assertEquals(id, submit(owner, "create", "Arcadia"));
        command(owner, "create", "Different").andExpect(status().isConflict());
        assertEquals(1, executions.count());
        assertTrue(worker(steps).processNextDue());
        SagaExecution completed = reload(id);
        assertEquals(SagaStatus.SUCCEEDED, completed.getStatus());
        assertTrue(completed.getSteps().stream().allMatch(s -> s.getStatus() == SagaStepStatus.SUCCEEDED));
        assertUniqueEffects(id, owner);
        assertEquals(id, submit(owner, "create", "Arcadia"));
        assertFalse(worker(steps).processNextDue());
        assertUniqueEffects(id, owner);
        mvc.perform(get("/api/v1/sagas/{id}", id).header("Authorization", bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.civilizationId").value(completed.getCivilizationId()));
        mvc.perform(get("/api/v1/sagas/{id}", id).header("Authorization", bearer("bob")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sagas/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/sagas/found-civilization").header("Idempotency-Key", "anonymous")
            .contentType(MediaType.APPLICATION_JSON).content(body("Anonymous")))
            .andExpect(status().isUnauthorized());

        // Two distinct subscriber/session registries represent delivery on separate app instances.
        List<TextMessage> ownMessages = new ArrayList<>();
        List<TextMessage> foreignMessages = new ArrayList<>();
        var ownerHandler = new NexusWebSocketHandler(mesh, mapper);
        var otherHandler = new NexusWebSocketHandler(mesh, mapper);
        connect(ownerHandler, owner, ownMessages);
        connect(otherHandler, "bob", foreignMessages);
        var ownerSubscriber = new SagaProgressRedisSubscriber(ownerHandler, mapper);
        var otherSubscriber = new SagaProgressRedisSubscriber(otherHandler, mapper);
        for (String event : transport) {
            ownerSubscriber.receiveMessage(event);
            otherSubscriber.receiveMessage(event);
        }
        assertFalse(ownMessages.isEmpty());
        assertTrue(foreignMessages.isEmpty());
        var terminal = mapper.readTree(ownMessages.getLast().getPayload());
        assertEquals("SAGA_PROGRESS", terminal.path("type").asText());
        assertFalse(terminal.has("ownerId"));
        assertEquals(id.toString(), terminal.path("payload").path("sagaId").asText());
        assertEquals("SUCCEEDED", terminal.path("payload").path("status").asText());
        assertEquals(completed.getSequence(), terminal.path("payload").path("sequence").asLong());
    }

    // Replay every possible interrupted fixed effect, retaining its committed rows but not its success marker.
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void reconstructedWorkerRecoversExpiredLeaseWithoutDuplicatingAppliedEffect(int interruptedStep) throws Exception {
        String owner = "recover-" + UUID.randomUUID();
        UUID id = submit(owner, "recover", owner);
        FoundCivilizationContext ctx = effectContext(id, owner);
        List<SagaStep<FoundCivilizationContext>> effects = SagaOrchestrator.foundCivilizationSteps(steps);
        for (int index = 0; index <= interruptedStep; index++) {
            int stepIndex = index;
            transactions.executeWithoutResult(ignored -> {
                SagaExecution row = executions.findById(id).orElseThrow();
                row.setStatus(SagaStatus.RUNNING);
                row.setLeaseUntil(clock.instant().plusSeconds(300));
                row.getSteps().get(stepIndex).setStatus(SagaStepStatus.POTENTIALLY_APPLIED);
                row.getSteps().get(stepIndex).setExecutionAttempts(1);
            });
            transactions.executeWithoutResult(ignored -> effects.get(stepIndex).execute(ctx));
            if (index < interruptedStep) transactions.executeWithoutResult(ignored ->
                executions.findById(id).orElseThrow().getSteps().get(stepIndex).setStatus(SagaStepStatus.SUCCEEDED));
        }
        Long existingCivilization = civilizations.findBySagaId(id).orElseThrow().getId();
        Long existingNode = nodes.findBySagaId(id).map(n -> n.getId()).orElse(null);
        assertEquals(SagaStepStatus.POTENTIALLY_APPLIED, reload(id).getSteps().get(interruptedStep).getStatus());
        assertFalse(worker(steps).processNextDue());
        clock.advance(Duration.ofSeconds(301));
        // New service and worker; no in-memory context from the interrupted execution is retained.
        var restoredService = new FoundCivilizationSagaService(executions, transactions);
        assertEquals(SagaStatus.RUNNING, restoredService.getStatus(id, owner).status());
        assertTrue(worker(new FoundCivilizationSagaSteps(civilizationService, regionService, mesh)).processNextDue());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
        assertEquals(2, reload(id).getSteps().get(interruptedStep).getExecutionAttempts());
        assertEquals(existingCivilization, reload(id).getCivilizationId());
        if (existingNode != null) assertEquals(existingNode, nodes.findBySagaId(id).orElseThrow().getId());
        assertUniqueEffects(id, owner);
        assertEquals(id, restoredService.submit(new FoundCivilizationRequest(owner, CivilizationScale.LOCAL,
            region.getId()), owner, "recover").sagaId());
        assertFalse(worker(steps).processNextDue());
    }

    @Test void reconstructedWorkerPersistsCompensationFailureAndStillReversesEarlierEffects() throws Exception {
        String owner = "cleanup-" + UUID.randomUUID();
        region.setClaimed(true);
        region.setClaimedByCivilizationId(777L);
        regions.saveAndFlush(region);
        UUID id = submit(owner, "cleanup", "Cleanup");
        var publisher = new RedisSagaProgressPublisher(redis, mapper);
        var interrupted = new FoundCivilizationSagaWorker(executions, transactions, steps,
            (ownerId, sagaId, snapshot, currentStep, failure, compensation) -> {
                publisher.publish(ownerId, sagaId, snapshot, currentStep, failure, compensation);
                // Stop the first worker at a committed compensation boundary by expiring its lease.
                if (snapshot.status() == SagaStatus.COMPENSATING) clock.advance(Duration.ofSeconds(301));
            }, clock);
        assertTrue(interrupted.processNextDue());
        assertEquals(SagaStatus.COMPENSATING, reload(id).getStatus());
        assertEquals("CLAIM_REGION", reload(id).getFailedStep());
        assertEquals("DOMAIN", reload(id).getFailureCode());
        assertEquals(0, reload(id).getSteps().get(1).getCompensationAttempts());
        // Deliberate failure at one compensation boundary; all other persisted effects/undo use real services.
        var failingCleanup = new FoundCivilizationSagaSteps(civilizationService, regionService, mesh) {
            @Override public SagaStep<FoundCivilizationContext> claimRegion() {
                return new SagaStep<>() {
                    public void execute(FoundCivilizationContext ctx) { steps.claimRegion().execute(ctx); }
                    public void compensate(FoundCivilizationContext ctx) { throw new IllegalStateException("Region cleanup unavailable"); }
                    public String getName() { return "ClaimRegion"; }
                };
            }
        };
        assertTrue(worker(failingCleanup).processNextDue());
        SagaExecution failed = reload(id);
        assertEquals(SagaStatus.COMPENSATION_FAILED, failed.getStatus());
        assertEquals("Region cleanup unavailable", failed.getSteps().get(1).getCompensationErrorMessage());
        assertEquals(SagaStepStatus.COMPENSATION_FAILED, failed.getSteps().get(1).getCompensationStatus());
        assertEquals(SagaStepStatus.PENDING, failed.getSteps().get(2).getStatus());
        assertEquals(SagaStepStatus.COMPENSATED, failed.getSteps().get(0).getCompensationStatus());
        assertEquals(CivilizationStatus.FALLEN, civilizations.findBySagaId(id).orElseThrow().getStatus());
        assertEquals(777L, regions.findById(region.getId()).orElseThrow().getClaimedByCivilizationId());
        assertTrue(nodes.findBySagaId(id).isEmpty());
        assertFalse(worker(steps).processNextDue());
        mvc.perform(get("/api/v1/sagas/{id}", id).header("Authorization", bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPENSATION_FAILED"))
            .andExpect(jsonPath("$.steps[1].compensation.failure.message").value("Region cleanup unavailable"));
    }

    // Oversized user input must be rejected before a VARCHAR constraint becomes a persistence 500.
    @ParameterizedTest @ValueSource(strings = {"name", "key"})
    void rejectsOversizedSubmissionWithoutPersistingSaga(String field) throws Exception {
        command("alice", field.equals("key") ? "k".repeat(256) : "valid",
            field.equals("name") ? "n".repeat(248) : "Valid").andExpect(status().isBadRequest());
        assertEquals(0, executions.count());
    }

    @Test void malformedRequestsRetainBadRequestStatusWithStarterAdvicePresent() throws Exception {
        mvc.perform(post("/api/v1/sagas/found-civilization").header("Authorization", bearer("alice"))
            .header("Idempotency-Key", "malformed").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sagas/found-civilization").header("Authorization", bearer("alice"))
            .contentType(MediaType.APPLICATION_JSON).content(body("Missing key")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sagas/not-a-uuid").header("Authorization", bearer("alice")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sagas/{id}", UUID.randomUUID()).header("Authorization", bearer("alice")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Saga not found"));
        assertEquals(0, executions.count());
    }

    @Test void acceptsFieldsAtThePersistenceBoundary() throws Exception {
        String maxSafeName = "n".repeat(247);
        UUID id = submit("alice", "k".repeat(255), maxSafeName);
        assertTrue(worker(steps).processNextDue());
        assertEquals(SagaStatus.SUCCEEDED, reload(id).getStatus());
        assertEquals(1, executions.count());
        assertEquals(255, nodes.findBySagaId(id).orElseThrow().getName().length());
        assertEquals(maxSafeName + "-Primary", nodes.findBySagaId(id).orElseThrow().getName());
    }

    @Test void unavailableDatabaseReturnsSafeServerErrorInsteadOfFalseSuccess() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private database connection details"))
            .when(executions).findByOwnerIdAndIdempotencyKey("alice", "unavailable");
        command("alice", "unavailable", "Unavailable").andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database"))))
            .andExpect(jsonPath("$.sagaId").doesNotExist());
        assertEquals(0, executions.count());
    }

    private void assertUniqueEffects(UUID id, String owner) {
        transactions.executeWithoutResult(ignored -> {
            entityManager.clear();
            var civ = civilizations.findBySagaId(id).orElseThrow();
            assertEquals(owner, civ.getOwnerToken());
            assertEquals(101, civ.getPopulation());
            assertEquals(1, civilizations.findAll().stream().filter(c -> id.equals(c.getSagaId())).count());
            assertEquals(1, nodes.findAll().stream().filter(n -> id.equals(n.getSagaId())).count());
            assertEquals(civ.getId(), nodes.findBySagaId(id).orElseThrow().getCivilization().getId());
            var claimed = regions.findById(region.getId()).orElseThrow();
            assertTrue(claimed.getClaimed());
            assertEquals(civ.getId(), claimed.getClaimedByCivilizationId());
        });
    }

    private FoundCivilizationContext effectContext(UUID id, String owner) {
        var ctx = new FoundCivilizationContext();
        ctx.setSagaId(id); ctx.setName(owner); ctx.setScale(CivilizationScale.LOCAL);
        ctx.setRegionId(region.getId()); ctx.setOwnerToken(owner);
        return ctx;
    }
    private SagaExecution reload(UUID id) {
        return transactions.execute(ignored -> { entityManager.clear(); return executions.findById(id).orElseThrow(); });
    }
    private FoundCivilizationSagaWorker worker(FoundCivilizationSagaSteps fixedSteps) {
        return new FoundCivilizationSagaWorker(executions, transactions, fixedSteps,
            new RedisSagaProgressPublisher(redis, mapper), clock);
    }
    private String bearer(String owner) { return "Bearer " + jwt.generateToken(owner); }
    private String body(String name) {
        return mapper.writeValueAsString(new FoundCivilizationRequest(name, CivilizationScale.LOCAL, region.getId()));
    }
    private org.springframework.test.web.servlet.ResultActions command(String owner, String key, String name) throws Exception {
        return mvc.perform(post("/api/v1/sagas/found-civilization").header("Authorization", bearer(owner))
            .header("X-Client-Id", "spoofed").header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body(name)));
    }
    private UUID submit(String owner, String key, String name) throws Exception {
        String json = command(owner, key, name).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(json).path("sagaId").asText());
    }
    private void connect(NexusWebSocketHandler handler, String owner, List<TextMessage> sent) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.getAttributes()).thenReturn(Map.of("X-Client-Id", owner));
        when(session.isOpen()).thenReturn(true);
        doAnswer(call -> { sent.add(call.getArgument(0)); return null; }).when(session).sendMessage(any());
        handler.afterConnectionEstablished(session);
    }
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-23T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }
}
