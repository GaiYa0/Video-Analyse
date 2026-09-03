package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.service.IGb28181DeviceSyncService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同步拉数桩：不调 ZLM。同学 A 在本类替换实现，写入时走 HDeviceService.upsertGb28181Device。
 */
@Service
public class Gb28181DeviceSyncServiceImpl implements IGb28181DeviceSyncService {

    @Override
    public Map<String, Object> syncFromZlm() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inserted", 0);
        result.put("updated", 0);
        result.put("failed", 0);
        result.put("ready", false);
        result.put("message", "同步拉数待同学 A 接入 ZLM REST/Hook。写入必须带 device_type=gb28181、gb_device_id、gb_platform_id。");
        return result;
    }
}
