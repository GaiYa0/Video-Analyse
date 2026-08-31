package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.DeploymentTask;

/**
 * 布控任务服务接口
 */
public interface IDeploymentTaskService
{
    public int insertDeploymentTask(DeploymentTask deploymentTask);

    public int updateDeploymentTask(DeploymentTask deploymentTask);

    public DeploymentTask selectDeploymentTaskById(String deploymentId);

    public List<DeploymentTask> selectDeploymentTaskList(String status, String taskName, String deploymentId);

    public int startDeploymentTask(String deploymentId);

    public int stopDeploymentTask(String deploymentId);
}
