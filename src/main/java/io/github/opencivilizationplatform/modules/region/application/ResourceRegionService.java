package io.github.opencivilizationplatform.modules.region.application;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.modules.region.domain.ResourceRegion;
import io.github.opencivilizationplatform.modules.region.infrastructure.ResourceRegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ResourceRegionService {

    private final ResourceRegionRepository repository;

    public ResourceRegionService(ResourceRegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ResourceRegion> getAllRegions() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ResourceRegion> getAvailableRegions() {
        return repository.findByClaimedFalse();
    }

    @Transactional(readOnly = true)
    public List<ResourceRegion> getRegionsByScale(CivilizationScale scale) {
        return repository.findByScale(scale);
    }

    @Transactional(readOnly = true)
    public ResourceRegion getRegion(Long id) {
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public ResourceRegion claimRegion(Long regionId, Long civilizationId) {
        ResourceRegion region = repository.findById(regionId).orElseThrow();
        region.setClaimed(true);
        region.setClaimedByCivilizationId(civilizationId);
        return repository.save(region);
    }

    @Transactional
    public ResourceRegion unclaimRegion(Long regionId) {
        ResourceRegion region = repository.findById(regionId).orElseThrow();
        region.setClaimed(false);
        region.setClaimedByCivilizationId(null);
        return repository.save(region);
    }

    @Transactional
    public ResourceRegion claimRegion(UUID sagaId, Long regionId, Long civilizationId) {
        if (sagaId == null || civilizationId == null) throw new IllegalArgumentException("Saga and civilization are required");
        ResourceRegion region = repository.findByIdForUpdate(regionId).orElseThrow();
        if (Boolean.TRUE.equals(region.getClaimed())) {
            if (civilizationId.equals(region.getClaimedByCivilizationId())) return region;
            throw new IllegalStateException("Region is already claimed by another civilization");
        }
        region.setClaimed(true);
        region.setClaimedByCivilizationId(civilizationId);
        return repository.save(region);
    }

    @Transactional
    public ResourceRegion compensateClaim(UUID sagaId, Long regionId, Long civilizationId) {
        if (sagaId == null) throw new IllegalArgumentException("sagaId is required");
        ResourceRegion region = repository.findByIdForUpdate(regionId).orElseThrow();
        if (civilizationId != null && civilizationId.equals(region.getClaimedByCivilizationId())) {
            region.setClaimed(false);
            region.setClaimedByCivilizationId(null);
            return repository.save(region);
        }
        return region;
    }

    @Transactional(readOnly = true)
    public long countAvailable() {
        return repository.countByClaimed(false);
    }

    @Transactional(readOnly = true)
    public long countTotal() {
        return repository.count();
    }
}
