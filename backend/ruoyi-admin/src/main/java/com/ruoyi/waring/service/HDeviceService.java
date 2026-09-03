package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HDevice;

import java.util.List;
import java.util.Map;

public interface HDeviceService {
    void insertDevice(HDevice device);

    void deleteDevice();

    HDevice selectDeviceByApeId(String apeId);

    int insertDeviceCrud(HDevice device);

    int updateDevice(HDevice device);

    /**
     * 同学 A 同步国标行时调用：按 ape_id 或 gb_device_id+gb_platform_id 更新，否则插入。
     * 强制 device_type=gb28181，且两个 gb 字段必填。
     */
    HDevice upsertGb28181Device(HDevice device);

    int deleteDeviceByApeIds(String[] apeIds);

    List<HDevice> selectDeviceList(HDevice device, Long userId);

    Map<String, Object> getDeviceNum(Long userId);

    Map<String, Object> getDirectLiveUrl(String apeId);

    List<HDevice> selectLDeviceList(HDevice device, Long userId);

    int startMonitor(String apeId);

    int stopMonitor(String apeId);

    Map<String, Object> previewMonitor(String apeId);
}
