package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.ScreenWallStream;
import com.ruoyi.system.mapper.ScreenWallStreamMapper;
import com.ruoyi.system.service.IScreenWallStreamService;

/**
 * 监控墙流服务实现
 */
@Service
public class ScreenWallStreamServiceImpl implements IScreenWallStreamService
{
    private static final String SOURCE_TYPE_REALTIME = "realtime";
    private static final String SOURCE_TYPE_TASK = "task";

    @Autowired
    private ScreenWallStreamMapper screenWallStreamMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenWallStream upsertScreenWallStream(ScreenWallStream stream,
        Boolean taskPushEnabled,
        String algorithmStreamUrl)
    {
        String sourceType = normalizeSourceType(stream.getSourceType());
        String finalPlayUrl = resolveFinalPlayUrl(sourceType, stream.getPlayUrl(), taskPushEnabled, algorithmStreamUrl);

        if (StringUtils.isEmpty(finalPlayUrl))
        {
            throw new IllegalArgumentException("playUrl不能为空");
        }

        stream.setSourceType(sourceType);
        stream.setPlayUrl(finalPlayUrl);
        if (stream.getEnabled() == null)
        {
            stream.setEnabled(1);
        }

        ScreenWallStream byWallDevice = screenWallStreamMapper.selectByWallCodeAndDeviceId(stream.getWallCode(),
            stream.getDeviceId());
        ScreenWallStream byWallSlot = null;
        if (stream.getSlotIndex() != null)
        {
            byWallSlot = screenWallStreamMapper.selectByWallCodeAndSlotIndex(stream.getWallCode(), stream.getSlotIndex());
        }

        ScreenWallStream target = null;
        if (byWallSlot != null && (byWallDevice == null || !byWallSlot.getId().equals(byWallDevice.getId())))
        {
            if (byWallDevice != null)
            {
                screenWallStreamMapper.deleteScreenWallStreamById(byWallDevice.getId());
            }
            target = byWallSlot;
        }
        else if (byWallDevice != null)
        {
            target = byWallDevice;
        }

        Date now = new Date();
        if (target == null)
        {
            stream.setCreateTime(now);
            stream.setUpdateTime(now);
            screenWallStreamMapper.insertScreenWallStream(stream);
            return screenWallStreamMapper.selectScreenWallStreamById(stream.getId());
        }

        stream.setId(target.getId());
        stream.setUpdateTime(now);
        screenWallStreamMapper.updateScreenWallStream(stream);
        return screenWallStreamMapper.selectScreenWallStreamById(target.getId());
    }

    @Override
    public List<ScreenWallStream> selectEnabledListByWallCode(String wallCode)
    {
        return screenWallStreamMapper.selectEnabledListByWallCode(wallCode);
    }

    @Override
    public int deleteScreenWallStreamById(Long id)
    {
        return screenWallStreamMapper.deleteScreenWallStreamById(id);
    }

    private String normalizeSourceType(String sourceType)
    {
        if (StringUtils.isEmpty(sourceType))
        {
            throw new IllegalArgumentException("sourceType不能为空");
        }
        String normalized = sourceType.trim().toLowerCase();
        if (!SOURCE_TYPE_REALTIME.equals(normalized) && !SOURCE_TYPE_TASK.equals(normalized))
        {
            throw new IllegalArgumentException("sourceType仅支持realtime或task");
        }
        return normalized;
    }

    private String resolveFinalPlayUrl(String sourceType,
        String playUrl,
        Boolean taskPushEnabled,
        String algorithmStreamUrl)
    {
        if (SOURCE_TYPE_REALTIME.equals(sourceType))
        {
            return playUrl;
        }

        if (Boolean.TRUE.equals(taskPushEnabled) && StringUtils.isNotEmpty(algorithmStreamUrl))
        {
            return algorithmStreamUrl;
        }
        return playUrl;
    }
}
