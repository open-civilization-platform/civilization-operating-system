package io.github.opencivilizationplatform.modules.region.infrastructure;
import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import io.github.opencivilizationplatform.modules.region.domain.ResourceRegion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRegionRepository extends JpaRepository<ResourceRegion, Long> {
    List<ResourceRegion> findByScale(CivilizationScale scale);
    List<ResourceRegion> findByClaimedFalse();
    long countByClaimed(boolean claimed);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResourceRegion r where r.id = :id")
    Optional<ResourceRegion> findByIdForUpdate(Long id);
}
