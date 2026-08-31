package com.ruoyi.waring.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface DeviceMapper {
    String getDeviceId(String deviceName);
}
