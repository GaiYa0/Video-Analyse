package com.ruoyi.framework.websocket;

import java.util.concurrent.Semaphore;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ServerEndpoint("/websocket/message")
public class WebSocketServer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketServer.class);

    public static int socketMaxOnlineCount = 100;

    private static final Semaphore socketSemaphore = new Semaphore(socketMaxOnlineCount);

    @OnOpen
    public void onOpen(Session session) throws Exception
    {
        boolean semaphoreFlag = SemaphoreUtils.tryAcquire(socketSemaphore);
        if (!semaphoreFlag)
        {
            LOGGER.error("\n 当前在线人数超过限制数- {}", socketMaxOnlineCount);
            WebSocketUsers.sendMessageToUserByText(session, "当前在线人数超过限制数：" + socketMaxOnlineCount);
            session.close();
        }
        else
        {
            WebSocketUsers.put(session.getId(), session);
            LOGGER.info("\n 建立连接 - {}", session);
            LOGGER.info("\n 当前人数 - {}", WebSocketUsers.getUsers().size());
            WebSocketUsers.sendMessageToUserByText(session, "连接成功");
            for (String message : SvaOverlayRelay.snapshotLatestFrames())
            {
                WebSocketUsers.sendMessageToUserByText(session, message);
            }
        }
    }

    @OnClose
    public void onClose(Session session)
    {
        LOGGER.info("\n 关闭连接 - {}", session);
        WebSocketUsers.remove(session.getId());
        SemaphoreUtils.release(socketSemaphore);
    }

    @OnError
    public void onError(Session session, Throwable exception) throws Exception
    {
        if (session != null && session.isOpen())
        {
            session.close();
        }
        String sessionId = session == null ? null : session.getId();
        LOGGER.info("\n 连接异常 - {}", sessionId);
        LOGGER.info("\n 异常信息 - {}", exception.getMessage(), exception);
        if (sessionId != null)
        {
            WebSocketUsers.remove(sessionId);
        }
        SemaphoreUtils.release(socketSemaphore);
    }

    @OnMessage
    public void onMessage(String message, Session session)
    {
        String msg = message.replace("你", "我").replace("吗", "");
        WebSocketUsers.sendMessageToUserByText(session, msg);
    }
}
