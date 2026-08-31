package com.ruoyi.web.controller.deployment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.ScreenWallStream;
import com.ruoyi.system.service.IScreenWallStreamService;

@RestController
@RequestMapping("/screen-wall/streams")
public class ScreenWallStreamController
{
    @Autowired
    private IScreenWallStreamService screenWallStreamService;

    @PostMapping("/upsert")
    public AjaxResult upsert(@RequestBody UpsertScreenWallStreamRequest request)
    {
        if (request == null)
        {
            return AjaxResult.error("请求体不能为空");
        }
        if (StringUtils.isEmpty(request.getWallCode()))
        {
            return AjaxResult.error("wallCode不能为空");
        }
        if (StringUtils.isEmpty(request.getDeviceId()))
        {
            return AjaxResult.error("deviceId不能为空");
        }
        if (StringUtils.isEmpty(request.getSourceType()))
        {
            return AjaxResult.error("sourceType不能为空");
        }

        ScreenWallStream stream = new ScreenWallStream();
        stream.setWallCode(request.getWallCode());
        stream.setSourceType(request.getSourceType());
        stream.setSourceId(request.getSourceId());
        stream.setDeviceId(request.getDeviceId());
        stream.setPlayUrl(request.getPlayUrl());
        stream.setTitle(request.getTitle());
        stream.setSlotIndex(request.getSlotIndex());
        stream.setEnabled(request.getEnabled());

        try
        {
            ScreenWallStream saved = screenWallStreamService.upsertScreenWallStream(stream,
                request.getTaskPushEnabled(),
                request.getAlgorithmStreamUrl());
            return AjaxResult.success(toDataMap(saved));
        }
        catch (IllegalArgumentException ex)
        {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @GetMapping
    public AjaxResult list(@RequestParam("wallCode") String wallCode)
    {
        if (StringUtils.isEmpty(wallCode))
        {
            return AjaxResult.error("wallCode不能为空");
        }

        List<ScreenWallStream> records = screenWallStreamService.selectEnabledListByWallCode(wallCode);
        List<Map<String, Object>> dataList = new ArrayList<>(records.size());
        for (ScreenWallStream record : records)
        {
            dataList.add(toDataMap(record));
        }
        return AjaxResult.success(dataList);
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id)
    {
        int rows = screenWallStreamService.deleteScreenWallStreamById(id);
        if (rows <= 0)
        {
            return AjaxResult.error("记录不存在或删除失败");
        }
        return AjaxResult.success();
    }

    private Map<String, Object> toDataMap(ScreenWallStream record)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("wallCode", record.getWallCode());
        data.put("sourceType", record.getSourceType());
        data.put("sourceId", record.getSourceId());
        data.put("deviceId", record.getDeviceId());
        data.put("playUrl", record.getPlayUrl());
        data.put("title", record.getTitle());
        data.put("slotIndex", record.getSlotIndex());
        data.put("enabled", record.getEnabled());
        data.put("createTime", record.getCreateTime());
        data.put("updateTime", record.getUpdateTime());
        return data;
    }

    public static class UpsertScreenWallStreamRequest
    {
        private String wallCode;
        private String sourceType;
        private String sourceId;
        private String deviceId;
        private String playUrl;
        private String title;
        private Integer slotIndex;
        private Integer enabled;
        private Boolean taskPushEnabled;
        private String algorithmStreamUrl;

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

        public void setEnabled(Object enabled)
        {
            this.enabled = normalizeEnabled(enabled);
        }

        private Integer normalizeEnabled(Object value)
        {
            if (value == null)
            {
                return null;
            }
            if (value instanceof Boolean)
            {
                return (Boolean) value ? 1 : 0;
            }
            if (value instanceof Number)
            {
                return ((Number) value).intValue() == 0 ? 0 : 1;
            }
            if (value instanceof String)
            {
                String normalized = ((String) value).trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty())
                {
                    return null;
                }
                if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized))
                {
                    return 1;
                }
                if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized))
                {
                    return 0;
                }
                try
                {
                    return Integer.parseInt(normalized) == 0 ? 0 : 1;
                }
                catch (NumberFormatException ex)
                {
                    throw new IllegalArgumentException("enabled仅支持boolean、number、string(true/false/1/0/yes/no)");
                }
            }
            throw new IllegalArgumentException("enabled仅支持boolean、number、string(true/false/1/0/yes/no)");
        }

        public Boolean getTaskPushEnabled()
        {
            return taskPushEnabled;
        }

        public void setTaskPushEnabled(Boolean taskPushEnabled)
        {
            this.taskPushEnabled = taskPushEnabled;
        }

        public String getAlgorithmStreamUrl()
        {
            return algorithmStreamUrl;
        }

        public void setAlgorithmStreamUrl(String algorithmStreamUrl)
        {
            this.algorithmStreamUrl = algorithmStreamUrl;
        }
    }
}
