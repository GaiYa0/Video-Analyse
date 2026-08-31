package com.ruoyi.framework.websocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketUsers
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketUsers.class);

    private static final Map<String, Session> USERS = new ConcurrentHashMap<>();

    public static void put(String key, Session session)
    {
        USERS.put(key, session);
    }

    public static boolean remove(Session session)
    {
        String key = null;
        boolean flag = USERS.containsValue(session);
        if (flag)
        {
            Set<Map.Entry<String, Session>> entries = USERS.entrySet();
            for (Map.Entry<String, Session> entry : entries)
            {
                Session value = entry.getValue();
                if (value.equals(session))
                {
                    key = entry.getKey();
                    break;
                }
            }
        }
        else
        {
            return true;
        }
        return remove(key);
    }

    public static boolean remove(String key)
    {
        LOGGER.info("\n 正在移出用户 - {}", key);
        Session remove = USERS.remove(key);
        if (remove != null)
        {
            boolean containsValue = USERS.containsValue(remove);
            LOGGER.info("\n 移出结果 - {}", containsValue ? "失败" : "成功");
            return containsValue;
        }
        return true;
    }

    public static Map<String, Session> getUsers()
    {
        return USERS;
    }

    public static void sendMessageToUsersByText(String message)
    {
        Collection<Session> values = USERS.values();
        for (Session value : values)
        {
            sendMessageToUserByText(value, message);
        }
    }

    public static void sendMessageToUserByText(Session session, String message)
    {
        if (session != null)
        {
            try
            {
                session.getBasicRemote().sendText(message);
            }
            catch (IOException e)
            {
                LOGGER.error("\n[发送消息异常]", e);
            }
        }
        else
        {
            LOGGER.info("\n[你已离线]");
        }
    }
}
