package com.ruoyi.waring.task;

import com.ruoyi.waring.service.IAiReviewService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component("aiReviewTask")
public class AiReviewTaskScheduler
{
    private static final Logger log = LoggerFactory.getLogger(AiReviewTaskScheduler.class);

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    @Resource
    private IAiReviewService aiReviewService;

    @Scheduled(initialDelay = 30000L, fixedDelay = 15000L)
    public void scheduledScanPending()
    {
        scanPending();
    }

    public void scanPending()
    {
        if (!scanning.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            int count = aiReviewService.scanAndDispatchPendingTasks();
            if (count > 0)
            {
                log.info("AI review dispatched {} task(s)", count);
            }
        }
        finally
        {
            scanning.set(false);
        }
    }
}