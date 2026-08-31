package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 业务事件模板对象 deployment_business_event_template
 */
public class DeploymentBusinessEventTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String templateId;
    private String templateCode;
    private String templateName;
    private String templateDesc;
    private String scopeType;
    private String ownerOrgIndex;
    private Long ownerUserId;
    private String status;
    private Integer versionNo;
    private String tagsJson;
    private String parameterSchemaJson;
    private String ruleBlueprintJson;
    private String uiSchemaJson;

    public String getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(String templateId)
    {
        this.templateId = templateId;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }

    public String getTemplateDesc()
    {
        return templateDesc;
    }

    public void setTemplateDesc(String templateDesc)
    {
        this.templateDesc = templateDesc;
    }

    public String getScopeType()
    {
        return scopeType;
    }

    public void setScopeType(String scopeType)
    {
        this.scopeType = scopeType;
    }

    public String getOwnerOrgIndex()
    {
        return ownerOrgIndex;
    }

    public void setOwnerOrgIndex(String ownerOrgIndex)
    {
        this.ownerOrgIndex = ownerOrgIndex;
    }

    public Long getOwnerUserId()
    {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId)
    {
        this.ownerUserId = ownerUserId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo)
    {
        this.versionNo = versionNo;
    }

    public String getTagsJson()
    {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson)
    {
        this.tagsJson = tagsJson;
    }

    public String getParameterSchemaJson()
    {
        return parameterSchemaJson;
    }

    public void setParameterSchemaJson(String parameterSchemaJson)
    {
        this.parameterSchemaJson = parameterSchemaJson;
    }

    public String getRuleBlueprintJson()
    {
        return ruleBlueprintJson;
    }

    public void setRuleBlueprintJson(String ruleBlueprintJson)
    {
        this.ruleBlueprintJson = ruleBlueprintJson;
    }

    public String getUiSchemaJson()
    {
        return uiSchemaJson;
    }

    public void setUiSchemaJson(String uiSchemaJson)
    {
        this.uiSchemaJson = uiSchemaJson;
    }
}