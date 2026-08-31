package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HWaring;

public interface IAiReviewService
{
    void createImageReviewTask(HWaring waring);

    int scanAndDispatchPendingTasks();
}