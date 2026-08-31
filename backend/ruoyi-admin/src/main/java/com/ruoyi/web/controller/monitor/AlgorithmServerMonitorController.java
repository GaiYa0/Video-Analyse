package com.ruoyi.web.controller.monitor;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.service.monitor.AlgorithmServerMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitor/algorithm")
public class AlgorithmServerMonitorController
{
    @Autowired
    private AlgorithmServerMonitorService algorithmServerMonitorService;

    @PreAuthorize("@ss.hasPermi('monitor:algorithm:list')")
    @GetMapping("/controls")
    public AjaxResult listControls()
    {
        return AjaxResult.success(algorithmServerMonitorService.listControls());
    }
}
