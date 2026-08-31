package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.DeploymentBusinessEventTemplate;

/**
 * 业务事件模板服务接口
 */
public interface IDeploymentBusinessEventTemplateService
{
    public DeploymentBusinessEventTemplate selectByTemplateId(String templateId);

    public List<DeploymentBusinessEventTemplate> selectList(DeploymentBusinessEventTemplate query);

    public int insertTemplate(DeploymentBusinessEventTemplate template);

    public int updateTemplate(DeploymentBusinessEventTemplate template);

    public int deleteByTemplateId(String templateId);
}