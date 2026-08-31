package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.ScreenWallStream;

/**
 * 监控墙流 Mapper
 */
public interface ScreenWallStreamMapper
{
    public ScreenWallStream selectScreenWallStreamById(Long id);

    public ScreenWallStream selectByWallCodeAndDeviceId(@Param("wallCode") String wallCode,
        @Param("deviceId") String deviceId);

    public ScreenWallStream selectByWallCodeAndSlotIndex(@Param("wallCode") String wallCode,
        @Param("slotIndex") Integer slotIndex);

    public List<ScreenWallStream> selectEnabledListByWallCode(@Param("wallCode") String wallCode);

    public int insertScreenWallStream(ScreenWallStream stream);

    public int updateScreenWallStream(ScreenWallStream stream);

    public int deleteScreenWallStreamById(Long id);
}
