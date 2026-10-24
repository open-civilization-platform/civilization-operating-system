package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.web.handler.NexusWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SagaProgressRedisSubscriber {
    private static final Logger log = LoggerFactory.getLogger(SagaProgressRedisSubscriber.class);
    private final NexusWebSocketHandler handler;
    private final ObjectMapper mapper;

    public SagaProgressRedisSubscriber(NexusWebSocketHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    public void receiveMessage(String message) {
        try {
            var routed = mapper.readValue(message, SagaProgressEvent.Routed.class);
            if (routed == null || routed.ownerId() == null || routed.ownerId().isBlank()
                || routed.event() == null || !"SAGA_PROGRESS".equals(routed.event().type())
                || routed.event().payload() == null || routed.event().payload().sagaId() == null) {
                log.warn("Ignoring unroutable saga progress event");
                return;
            }
            handler.sendToClient(routed.ownerId(), mapper.writeValueAsString(routed.event()));
        } catch (Exception failureToNotify) {
            log.warn("Failed to process saga progress event", failureToNotify);
        }
    }
}
