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

    int deleteDeviceByApeIds(String[] apeIds);

    List<HDevice> selectDeviceList(HDevice device, Long userId);

    Map<String, Object> getDeviceNum(Long userId);

    Map<String, Object> getDirectLiveUrl(String apeId);

    List<HDevice> selectLDeviceList(HDevice device, Long userId);

    int startMonitor(String apeId);

    int stopMonitor(String apeId);

    Map<String, Object> previewMonitor(String apeId);
}
