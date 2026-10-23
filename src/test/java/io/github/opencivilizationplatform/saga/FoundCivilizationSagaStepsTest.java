package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.core.eventbus.EventBus;
import io.github.opencivilizationplatform.modules.civilization.application.CivilizationService;
import io.github.opencivilizationplatform.modules.civilization.domain.Civilization;
import io.github.opencivilizationplatform.modules.civilization.domain.CivilizationStatus;
import io.github.opencivilizationplatform.modules.civilization.infrastructure.CivilizationRepository;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.CitizenRepository;
import io.github.opencivilizationplatform.modules.contribution.infrastructure.CitizenWalletRepository;
import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusNode;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusNodeStatus;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusNodeType;
import io.github.opencivilizationplatform.modules.nexus.infrastructure.NexusConnectionRepository;
import io.github.opencivilizationplatform.modules.nexus.infrastructure.NexusMessageRepository;
import io.github.opencivilizationplatform.modules.nexus.infrastructure.NexusNodeRepository;
import io.github.opencivilizationplatform.modules.participation.infrastructure.RuleRepository;
import io.github.opencivilizationplatform.modules.region.application.ResourceRegionService;
import io.github.opencivilizationplatform.modules.region.domain.ResourceRegion;
import io.github.opencivilizationplatform.modules.region.infrastructure.ResourceRegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoundCivilizationSagaStepsTest {
    private final UUID sagaId = UUID.randomUUID();
    private final List<Civilization> civilizations = new ArrayList<>();
    private final List<NexusNode> nodes = new ArrayList<>();
    private CivilizationRepository civilizationsRepo;
    private NexusNodeRepository nodesRepo;
    private SagaExecutionRepository sagaRepo;
    private final ReentrantLock sagaLock = new ReentrantLock();
    private CivilizationService civilizationService;
    private ResourceRegionService regionService;
    private NexusMeshService nexusService;
    private FoundCivilizationSagaSteps steps;
    private ResourceRegion region;

    @BeforeEach
    void setUp() {
        sagaRepo = mock(SagaExecutionRepository.class);
        when(sagaRepo.lockForEffect(sagaId)).thenAnswer(call -> {
            sagaLock.lock();
            return Optional.of(sagaId);
        });
        civilizationsRepo = mock(CivilizationRepository.class);
        when(civilizationsRepo.findBySagaId(any())).thenAnswer(call -> civilizations.stream()
            .filter(c -> call.<UUID>getArgument(0).equals(c.getSagaId())).findFirst());
        when(civilizationsRepo.findById(anyLong())).thenAnswer(call -> civilizations.stream()
            .filter(c -> call.<Long>getArgument(0).equals(c.getId())).findFirst());
        when(civilizationsRepo.save(any())).thenAnswer(call -> {
            Civilization civ = call.getArgument(0);
            synchronized (civilizations) {
                if (civ.getId() == null) {
                    if (civilizations.stream().anyMatch(existing -> sagaId.equals(existing.getSagaId())))
                        throw new org.springframework.dao.DataIntegrityViolationException("duplicate saga_id");
                    civ.setId((long) civilizations.size() + 1);
                    civilizations.add(civ);
                }
            }
            if (sagaLock.isHeldByCurrentThread()) sagaLock.unlock();
            return civ;
        });
        civilizationService = spy(new CivilizationService(civilizationsRepo, mock(EventBus.class),
            mock(RuleRepository.class), mock(CitizenRepository.class), mock(CitizenWalletRepository.class), sagaRepo));
        doAnswer(call -> civilizations.stream().filter(c -> c.getId().equals(call.<Long>getArgument(0))).findFirst().orElseThrow())
            .when(civilizationService).joinAsAgent(anyLong(), anyString());

        region = new ResourceRegion();
        region.setId(7L);
        region.setName("Valley");
        region.setClaimed(false);
        ResourceRegionRepository regionsRepo = mock(ResourceRegionRepository.class);
        when(regionsRepo.findById(7L)).thenAnswer(call -> Optional.of(region));
        when(regionsRepo.findByIdForUpdate(7L)).thenAnswer(call -> Optional.of(region));
        when(regionsRepo.save(any())).thenAnswer(call -> call.getArgument(0));
        regionService = new ResourceRegionService(regionsRepo);

        nodesRepo = mock(NexusNodeRepository.class);
        when(nodesRepo.findBySagaId(any())).thenAnswer(call -> nodes.stream()
            .filter(n -> call.<UUID>getArgument(0).equals(n.getSagaId())).findFirst());
        when(nodesRepo.findById(anyLong())).thenAnswer(call -> nodes.stream()
            .filter(n -> call.<Long>getArgument(0).equals(n.getId())).findFirst());
        when(nodesRepo.findByCivilizationId(anyLong())).thenAnswer(call -> nodes.stream()
            .filter(n -> call.<Long>getArgument(0).equals(n.getCivilization().getId())).toList());
        when(nodesRepo.findAll()).thenAnswer(call -> new ArrayList<>(nodes));
        when(nodesRepo.save(any())).thenAnswer(call -> {
            NexusNode node = call.getArgument(0);
            synchronized (nodes) {
                if (node.getId() == null) {
                    if (nodes.stream().anyMatch(existing -> sagaId.equals(existing.getSagaId())))
                        throw new org.springframework.dao.DataIntegrityViolationException("duplicate saga_id");
                    node.setId((long) nodes.size() + 1);
                    nodes.add(node);
                }
            }
            if (sagaLock.isHeldByCurrentThread()) sagaLock.unlock();
            return node;
        });
        nexusService = new NexusMeshService(nodesRepo, mock(NexusMessageRepository.class),
            mock(NexusConnectionRepository.class), mock(EventBus.class), mock(StringRedisTemplate.class), mock(ObjectMapper.class), sagaRepo);
        steps = new FoundCivilizationSagaSteps(civilizationService, regionService, nexusService);
    }

    private FoundCivilizationContext context() {
        FoundCivilizationContext ctx = new FoundCivilizationContext();
        ctx.setSagaId(sagaId);
        ctx.setName("Aurora");
        ctx.setScale(CivilizationScale.LOCAL);
        ctx.setRegionId(7L);
        ctx.setOwnerToken("owner");
        return ctx;
    }

    @Test
    void repeatedCreateReturnsSameCivilization() {
        FoundCivilizationContext first = context();
        steps.createCivilization().execute(first);
        FoundCivilizationContext replay = context();
        steps.createCivilization().execute(replay);
        assertEquals(first.getCivilization().getId(), replay.getCivilization().getId());
        assertEquals(1, civilizations.size());
    }

    @Test
    void concurrentCreateReturnsSameCivilizationAfterUniqueRace() {
        CountDownLatch secondLookup = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
        when(civilizationsRepo.findBySagaId(sagaId)).thenAnswer(call -> {
            Optional<Civilization> seen = civilizations.stream().findFirst();
            if (lookups.incrementAndGet() == 1) secondLookup.await(200, TimeUnit.MILLISECONDS);
            else secondLookup.countDown();
            return seen;
        });
        CompletableFuture<Civilization> first = CompletableFuture.supplyAsync(() ->
            civilizationService.createCivilization(sagaId, "Aurora", CivilizationScale.LOCAL, "Valley", "owner"));
        CompletableFuture<Civilization> second = CompletableFuture.supplyAsync(() ->
            civilizationService.createCivilization(sagaId, "Aurora", CivilizationScale.LOCAL, "Valley", "owner"));
        assertEquals(first.join().getId(), second.join().getId());
        assertEquals(1, civilizations.size());
    }

    @Test
    void repeatedClaimKeepsSameOwner() {
        FoundCivilizationContext ctx = context();
        steps.createCivilization().execute(ctx);
        steps.claimRegion().execute(ctx);
        steps.claimRegion().execute(ctx);
        assertEquals(ctx.getCivilization().getId(), region.getClaimedByCivilizationId());
        assertTrue(region.getClaimed());
    }

    @Test
    void unclaimDoesNotClearDifferentCivilizationClaim() {
        FoundCivilizationContext ctx = context();
        steps.createCivilization().execute(ctx);
        steps.claimRegion().execute(ctx);
        region.setClaimedByCivilizationId(999L);
        steps.claimRegion().compensate(ctx);
        assertEquals(999L, region.getClaimedByCivilizationId());
        assertTrue(region.getClaimed());
    }

    @Test
    void repeatedNodeDeploymentReturnsSameNode() {
        FoundCivilizationContext ctx = context();
        steps.createCivilization().execute(ctx);
        steps.deployNexusNode().execute(ctx);
        steps.deployNexusNode().execute(ctx);
        assertEquals(1, nodes.size());
        assertEquals(sagaId, nodes.getFirst().getSagaId());
    }

    @Test
    void concurrentNodeDeploymentReturnsSameNodeAfterUniqueRace() {
        CountDownLatch secondLookup = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
        when(nodesRepo.findBySagaId(sagaId)).thenAnswer(call -> {
            Optional<NexusNode> seen = nodes.stream().findFirst();
            if (lookups.incrementAndGet() == 1) secondLookup.await(200, TimeUnit.MILLISECONDS);
            else secondLookup.countDown();
            return seen;
        });
        CompletableFuture<NexusNode> first = CompletableFuture.supplyAsync(() ->
            nexusService.deployNexusNode(sagaId, "Aurora-Primary", NexusNodeType.PRIMARY, "Valley", 1L, "knowledge"));
        CompletableFuture<NexusNode> second = CompletableFuture.supplyAsync(() ->
            nexusService.deployNexusNode(sagaId, "Aurora-Primary", NexusNodeType.PRIMARY, "Valley", 1L, "knowledge"));
        assertEquals(first.join().getId(), second.join().getId());
        assertEquals(1, nodes.size());
    }

    @Test
    void compensationIsSafeWhenEffectIsAbsent() {
        FoundCivilizationContext ctx = context();
        steps.deployNexusNode().compensate(ctx);
        steps.claimRegion().compensate(ctx);
        steps.createCivilization().compensate(ctx);
        assertFalse(region.getClaimed());
        assertTrue(civilizations.isEmpty());
        assertTrue(nodes.isEmpty());
    }

    @Test
    void failedPartialEffectIsFindableForCompensation() {
        FoundCivilizationContext ctx = context();
        steps.createCivilization().execute(ctx);
        NexusMeshService failing = mock(NexusMeshService.class);
        when(failing.deployNexusNode(eq(sagaId), anyString(), eq(NexusNodeType.PRIMARY), anyString(),
            anyLong(), anyString())).thenAnswer(call -> {
                nexusService.deployNexusNode(sagaId, call.getArgument(1), call.getArgument(2),
                    call.getArgument(3), call.getArgument(4), call.getArgument(5));
                throw new IllegalStateException("response lost after durable effect");
            });
        doAnswer(call -> {
            nexusService.compensateNodeDeployment(sagaId);
            return null;
        }).when(failing).compensateNodeDeployment(sagaId);
        FoundCivilizationSagaSteps failingSteps = new FoundCivilizationSagaSteps(civilizationService, regionService, failing);
        assertThrows(IllegalStateException.class, () -> failingSteps.deployNexusNode().execute(ctx));
        assertEquals(1, nodes.size());
        failingSteps.deployNexusNode().compensate(ctx);
        assertEquals(NexusNodeStatus.OFFLINE, nodes.getFirst().getStatus());
        assertEquals(nodes.getFirst().getId(), nexusService.findNodeBySagaId(sagaId).orElseThrow().getId());
    }
}
