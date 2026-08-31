package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.DeploymentTask;

/**
 * 布控任务 Mapper
 */
public interface DeploymentTaskMapper
{
    public int insertDeploymentTask(DeploymentTask deploymentTask);

    public int updateDeploymentTask(DeploymentTask deploymentTask);

    public DeploymentTask selectDeploymentTaskById(String deploymentId);

    public List<DeploymentTask> selectDeploymentTaskList(DeploymentTask deploymentTask);

    public int updateDeploymentTaskStart(@Param("deploymentId") String deploymentId,
        @Param("status") String status,
        @Param("startTime") Date startTime,
        @Param("updateTime") Date updateTime);

    public int updateDeploymentTaskStop(@Param("deploymentId") String deploymentId,
        @Param("status") String status,
        @Param("stopTime") Date stopTime,
        @Param("updateTime") Date updateTime);
}
