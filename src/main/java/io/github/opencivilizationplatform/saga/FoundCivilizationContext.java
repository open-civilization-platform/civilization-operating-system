package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.modules.civilization.domain.Civilization;
import java.util.UUID;

public class FoundCivilizationContext {
    private UUID sagaId;
    private String name;
    private CivilizationScale scale;
    private Long regionId;
    private String ownerToken;
    private Civilization civilization;
    private Long civilizationId;
    private boolean regionClaimed;
    private boolean nexusNodeDeployed;

    public String getName() { return name; }
    public UUID getSagaId() { return sagaId; }
    public void setSagaId(UUID sagaId) { this.sagaId = sagaId; }
    public void setName(String name) { this.name = name; }
    public CivilizationScale getScale() { return scale; }
    public void setScale(CivilizationScale scale) { this.scale = scale; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    public String getOwnerToken() { return ownerToken; }
    public void setOwnerToken(String ownerToken) { this.ownerToken = ownerToken; }
    public Civilization getCivilization() { return civilization; }
    public void setCivilization(Civilization civilization) {
        this.civilization = civilization;
        if (civilization != null) this.civilizationId = civilization.getId();
    }
    public Long getCivilizationId() { return civilizationId; }
    public void setCivilizationId(Long civilizationId) { this.civilizationId = civilizationId; }
    public boolean isRegionClaimed() { return regionClaimed; }
    public void setRegionClaimed(boolean regionClaimed) { this.regionClaimed = regionClaimed; }
    public boolean isNexusNodeDeployed() { return nexusNodeDeployed; }
    public void setNexusNodeDeployed(boolean nexusNodeDeployed) { this.nexusNodeDeployed = nexusNodeDeployed; }
}
