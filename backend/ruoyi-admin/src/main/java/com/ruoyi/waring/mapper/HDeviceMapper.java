package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Mapper
@Repository
public interface HDeviceMapper {

    void insertDevice(HDevice device);

    void deleteDevice();

    HDevice selectDeviceByApeId(String apeId);

    int insertDeviceCrud(HDevice device);

    int updateDevice(HDevice device);

    int deleteDeviceByApeIds(@Param("apeIds") String[] apeIds);

    int updateMonitorStateByApeId(@Param("apeId") String apeId,
                                  @Param("monitorStatus") String monitorStatus);

    int updatePlayUrlByApeId(@Param("apeId") String apeId,
                             @Param("playUrl") String playUrl);

    int updateZlmProxyKeyByApeId(@Param("apeId") String apeId,
                                 @Param("zlmProxyKey") String zlmProxyKey);

    List<HDevice> selectDeviceList(HDevice device);

    List<HDevice> selectLDeviceList(HDevice device);

    int getDeviceNum();

    int getDeviceNumByOrg(HDevice device);

    int getDeviceEnableNum();

    int getDeviceEnableNumByOrg(HDevice device);


}
