package io.github.opencivilizationplatform.modules.social.infrastructure;

import io.github.opencivilizationplatform.modules.social.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
}
