package com.ruoyi.web.controller.deployment;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.DeploymentBusinessEventTemplate;
import com.ruoyi.system.service.IDeploymentBusinessEventTemplateService;

@RestController
@RequestMapping("/deployment-business-events")
public class DeploymentBusinessEventTemplateController extends BaseController
{
    @Autowired
    private IDeploymentBusinessEventTemplateService deploymentBusinessEventTemplateService;

    @GetMapping
    public AjaxResult list(
        @RequestParam(value = "templateName", required = false) String templateName,
        @RequestParam(value = "templateCode", required = false) String templateCode,
        @RequestParam(value = "scopeType", required = false) String scopeType,
        @RequestParam(value = "status", required = false) String status)
    {
        DeploymentBusinessEventTemplate query = new DeploymentBusinessEventTemplate();
        query.setTemplateName(templateName);
        query.setTemplateCode(templateCode);
        query.setScopeType(scopeType);
        query.setStatus(status);
        List<DeploymentBusinessEventTemplate> list = deploymentBusinessEventTemplateService.selectList(query);
        List<Map<String, Object>> dataList = new ArrayList<>(list.size());
        for (DeploymentBusinessEventTemplate template : list)
        {
            dataList.add(toDataMap(template));
        }
        return AjaxResult.success(dataList);
    }

    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable("id") String id)
    {
        DeploymentBusinessEventTemplate template = deploymentBusinessEventTemplateService.selectByTemplateId(id);
        if (template == null)
        {
            return AjaxResult.error("业务事件模板不存在");
        }
        return AjaxResult.success(toDataMap(template));
    }

    @PostMapping
    public AjaxResult create(@RequestBody DeploymentBusinessEventTemplate template)
    {
        if (template == null || StringUtils.isEmpty(template.getTemplateName()))
        {
            return AjaxResult.error("templateName不能为空");
        }
        if (StringUtils.isEmpty(template.getTemplateId()))
        {
            template.setTemplateId(generateTemplateId());
        }
        template.setCreateBy(getUsername());
        template.setUpdateBy(getUsername());
        template.setCreateTime(new Date());
        template.setUpdateTime(new Date());
        template.setVersionNo(template.getVersionNo() == null || template.getVersionNo() <= 0 ? 1 : template.getVersionNo());
        template.setStatus(StringUtils.isEmpty(template.getStatus()) ? "ACTIVE" : template.getStatus());
        template.setScopeType(StringUtils.isEmpty(template.getScopeType()) ? "USER" : template.getScopeType());
        template.setOwnerUserId(template.getOwnerUserId() == null ? Long.valueOf(getUserId()) : template.getOwnerUserId());
        int rows = deploymentBusinessEventTemplateService.insertTemplate(template);
        if (rows <= 0)
        {
            return AjaxResult.error("业务事件模板创建失败");
        }
        return AjaxResult.success(toDataMap(deploymentBusinessEventTemplateService.selectByTemplateId(template.getTemplateId())));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable("id") String id, @RequestBody DeploymentBusinessEventTemplate template)
    {
        DeploymentBusinessEventTemplate existing = deploymentBusinessEventTemplateService.selectByTemplateId(id);
        if (existing == null)
        {
            return AjaxResult.error("业务事件模板不存在");
        }
        if (template == null || StringUtils.isEmpty(template.getTemplateName()))
        {
            return AjaxResult.error("templateName不能为空");
        }
        template.setTemplateId(id);
        template.setUpdateBy(getUsername());
        template.setUpdateTime(new Date());
        template.setCreateBy(existing.getCreateBy());
        template.setCreateTime(existing.getCreateTime());
        if (template.getOwnerUserId() == null)
        {
            template.setOwnerUserId(existing.getOwnerUserId());
        }
        if (StringUtils.isEmpty(template.getScopeType()))
        {
            template.setScopeType(existing.getScopeType());
        }
        if (template.getVersionNo() == null || template.getVersionNo() <= 0)
        {
            template.setVersionNo(existing.getVersionNo());
        }
        int rows = deploymentBusinessEventTemplateService.updateTemplate(template);
        if (rows <= 0)
        {
            return AjaxResult.error("业务事件模板更新失败");
        }
        return AjaxResult.success(toDataMap(deploymentBusinessEventTemplateService.selectByTemplateId(id)));
    }

    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") String id)
    {
        int rows = deploymentBusinessEventTemplateService.deleteByTemplateId(id);
        if (rows <= 0)
        {
            return AjaxResult.error("业务事件模板不存在或删除失败");
        }
        return AjaxResult.success();
    }

    private String generateTemplateId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Map<String, Object> toDataMap(DeploymentBusinessEventTemplate template)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (template == null)
        {
            return data;
        }
        data.put("templateId", template.getTemplateId());
        data.put("templateCode", template.getTemplateCode());
        data.put("templateName", template.getTemplateName());
        data.put("templateDesc", template.getTemplateDesc());
        data.put("scopeType", template.getScopeType());
        data.put("ownerOrgIndex", template.getOwnerOrgIndex());
        data.put("ownerUserId", template.getOwnerUserId());
        data.put("status", template.getStatus());
        data.put("versionNo", template.getVersionNo());
        data.put("tagsJson", template.getTagsJson());
        data.put("parameterSchemaJson", template.getParameterSchemaJson());
        data.put("ruleBlueprintJson", template.getRuleBlueprintJson());
        data.put("uiSchemaJson", template.getUiSchemaJson());
        data.put("createBy", template.getCreateBy());
        data.put("updateBy", template.getUpdateBy());
        data.put("createTime", template.getCreateTime());
        data.put("updateTime", template.getUpdateTime());
        return data;
    }
}