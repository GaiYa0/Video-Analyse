package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.domain.DeploymentTask;
import com.ruoyi.system.domain.DeploymentTaskAlgorithm;
import com.ruoyi.system.mapper.DeploymentTaskAlgorithmMapper;
import com.ruoyi.system.mapper.DeploymentTaskMapper;
import com.ruoyi.system.service.IDeploymentTaskService;

/**
 * 布控任务服务实现
 */
@Service
public class DeploymentTaskServiceImpl implements IDeploymentTaskService
{
    @Autowired
    private DeploymentTaskMapper deploymentTaskMapper;

    @Autowired
    private DeploymentTaskAlgorithmMapper deploymentTaskAlgorithmMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDeploymentTask(DeploymentTask deploymentTask)
    {
        int rows = deploymentTaskMapper.insertDeploymentTask(deploymentTask);
        if (rows > 0)
        {
            saveDeploymentTaskAlgorithms(deploymentTask);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDeploymentTask(DeploymentTask deploymentTask)
    {
        int rows = deploymentTaskMapper.updateDeploymentTask(deploymentTask);
        if (rows > 0)
        {
            deploymentTaskAlgorithmMapper.deleteByDeploymentId(deploymentTask.getDeploymentId());
            saveDeploymentTaskAlgorithms(deploymentTask);
        }
        return rows;
    }

    @Override
    public DeploymentTask selectDeploymentTaskById(String deploymentId)
    {
        DeploymentTask task = deploymentTaskMapper.selectDeploymentTaskById(deploymentId);
        if (task != null)
        {
            task.setAlgorithmTasks(deploymentTaskAlgorithmMapper.selectByDeploymentId(deploymentId));
        }
        return task;
    }

    @Override
    public List<DeploymentTask> selectDeploymentTaskList(String status, String taskName, String deploymentId)
    {
        DeploymentTask query = new DeploymentTask();
        query.setStatus(status);
        query.setTaskName(taskName);
        query.setDeploymentId(deploymentId);
        List<DeploymentTask> tasks = deploymentTaskMapper.selectDeploymentTaskList(query);
        attachAlgorithmTasks(tasks);
        return tasks;
    }

    @Override
    public int startDeploymentTask(String deploymentId)
    {
        Date now = new Date();
        return deploymentTaskMapper.updateDeploymentTaskStart(deploymentId, "RUNNING", now, now);
    }

    @Override
    public int stopDeploymentTask(String deploymentId)
    {
        Date now = new Date();
        return deploymentTaskMapper.updateDeploymentTaskStop(deploymentId, "STOPPED", now, now);
    }

    private void saveDeploymentTaskAlgorithms(DeploymentTask deploymentTask)
    {
        if (deploymentTask == null || deploymentTask.getAlgorithmTasks() == null || deploymentTask.getAlgorithmTasks().isEmpty())
        {
            return;
        }

        Date now = new Date();
        List<DeploymentTaskAlgorithm> algorithms = new ArrayList<>(deploymentTask.getAlgorithmTasks().size());
        for (int i = 0; i < deploymentTask.getAlgorithmTasks().size(); ++i)
        {
            DeploymentTaskAlgorithm source = deploymentTask.getAlgorithmTasks().get(i);
            if (source == null)
            {
                continue;
            }
            DeploymentTaskAlgorithm target = new DeploymentTaskAlgorithm();
            target.setDeploymentId(deploymentTask.getDeploymentId());
            target.setAlgorithmCode(source.getAlgorithmCode());
            target.setAlgorithmName(source.getAlgorithmName());
            target.setDetectFps(source.getDetectFps());
            target.setScoreThreshold(source.getScoreThreshold());
            target.setNmsThreshold(source.getNmsThreshold());
            target.setTargetCodes(source.getTargetCodes());
            target.setSortOrder(source.getSortOrder() == null ? i : source.getSortOrder());
            target.setCreateTime(now);
            target.setUpdateTime(now);
            algorithms.add(target);
        }
        if (!algorithms.isEmpty())
        {
            deploymentTaskAlgorithmMapper.insertDeploymentTaskAlgorithms(algorithms);
            deploymentTask.setAlgorithmTasks(algorithms);
        }
    }

    private void attachAlgorithmTasks(List<DeploymentTask> tasks)
    {
        if (tasks == null || tasks.isEmpty())
        {
            return;
        }

        List<String> deploymentIds = new ArrayList<>(tasks.size());
        for (DeploymentTask task : tasks)
        {
            if (task != null && task.getDeploymentId() != null)
            {
                deploymentIds.add(task.getDeploymentId());
            }
        }
        if (deploymentIds.isEmpty())
        {
            return;
        }

        List<DeploymentTaskAlgorithm> algorithms = deploymentTaskAlgorithmMapper.selectByDeploymentIds(deploymentIds);
        Map<String, List<DeploymentTaskAlgorithm>> grouped = new LinkedHashMap<>();
        for (DeploymentTaskAlgorithm algorithm : algorithms)
        {
            grouped.computeIfAbsent(algorithm.getDeploymentId(), key -> new ArrayList<>()).add(algorithm);
        }
        for (DeploymentTask task : tasks)
        {
            if (task == null)
            {
                continue;
            }
            List<DeploymentTaskAlgorithm> values = grouped.get(task.getDeploymentId());
            task.setAlgorithmTasks(values == null ? new ArrayList<DeploymentTaskAlgorithm>() : values);
        }
    }
}
