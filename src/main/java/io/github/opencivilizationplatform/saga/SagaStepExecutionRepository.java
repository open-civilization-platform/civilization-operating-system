package io.github.opencivilizationplatform.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SagaStepExecutionRepository extends JpaRepository<SagaStepExecution, UUID> {
    List<SagaStepExecution> findBySagaIdOrderByStepOrder(UUID sagaId);
}
