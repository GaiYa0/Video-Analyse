package com.ruoyi.web.controller.monitor;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.service.monitor.MediaStreamMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitor/media")
public class MediaServerController
{
    @Autowired
    private MediaStreamMonitorService mediaStreamMonitorService;

    @PreAuthorize("@ss.hasPermi('monitor:media:list')")
    @GetMapping("/streams")
    public AjaxResult listStreams()
    {
        return AjaxResult.success(mediaStreamMonitorService.listStreams());
    }
}