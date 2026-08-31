package com.ruoyi.system.domain;

import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 布控任务对象 deployment_task
 */
public class DeploymentTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String deploymentId;
    private String taskName;
    private String deviceId;
    private String algorithmCode;
    private String algorithmName;
    private String targetCode;
    private Boolean pushEnabled;
    private Boolean frontendOverlayEnabled;
    private String recordEngine;
    private Integer alarmIntervalSec;
    private Boolean dwellEnabled;
    private Long dwellThresholdMs;
    private Boolean aiReviewEnabled;
    private String aiReviewPrompt;
    private String geometryConfig;
    private String streamUrl;
    private String pushStreamUrl;
    private String algorithmStreamUrl;
    private String status;
    private Date startTime;
    private Date stopTime;
    private List<DeploymentTaskAlgorithm> algorithmTasks;

    public String getDeploymentId()
    {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId)
    {
        this.deploymentId = deploymentId;
    }

    public String getTaskName()
    {
        return taskName;
    }

    public void setTaskName(String taskName)
    {
        this.taskName = taskName;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getAlgorithmCode()
    {
        return algorithmCode;
    }

    public void setAlgorithmCode(String algorithmCode)
    {
        this.algorithmCode = algorithmCode;
    }

    public String getAlgorithmName()
    {
        return algorithmName;
    }

    public void setAlgorithmName(String algorithmName)
    {
        this.algorithmName = algorithmName;
    }

    public String getTargetCode()
    {
        return targetCode;
    }

    public void setTargetCode(String targetCode)
    {
        this.targetCode = targetCode;
    }

    public Boolean getPushEnabled()
    {
        return pushEnabled;
    }

    public void setPushEnabled(Boolean pushEnabled)
    {
        this.pushEnabled = pushEnabled;
    }

    public Boolean getFrontendOverlayEnabled()
    {
        return frontendOverlayEnabled;
    }

    public void setFrontendOverlayEnabled(Boolean frontendOverlayEnabled)
    {
        this.frontendOverlayEnabled = frontendOverlayEnabled;
    }

    public String getRecordEngine()
    {
        return recordEngine;
    }

    public void setRecordEngine(String recordEngine)
    {
        this.recordEngine = recordEngine;
    }

    public Integer getAlarmIntervalSec()
    {
        return alarmIntervalSec;
    }

    public void setAlarmIntervalSec(Integer alarmIntervalSec)
    {
        this.alarmIntervalSec = alarmIntervalSec;
    }

    public Boolean getDwellEnabled()
    {
        return dwellEnabled;
    }

    public void setDwellEnabled(Boolean dwellEnabled)
    {
        this.dwellEnabled = dwellEnabled;
    }

    public Long getDwellThresholdMs()
    {
        return dwellThresholdMs;
    }

    public void setDwellThresholdMs(Long dwellThresholdMs)
    {
        this.dwellThresholdMs = dwellThresholdMs;
    }

    public Boolean getAiReviewEnabled()
    {
        return aiReviewEnabled;
    }

    public void setAiReviewEnabled(Boolean aiReviewEnabled)
    {
        this.aiReviewEnabled = aiReviewEnabled;
    }

    public String getAiReviewPrompt()
    {
        return aiReviewPrompt;
    }

    public void setAiReviewPrompt(String aiReviewPrompt)
    {
        this.aiReviewPrompt = aiReviewPrompt;
    }

    public String getGeometryConfig()
    {
        return geometryConfig;
    }

    public void setGeometryConfig(String geometryConfig)
    {
        this.geometryConfig = geometryConfig;
    }

    public String getStreamUrl()
    {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl)
    {
        this.streamUrl = streamUrl;
    }

    public String getPushStreamUrl()
    {
        return pushStreamUrl;
    }

    public void setPushStreamUrl(String pushStreamUrl)
    {
        this.pushStreamUrl = pushStreamUrl;
    }

    public String getAlgorithmStreamUrl()
    {
        return algorithmStreamUrl;
    }

    public void setAlgorithmStreamUrl(String algorithmStreamUrl)
    {
        this.algorithmStreamUrl = algorithmStreamUrl;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getStopTime()
    {
        return stopTime;
    }

    public void setStopTime(Date stopTime)
    {
        this.stopTime = stopTime;
    }

    public List<DeploymentTaskAlgorithm> getAlgorithmTasks()
    {
        return algorithmTasks;
    }

    public void setAlgorithmTasks(List<DeploymentTaskAlgorithm> algorithmTasks)
    {
        this.algorithmTasks = algorithmTasks;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("deploymentId", getDeploymentId())
            .append("taskName", getTaskName())
            .append("deviceId", getDeviceId())
            .append("algorithmCode", getAlgorithmCode())
            .append("algorithmName", getAlgorithmName())
            .append("targetCode", getTargetCode())
            .append("pushEnabled", getPushEnabled())
            .append("frontendOverlayEnabled", getFrontendOverlayEnabled())
            .append("recordEngine", getRecordEngine())
            .append("alarmIntervalSec", getAlarmIntervalSec())
            .append("dwellEnabled", getDwellEnabled())
            .append("dwellThresholdMs", getDwellThresholdMs())
            .append("aiReviewEnabled", getAiReviewEnabled())
            .append("aiReviewPrompt", getAiReviewPrompt())
            .append("remark", getRemark())
            .append("geometryConfig", getGeometryConfig())
            .append("streamUrl", getStreamUrl())
            .append("pushStreamUrl", getPushStreamUrl())
            .append("algorithmStreamUrl", getAlgorithmStreamUrl())
            .append("status", getStatus())
            .append("startTime", getStartTime())
            .append("stopTime", getStopTime())
            .append("algorithmTasks", getAlgorithmTasks())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
