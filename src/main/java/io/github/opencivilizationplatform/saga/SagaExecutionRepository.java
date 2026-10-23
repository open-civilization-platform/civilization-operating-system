package io.github.opencivilizationplatform.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaExecutionRepository extends JpaRepository<SagaExecution, UUID> {
    Optional<SagaExecution> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);
    Optional<SagaExecution> findByIdAndOwnerId(UUID id, String ownerId);

    @Query("select e.id from SagaExecution e where e.status in "
        + "(io.github.opencivilizationplatform.saga.SagaStatus.PENDING, "
        + "io.github.opencivilizationplatform.saga.SagaStatus.RUNNING, "
        + "io.github.opencivilizationplatform.saga.SagaStatus.COMPENSATING) "
        + "and (e.leaseUntil is null or e.leaseUntil <= :now) "
        + "and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) order by e.createdAt, e.id")
    List<UUID> findDueIds(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update SagaExecution e set e.leaseUntil = :leaseUntil, e.heartbeatAt = :now, e.version = e.version + 1 "
        + "where e.id = :id and e.status in "
        + "(io.github.opencivilizationplatform.saga.SagaStatus.PENDING, "
        + "io.github.opencivilizationplatform.saga.SagaStatus.RUNNING, "
        + "io.github.opencivilizationplatform.saga.SagaStatus.COMPENSATING) "
        + "and (e.leaseUntil is null or e.leaseUntil <= :now) "
        + "and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)")
    int claimIfDue(@Param("id") UUID id, @Param("now") Instant now,
                   @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update SagaExecution e set e.sequence = e.sequence + 1, e.version = e.version + 1 where e.id = :id")
    int incrementSequence(@Param("id") UUID id);

    default Optional<SagaExecution> claimNextDue(Instant now, Duration lease) {
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be positive");
        for (UUID id : findDueIds(now)) {
            if (claimIfDue(id, now, now.plus(lease)) == 1) return findById(id);
        }
        return Optional.empty();
    }
}
