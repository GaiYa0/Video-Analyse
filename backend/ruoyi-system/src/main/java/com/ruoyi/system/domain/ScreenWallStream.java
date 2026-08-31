package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 监控墙流对象 h_screen_wall_stream
 */
public class ScreenWallStream extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String wallCode;
    private String sourceType;
    private String sourceId;
    private String deviceId;
    private String playUrl;
    private String title;
    private Integer slotIndex;
    private Integer enabled;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getWallCode()
    {
        return wallCode;
    }

    public void setWallCode(String wallCode)
    {
        this.wallCode = wallCode;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getSourceId()
    {
        return sourceId;
    }

    public void setSourceId(String sourceId)
    {
        this.sourceId = sourceId;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getPlayUrl()
    {
        return playUrl;
    }

    public void setPlayUrl(String playUrl)
    {
        this.playUrl = playUrl;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getSlotIndex()
    {
        return slotIndex;
    }

    public void setSlotIndex(Integer slotIndex)
    {
        this.slotIndex = slotIndex;
    }

    public Integer getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Integer enabled)
    {
        this.enabled = enabled;
    }
}
