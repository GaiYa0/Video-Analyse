package com.ruoyi.system.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 布控任务算法配置对象 deployment_task_algorithm
 */
public class DeploymentTaskAlgorithm extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;
    private String deploymentId;
    private String algorithmCode;
    private String algorithmName;
    private Float detectFps;
    private Float scoreThreshold;
    private Float nmsThreshold;
    private List<String> targetCodes = new ArrayList<>();
    private String targetCodesText;
    private Integer sortOrder;

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

    public Float getDetectFps()
    {
        return detectFps;
    }

    public void setDetectFps(Float detectFps)
    {
        this.detectFps = detectFps;
    }

    public Float getScoreThreshold()
    {
        return scoreThreshold;
    }

    public void setScoreThreshold(Float scoreThreshold)
    {
        this.scoreThreshold = scoreThreshold;
    }

    public Float getNmsThreshold()
    {
        return nmsThreshold;
    }

    public void setNmsThreshold(Float nmsThreshold)
    {
        this.nmsThreshold = nmsThreshold;
    }

    public List<String> getTargetCodes()
    {
        if ((targetCodes == null || targetCodes.isEmpty()) && targetCodesText != null)
        {
            targetCodes = parseTargetCodes(targetCodesText);
        }
        return targetCodes == null ? new ArrayList<String>() : targetCodes;
    }

    public void setTargetCodes(List<String> targetCodes)
    {
        this.targetCodes = normalizeTargetCodes(targetCodes);
        this.targetCodesText = joinTargetCodes(this.targetCodes);
    }

    @JsonIgnore
    public String getTargetCodesText()
    {
        if ((targetCodesText == null || targetCodesText.isEmpty()) && targetCodes != null && !targetCodes.isEmpty())
        {
            targetCodesText = joinTargetCodes(targetCodes);
        }
        return targetCodesText;
    }

    public void setTargetCodesText(String targetCodesText)
    {
        this.targetCodesText = joinTargetCodes(parseTargetCodes(targetCodesText));
        this.targetCodes = parseTargetCodes(this.targetCodesText);
    }

    @JsonIgnore
    public String getPrimaryTargetCode()
    {
        List<String> values = getTargetCodes();
        return values.isEmpty() ? "" : values.get(0);
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    private static List<String> normalizeTargetCodes(List<String> source)
    {
        List<String> normalized = new ArrayList<>();
        if (source == null || source.isEmpty())
        {
            return normalized;
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String item : source)
        {
            String value = normalizeTargetCode(item);
            if (value.isEmpty() || !seen.add(value))
            {
                continue;
            }
            normalized.add(value);
        }
        return normalized;
    }

    private static List<String> parseTargetCodes(String targetCodesText)
    {
        List<String> values = new ArrayList<>();
        if (targetCodesText == null || targetCodesText.trim().isEmpty())
        {
            return values;
        }

        String[] parts = targetCodesText.split(",");
        for (String part : parts)
        {
            String value = normalizeTargetCode(part);
            if (!value.isEmpty())
            {
                values.add(value);
            }
        }
        return normalizeTargetCodes(values);
    }

    private static String joinTargetCodes(List<String> targetCodes)
    {
        List<String> normalized = normalizeTargetCodes(targetCodes);
        if (normalized.isEmpty())
        {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String value : normalized)
        {
            if (builder.length() > 0)
            {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static String normalizeTargetCode(String value)
    {
        return value == null ? "" : value.trim().toLowerCase();
    }
}