package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.ScreenWallStream;

/**
 * 监控墙流服务接口
 */
public interface IScreenWallStreamService
{
    public ScreenWallStream upsertScreenWallStream(ScreenWallStream stream,
        Boolean taskPushEnabled,
        String algorithmStreamUrl);

    public List<ScreenWallStream> selectEnabledListByWallCode(String wallCode);

    public int deleteScreenWallStreamById(Long id);
}
