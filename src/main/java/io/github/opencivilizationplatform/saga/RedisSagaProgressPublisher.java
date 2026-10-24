package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.RedisEventConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Component
public class RedisSagaProgressPublisher implements SagaProgressPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisSagaProgressPublisher.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisSagaProgressPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    // The worker invokes this adapter after its transition transaction commits.
    @Override public void publish(String ownerId, UUID sagaId, SagaStatusResponse snapshot, String currentStep,
                                  SagaFailureView failure, SagaCompensationView compensation) {
        try {
            var payload = new SagaProgressPayload(sagaId, snapshot.status(), currentStep, snapshot.steps(),
                failure, compensation, snapshot.updatedAt(), snapshot.sequence());
            var routed = new SagaProgressEvent.Routed(ownerId, new SagaProgressEvent(payload));
            redis.convertAndSend(RedisEventConfig.SAGA_PROGRESS_CHANNEL_NAME, mapper.writeValueAsString(routed));
        } catch (Exception failureToNotify) {
            // Delivery is best effort. GET restores the already committed saga state.
            log.warn("Failed to publish progress for saga {} sequence {}", sagaId, snapshot.sequence(), failureToNotify);
        }
    }
}
