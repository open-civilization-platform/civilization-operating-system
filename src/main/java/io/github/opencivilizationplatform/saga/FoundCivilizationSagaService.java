package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FoundCivilizationSagaService {
    private static final int MAX_NODE_NAME_LENGTH = 255;
    private static final String NODE_NAME_SUFFIX = "-Primary";
    private static final int MAX_CIVILIZATION_NAME_LENGTH = MAX_NODE_NAME_LENGTH - NODE_NAME_SUFFIX.length();
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final SagaExecutionRepository executions;
    private final TransactionTemplate transactions;

    public FoundCivilizationSagaService(SagaExecutionRepository executions, TransactionTemplate transactions) {
        this.executions = executions;
        this.transactions = transactions;
    }

    public SagaStatusResponse submit(FoundCivilizationRequest request, String ownerId, String idempotencyKey) {
        if (request == null || ownerId == null || ownerId.isBlank() || idempotencyKey == null
            || idempotencyKey.isBlank() || request.name() == null || request.name().isBlank()
            || request.regionId() == null) throw new IllegalArgumentException("Missing saga submission field");
        String name = request.name().trim();
        if (name.length() > MAX_CIVILIZATION_NAME_LENGTH || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Saga name or idempotency key exceeds its persistence limit");
        }
        CivilizationScale scale = request.scale() == null ? CivilizationScale.LOCAL : request.scale();
        String fingerprint = fingerprint(name, scale, request.regionId());
        try {
            return transactions.execute(ignored -> {
                var existing = executions.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey);
                if (existing.isPresent()) return matching(existing.get(), fingerprint);
                return SagaStatusResponse.from(executions.saveAndFlush(new SagaExecution(
                    ownerId, idempotencyKey, fingerprint, name, scale, request.regionId())));
            });
        } catch (DataIntegrityViolationException concurrentInsert) {
            return transactions.execute(ignored -> matching(executions
                .findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey).orElseThrow(() -> concurrentInsert), fingerprint));
        }
    }

    public SagaStatusResponse getStatus(UUID sagaId, String ownerId) {
        return transactions.execute(ignored -> SagaStatusResponse.from(executions
            .findByIdAndOwnerId(sagaId, ownerId).orElseThrow(() -> new SagaException("Saga not found"))));
    }

    private static SagaStatusResponse matching(SagaExecution existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) throw new SagaIdempotencyConflictException();
        return SagaStatusResponse.from(existing);
    }

    private static String fingerprint(String name, CivilizationScale scale, Long regionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] canonical = (name.length() + ":" + name + ":" + scale.name() + ":" + regionId)
                .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
