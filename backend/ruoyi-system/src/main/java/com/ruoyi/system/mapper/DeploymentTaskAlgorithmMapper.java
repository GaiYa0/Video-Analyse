package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DeploymentTaskAlgorithm;

/**
 * 布控任务算法配置 Mapper
 */
public interface DeploymentTaskAlgorithmMapper
{
    public List<DeploymentTaskAlgorithm> selectByDeploymentId(String deploymentId);

    public List<DeploymentTaskAlgorithm> selectByDeploymentIds(@Param("deploymentIds") List<String> deploymentIds);

    public int insertDeploymentTaskAlgorithms(@Param("list") List<DeploymentTaskAlgorithm> algorithms);

    public int deleteByDeploymentId(String deploymentId);
}