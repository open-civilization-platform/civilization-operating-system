package io.github.opencivilizationplatform.modules.participation.infrastructure;

import io.github.opencivilizationplatform.modules.participation.domain.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
}
