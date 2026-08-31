package com.ruoyi.framework.websocket;

import java.util.concurrent.atomic.AtomicLong;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Accepts SVA WebSocket push payloads and relays real-time overlay messages to
 * the existing web websocket channel.
 */
@Component
@ServerEndpoint("/websocket/sva/noop")
public class SvaNoopWebSocketEndpoint
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SvaNoopWebSocketEndpoint.class);
    private static final AtomicLong ACTIVE_SESSIONS = new AtomicLong(0);
    private static final AtomicLong RECEIVED_MESSAGES = new AtomicLong(0);
    private static final int MAX_TEXT_MESSAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BINARY_MESSAGE_BYTES = 4 * 1024 * 1024;

    @OnOpen
    public void onOpen(Session session)
    {
        session.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES);
        session.setMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BYTES);
        long active = ACTIVE_SESSIONS.incrementAndGet();
        LOGGER.info("SVA overlay websocket opened: sessionId={}, active={}", session.getId(), active);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason)
    {
        long active = ACTIVE_SESSIONS.updateAndGet(v -> Math.max(0, v - 1));
        LOGGER.info("SVA overlay websocket closed: sessionId={}, active={}", session.getId(), active);
    }

    @OnError
    public void onError(Session session, Throwable exception)
    {
        String sessionId = session == null ? "unknown" : session.getId();
        LOGGER.warn("SVA overlay websocket error: sessionId={}, message={}", sessionId,
            exception == null ? "unknown" : exception.getMessage());
    }

    @OnMessage
    public void onMessage(String message, Session session)
    {
        long count = RECEIVED_MESSAGES.incrementAndGet();
        boolean relayed = SvaOverlayRelay.relayIncomingMessage(message);
        if ((count % 500) == 0)
        {
            LOGGER.info("SVA overlay websocket received {} messages, latestSessionId={}, relayed={}",
                    count, session.getId(), relayed);
        }
    }
}
