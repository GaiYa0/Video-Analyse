package com.ruoyi.framework.websocket;

import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SemaphoreUtils
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SemaphoreUtils.class);

    public static boolean tryAcquire(Semaphore semaphore)
    {
        boolean flag = false;
        try
        {
            flag = semaphore.tryAcquire();
        }
        catch (Exception e)
        {
            LOGGER.error("获取信号量异常", e);
        }
        return flag;
    }

    public static void release(Semaphore semaphore)
    {
        try
        {
            semaphore.release();
        }
        catch (Exception e)
        {
            LOGGER.error("释放信号量异常", e);
        }
    }
}
