package io.github.opencivilizationplatform.web.handler;

import io.github.opencivilizationplatform.modules.nexus.application.NexusMeshService;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusMessage;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusMessageType;
import io.github.opencivilizationplatform.modules.nexus.domain.NexusNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NexusWebSocketHandlerTest {
    private final NexusWebSocketHandler handler = new NexusWebSocketHandler(
        mock(NexusMeshService.class), JsonMapper.builder().build());

    // Catches broadcasting private progress or skipping additional sessions for the same owner.
    @Test void sendsSagaEventOnlyToMatchingClientSessions() throws Exception {
        WebSocketSession first = connect("first", "owner");
        WebSocketSession second = connect("second", "owner");
        WebSocketSession foreign = connect("foreign", "another-owner");
        WebSocketSession anonymous = connect("anonymous", null);
        String json = "{\"type\":\"SAGA_PROGRESS\",\"payload\":{\"sequence\":2}}";

        handler.sendToClient("owner", json);

        verify(first).sendMessage(new TextMessage(json));
        verify(second).sendMessage(new TextMessage(json));
        verify(foreign, never()).sendMessage(any());
        verify(anonymous, never()).sendMessage(any());
    }

    @Test void missingOwnerCannotTargetAnonymousSessions() throws Exception {
        WebSocketSession anonymous = connect("anonymous", null);
        handler.sendToClient(null, "private");
        handler.sendToClient("", "private");
        verify(anonymous, never()).sendMessage(any());
    }

    @Test void closedAndDisconnectedSessionsDoNotReceiveProgress() throws Exception {
        WebSocketSession closed = connect("closed", "owner");
        WebSocketSession disconnected = connect("disconnected", "owner");
        when(closed.isOpen()).thenReturn(false);
        handler.afterConnectionClosed(disconnected, CloseStatus.NORMAL);
        handler.sendToClient("owner", "progress");
        verify(closed, never()).sendMessage(any());
        verify(disconnected, never()).sendMessage(any());
    }

    @Test void brokenSocketDoesNotPreventOtherOwnerSessionsReceivingProgress() throws Exception {
        WebSocketSession broken = connect("broken", "owner");
        WebSocketSession healthy = connect("healthy", "owner");
        doThrow(new IOException("connection reset")).when(broken).sendMessage(any());
        handler.sendToClient("owner", "progress");
        verify(healthy).sendMessage(new TextMessage("progress"));
    }

    @Test void nexusMessagesStillBroadcastToAllClients() throws Exception {
        WebSocketSession owner = connect("owner", "owner");
        WebSocketSession foreign = connect("foreign", "another-owner");
        handler.broadcastMessageLocally(nexusMessage());
        verify(owner).sendMessage(any(TextMessage.class));
        verify(foreign).sendMessage(any(TextMessage.class));
    }

    // Catches an unsynchronized Nexus or pong write while Redis progress owns the socket.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serializesNexusAndPongWritesWithSagaProgress(boolean pong) throws Exception {
        WebSocketSession session = connect("owner", "owner");
        CountDownLatch progressEntered = new CountDownLatch(1);
        CountDownLatch releaseProgress = new CountDownLatch(1);
        CountDownLatch otherStarted = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (first.getAndSet(false)) {
                progressEntered.countDown();
                assertTrue(releaseProgress.await(5, TimeUnit.SECONDS));
            }
            return null;
        }).when(session).sendMessage(any());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var progress = executor.submit(() -> handler.sendToClient("owner", "progress"));
            try {
                assertTrue(progressEntered.await(5, TimeUnit.SECONDS));
                var other = executor.submit(() -> {
                    otherStarted.countDown();
                    if (pong) handler.handleTextMessage(session, new TextMessage("{\"action\":\"ping\"}"));
                    else handler.broadcastMessageLocally(nexusMessage());
                    return null;
                });
                assertTrue(otherStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> other.get(100, TimeUnit.MILLISECONDS));
                releaseProgress.countDown();
                progress.get(5, TimeUnit.SECONDS);
                other.get(5, TimeUnit.SECONDS);
                verify(session, times(2)).sendMessage(any());
            } finally {
                releaseProgress.countDown();
            }
        }
    }

    private NexusMessage nexusMessage() {
        NexusNode source = new NexusNode();
        source.setId(1L);
        NexusNode target = new NexusNode();
        target.setId(2L);
        NexusMessage message = new NexusMessage();
        message.setSourceNode(source);
        message.setTargetNode(target);
        message.setMessageType(NexusMessageType.values()[0]);
        message.setContent("mesh content");
        message.setHopCount(0);
        return message;
    }

    private WebSocketSession connect(String id, String clientId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(clientId == null ? Map.of() : Map.of("X-Client-Id", clientId));
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }
}
