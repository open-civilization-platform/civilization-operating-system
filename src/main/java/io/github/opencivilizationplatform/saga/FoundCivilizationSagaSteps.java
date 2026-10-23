package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.modules.civilization.application.CivilizationService;
import io.github.opencivilizationplatform.modules.civilization.domain.Civilization;
import io.github.opencivilizationplatform.modules.region.application.ResourceRegionService;
import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FoundCivilizationSagaSteps {

    private static final Logger log = LoggerFactory.getLogger(FoundCivilizationSagaSteps.class);
    private final CivilizationService civilizationService;
    private final ResourceRegionService regionService;
    private final NexusMeshService nexusService;

    public FoundCivilizationSagaSteps(CivilizationService civilizationService,
                                       ResourceRegionService regionService,
                                       NexusMeshService nexusService) {
        this.civilizationService = civilizationService;
        this.regionService = regionService;
        this.nexusService = nexusService;
    }

    public SagaStep<FoundCivilizationContext> createCivilization() {
        return new SagaStep<>() {
            @Override
            public void execute(FoundCivilizationContext ctx) {
                Civilization civ = civilizationService.createCivilization(
                    ctx.getSagaId(), ctx.getName(), ctx.getScale(),
                    regionService.getRegion(ctx.getRegionId()).getName(),
                    ctx.getOwnerToken()
                );
                ctx.setCivilization(civ);
                log.info("SAGA step: civilization {} created (id={})", civ.getName(), civ.getId());
            }

            @Override
            public void compensate(FoundCivilizationContext ctx) {
                civilizationService.findBySagaId(ctx.getSagaId()).ifPresent(ctx::setCivilization);
                civilizationService.compensateCreation(ctx.getSagaId());
                log.warn("SAGA compensate: civilization for saga {} marked as FALLEN if present", ctx.getSagaId());
            }

            @Override
            public String getName() { return "CreateCivilization"; }
        };
    }

    public SagaStep<FoundCivilizationContext> claimRegion() {
        return new SagaStep<>() {
            @Override
            public void execute(FoundCivilizationContext ctx) {
                Civilization civ = resolveCivilization(ctx);
                regionService.claimRegion(ctx.getSagaId(), ctx.getRegionId(), civ.getId());
                ctx.setRegionClaimed(true);
                log.info("SAGA step: region {} claimed for civilization {}", ctx.getRegionId(), ctx.getCivilization().getId());
            }

            @Override
            public void compensate(FoundCivilizationContext ctx) {
                Long civilizationId = resolveCivilizationId(ctx);
                regionService.compensateClaim(ctx.getSagaId(), ctx.getRegionId(), civilizationId);
                log.warn("SAGA compensate: region {} unclaimed if owned by saga civilization", ctx.getRegionId());
            }

            @Override
            public String getName() { return "ClaimRegion"; }
        };
    }

    public SagaStep<FoundCivilizationContext> deployNexusNode() {
        return new SagaStep<>() {
            @Override
            public void execute(FoundCivilizationContext ctx) {
                String regionName = regionService.getRegion(ctx.getRegionId()).getName();
                Civilization civ = resolveCivilization(ctx);
                nexusService.deployNexusNode(
                    ctx.getSagaId(), civ.getName() + "-Primary",
                    NexusNodeType.PRIMARY,
                    regionName,
                    civ.getId(),
                    "Primary neural node for " + civ.getName()
                );
                ctx.setNexusNodeDeployed(true);
                log.info("SAGA step: nexus node deployed for civilization {}", ctx.getCivilization().getId());
            }

            @Override
            public void compensate(FoundCivilizationContext ctx) {
                nexusService.compensateNodeDeployment(ctx.getSagaId());
                log.warn("SAGA compensate: saga node set to OFFLINE if present for {}", ctx.getSagaId());
            }

            @Override
            public String getName() { return "DeployNexusNode"; }
        };
    }

    private Civilization resolveCivilization(FoundCivilizationContext ctx) {
        if (ctx.getCivilization() != null) return ctx.getCivilization();
        Civilization civ = civilizationService.findBySagaId(ctx.getSagaId()).orElseThrow();
        ctx.setCivilization(civ);
        return civ;
    }

    private Long resolveCivilizationId(FoundCivilizationContext ctx) {
        if (ctx.getCivilizationId() != null) return ctx.getCivilizationId();
        return civilizationService.findBySagaId(ctx.getSagaId()).map(civ -> {
            ctx.setCivilization(civ);
            return civ.getId();
        }).orElse(null);
    }
}
