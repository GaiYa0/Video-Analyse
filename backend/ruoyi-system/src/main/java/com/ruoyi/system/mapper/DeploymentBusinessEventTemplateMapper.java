package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.DeploymentBusinessEventTemplate;

/**
 * 业务事件模板 Mapper
 */
public interface DeploymentBusinessEventTemplateMapper
{
    public DeploymentBusinessEventTemplate selectByTemplateId(String templateId);

    public List<DeploymentBusinessEventTemplate> selectList(DeploymentBusinessEventTemplate query);

    public int insertTemplate(DeploymentBusinessEventTemplate template);

    public int updateTemplate(DeploymentBusinessEventTemplate template);

    public int deleteByTemplateId(String templateId);
}