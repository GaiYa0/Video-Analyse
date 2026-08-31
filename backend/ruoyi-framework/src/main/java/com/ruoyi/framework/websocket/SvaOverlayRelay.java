package com.ruoyi.framework.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.service.SvaDetectEventConsumer;
import com.ruoyi.common.utils.spring.SpringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SvaOverlayRelay
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SvaOverlayRelay.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long FRAME_TTL_MS = 5000L;
    private static final Map<String, CachedFrame> LATEST_FRAMES = new ConcurrentHashMap<>();
    private static final AtomicLong FORWARDED_FRAMES = new AtomicLong(0);

    private SvaOverlayRelay()
    {
    }

    public static boolean relayIncomingMessage(String message)
    {
        if (message == null || message.trim().isEmpty())
        {
            return false;
        }

        try
        {
            JsonNode root = OBJECT_MAPPER.readTree(message);
            String type = trimText(root.path("type").asText(""));
            if ("detect.frame".equals(type))
            {
                cacheFrame(root, message);
                WebSocketUsers.sendMessageToUsersByText(message);
                long count = FORWARDED_FRAMES.incrementAndGet();
                if ((count % 500) == 0)
                {
                    LOGGER.info("Forwarded {} detect.frame messages to web clients", count);
                }
                return true;
            }
            if ("detect.event".equals(type))
            {
                LOGGER.info("Received detect.event websocket payload: eventId={} eventState={} behaviorType={}",
                    trimText(root.path("eventId").asText("")),
                    trimText(root.path("eventState").asText("")),
                    trimText(root.path("behaviorType").asText("")));
                consumeDetectEvent(message);
                WebSocketUsers.sendMessageToUsersByText(message);
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            LOGGER.warn("Failed to parse SVA websocket payload: {}", ex.getMessage());
            return false;
        }
    }

    public static List<String> snapshotLatestFrames()
    {
        cleanupExpired(System.currentTimeMillis());
        List<String> messages = new ArrayList<>();
        for (CachedFrame cachedFrame : LATEST_FRAMES.values())
        {
            if (cachedFrame != null && cachedFrame.message != null && !cachedFrame.message.isEmpty())
            {
                messages.add(cachedFrame.message);
            }
        }
        return messages;
    }

    private static void cacheFrame(JsonNode root, String message)
    {
        String controlCode = trimText(root.path("controlCode").asText(root.path("control_code").asText("")));
        String streamCode = trimText(root.path("streamCode").asText(""));
        if (controlCode.isEmpty() && streamCode.isEmpty())
        {
            return;
        }
        String cacheKey = controlCode + "|" + streamCode;
        LATEST_FRAMES.put(cacheKey, new CachedFrame(message, System.currentTimeMillis()));
        cleanupExpired(System.currentTimeMillis());
    }

    private static void cleanupExpired(long nowMs)
    {
        for (Map.Entry<String, CachedFrame> entry : LATEST_FRAMES.entrySet())
        {
            CachedFrame cachedFrame = entry.getValue();
            if (cachedFrame == null || (nowMs - cachedFrame.timestampMs) > FRAME_TTL_MS)
            {
                LATEST_FRAMES.remove(entry.getKey(), cachedFrame);
            }
        }
    }

    private static String trimText(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static void consumeDetectEvent(String message)
    {
        try
        {
            SvaDetectEventConsumer consumer = SpringUtils.getBean(SvaDetectEventConsumer.class);
            consumer.consumeSvaDetectEvent(message);
        }
        catch (Exception ex)
        {
            LOGGER.warn("Failed to consume detect.event payload: {}", ex.getMessage(), ex);
        }
    }

    private static final class CachedFrame
    {
        private final String message;
        private final long timestampMs;

        private CachedFrame(String message, long timestampMs)
        {
            this.message = Objects.requireNonNullElse(message, "");
            this.timestampMs = timestampMs;
        }
    }
}