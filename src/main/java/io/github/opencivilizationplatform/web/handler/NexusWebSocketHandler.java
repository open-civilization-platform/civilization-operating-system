package io.github.opencivilizationplatform.web.handler;

import tools.jackson.databind.ObjectMapper;
import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusMessage;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NexusWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NexusWebSocketHandler.class);
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final NexusMeshService meshService;
    private final ObjectMapper objectMapper;

    public NexusWebSocketHandler(NexusMeshService meshService, ObjectMapper objectMapper) {
        this.meshService = meshService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String clientId = (String) session.getAttributes().get("X-Client-Id");
        sessions.put(session.getId(), new ClientSession(clientId, session));
        log.info("WebSocket connected: sessionId={}, clientId={}", session.getId(), clientId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var payload = objectMapper.readValue(message.getPayload(), Map.class);
        String action = (String) payload.getOrDefault("action", "");
        switch (action) {
            case "send_message" -> {
                Long sourceId = Long.valueOf(payload.get("sourceNodeId").toString());
                Long targetId = Long.valueOf(payload.get("targetNodeId").toString());
                String content = (String) payload.get("content");
                String typeStr = (String) payload.get("messageType");
                NexusMessageType msgType = NexusMessageType.valueOf(typeStr);
                meshService.sendMessage(sourceId, targetId, msgType, content);
            }
            case "ping" -> {
                sendText(session, new TextMessage("{\"type\":\"pong\"}"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(s -> s.session().isOpen()).count();
    }

    public void sendToClient(String clientId, String json) {
        if (clientId == null || clientId.isBlank()) return;
        TextMessage message = new TextMessage(json);
        for (ClientSession connected : sessions.values()) {
            if (!clientId.equals(connected.clientId())) continue;
            WebSocketSession session = connected.session();
            try {
                sendText(session, message);
            } catch (Exception failureToNotify) {
                log.warn("Failed to send saga progress to session {}", session.getId(), failureToNotify);
            }
        }
    }

    private record ClientSession(String clientId, WebSocketSession session) {}

    private void sendText(WebSocketSession session, TextMessage message) throws java.io.IOException {
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(message);
        }
    }

    public void broadcastMessageLocally(NexusMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "type", "Nexus-message",
                "sourceNodeId", msg.getSourceNode().getId(),
                "targetNodeId", msg.getTargetNode().getId(),
                "messageType", msg.getMessageType().name(),
                "content", msg.getContent(),
                "hopCount", msg.getHopCount()
            ));
            TextMessage textMsg = new TextMessage(json);
            for (ClientSession connected : sessions.values()) {
                WebSocketSession s = connected.session();
                sendText(s, textMsg);
            }
        } catch (Exception e) {
            log.error("Failed to broadcast message locally", e);
        }
    }
}
