package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.DeploymentBusinessEventTemplate;
import com.ruoyi.system.mapper.DeploymentBusinessEventTemplateMapper;
import com.ruoyi.system.service.IDeploymentBusinessEventTemplateService;

/**
 * 业务事件模板服务实现
 */
@Service
public class DeploymentBusinessEventTemplateServiceImpl implements IDeploymentBusinessEventTemplateService
{
    @Autowired
    private DeploymentBusinessEventTemplateMapper deploymentBusinessEventTemplateMapper;

    @Override
    public DeploymentBusinessEventTemplate selectByTemplateId(String templateId)
    {
        return deploymentBusinessEventTemplateMapper.selectByTemplateId(templateId);
    }

    @Override
    public List<DeploymentBusinessEventTemplate> selectList(DeploymentBusinessEventTemplate query)
    {
        return deploymentBusinessEventTemplateMapper.selectList(query);
    }

    @Override
    public int insertTemplate(DeploymentBusinessEventTemplate template)
    {
        return deploymentBusinessEventTemplateMapper.insertTemplate(template);
    }

    @Override
    public int updateTemplate(DeploymentBusinessEventTemplate template)
    {
        return deploymentBusinessEventTemplateMapper.updateTemplate(template);
    }

    @Override
    public int deleteByTemplateId(String templateId)
    {
        return deploymentBusinessEventTemplateMapper.deleteByTemplateId(templateId);
    }
}