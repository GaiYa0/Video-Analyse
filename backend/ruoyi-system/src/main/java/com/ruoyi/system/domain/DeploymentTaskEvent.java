package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 布控任务业务事件实例对象 deployment_task_event
 */
public class DeploymentTaskEvent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String deploymentId;
    private String eventKey;
    private String templateId;
    private Integer templateVersion;
    private String eventName;
    private Boolean enabled;
    private Integer sortOrder;
    private String parameterValuesJson;
    private String compiledRuleIdsJson;
    private String compiledRuleSnapshotJson;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getDeploymentId()
    {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId)
    {
        this.deploymentId = deploymentId;
    }

    public String getEventKey()
    {
        return eventKey;
    }

    public void setEventKey(String eventKey)
    {
        this.eventKey = eventKey;
    }

    public String getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(String templateId)
    {
        this.templateId = templateId;
    }

    public Integer getTemplateVersion()
    {
        return templateVersion;
    }

    public void setTemplateVersion(Integer templateVersion)
    {
        this.templateVersion = templateVersion;
    }

    public String getEventName()
    {
        return eventName;
    }

    public void setEventName(String eventName)
    {
        this.eventName = eventName;
    }

    public Boolean getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Boolean enabled)
    {
        this.enabled = enabled;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getParameterValuesJson()
    {
        return parameterValuesJson;
    }

    public void setParameterValuesJson(String parameterValuesJson)
    {
        this.parameterValuesJson = parameterValuesJson;
    }

    public String getCompiledRuleIdsJson()
    {
        return compiledRuleIdsJson;
    }

    public void setCompiledRuleIdsJson(String compiledRuleIdsJson)
    {
        this.compiledRuleIdsJson = compiledRuleIdsJson;
    }

    public String getCompiledRuleSnapshotJson()
    {
        return compiledRuleSnapshotJson;
    }

    public void setCompiledRuleSnapshotJson(String compiledRuleSnapshotJson)
    {
        this.compiledRuleSnapshotJson = compiledRuleSnapshotJson;
    }
}