package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DeploymentTaskEvent;

/**
 * 布控任务业务事件实例 Mapper
 */
public interface DeploymentTaskEventMapper
{
    public DeploymentTaskEvent selectById(Long id);

    public List<DeploymentTaskEvent> selectByDeploymentId(String deploymentId);

    public List<DeploymentTaskEvent> selectByDeploymentIds(@Param("deploymentIds") List<String> deploymentIds);

    public int insertDeploymentTaskEvents(@Param("list") List<DeploymentTaskEvent> events);

    public int updateDeploymentTaskEvent(DeploymentTaskEvent event);

    public int deleteById(Long id);

    public int deleteByDeploymentId(String deploymentId);
}