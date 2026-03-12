package io.github.opencivilizationplatform.modules.logistics.infrastructure;

import io.github.opencivilizationplatform.modules.logistics.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
}
