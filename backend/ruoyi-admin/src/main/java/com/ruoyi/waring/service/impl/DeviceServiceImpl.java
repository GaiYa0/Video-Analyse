package com.ruoyi.waring.service.impl;


import com.ruoyi.waring.mapper.DeviceMapper;
import com.ruoyi.waring.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    DeviceMapper deviceMapper;

    public String getDeviceId(String deviceName) {
        return deviceMapper.getDeviceId(deviceName);
    }
}
