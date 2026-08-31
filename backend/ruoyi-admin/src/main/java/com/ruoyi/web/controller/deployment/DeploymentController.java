package com.ruoyi.web.controller.deployment;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.DeploymentTask;
import com.ruoyi.system.domain.DeploymentTaskAlgorithm;
import com.ruoyi.system.domain.DeploymentTaskEvent;
import com.ruoyi.system.mapper.DeploymentTaskEventMapper;
import com.ruoyi.system.service.IDeploymentTaskService;
import com.ruoyi.web.service.deployment.DeploymentAnalyzerClient;
import com.ruoyi.waring.service.HAlgorithmService;

@RestController
@RequestMapping("/deployments")
public class DeploymentController
{
    private static final String ENGINE_A_SERVER = "A-SERVER";
    private static final String ENGINE_M_SERVER = "M-SERVER";
    private static final int DEFAULT_ALARM_INTERVAL_SEC = 180;
    private static final long DEFAULT_DWELL_THRESHOLD_MS = 5000L;
    private static final long MAX_DWELL_THRESHOLD_MS = 3600000L;
    private static final int DEPLOYMENT_ID_RANDOM_LEN = 14;
    private static final String DEPLOYMENT_ID_PREFIX = "control";
    private static final String EVENT_ORCHESTRATION_KEY_PREFIX = "event_orchestration::";
    private static final int EVENT_ORCHESTRATION_NAME_MAX_LEN = 64;
    private static final int EVENT_ORCHESTRATION_OUTPUT_ALARM_NAME_MAX_LEN = 64;
    private static final long EVENT_ORCHESTRATION_TIME_WINDOW_MIN = 1L;
    private static final long EVENT_ORCHESTRATION_TIME_WINDOW_MAX = 600000L;
    private static final long EVENT_ORCHESTRATION_COOLDOWN_MIN = 0L;
    private static final long EVENT_ORCHESTRATION_COOLDOWN_MAX = 3600000L;
    private static final long EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW = 30000L;
    private static final long EVENT_ORCHESTRATION_DEFAULT_COOLDOWN = 30000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private IDeploymentTaskService deploymentTaskService;

    @Autowired
    private DeploymentTaskEventMapper deploymentTaskEventMapper;

    @Autowired
    private DeploymentAnalyzerClient deploymentAnalyzerClient;

    @Autowired
    private HAlgorithmService hAlgorithmService;

    @PostMapping
    public AjaxResult create(@RequestBody CreateDeploymentRequest request)
    {
        if (request == null || StringUtils.isEmpty(request.getTaskName()))
        {
            return AjaxResult.error("taskName不能为空");
        }
        if (StringUtils.isEmpty(request.getDeviceId()))
        {
            return AjaxResult.error("deviceId不能为空");
        }

        List<DeploymentTaskAlgorithm> algorithmTasks = normalizeAlgorithmTasks(request);
        if (algorithmTasks.isEmpty())
        {
            return AjaxResult.error("至少需要配置一个算法及检测目标");
        }

        String recordEngine = normalizeRecordEngine(request.getRecordEngine());
        if (recordEngine == null)
        {
            return AjaxResult.error("recordEngine仅支持算法服务器或媒体服务器");
        }

        Integer alarmIntervalSec = request.getAlarmIntervalSec();
        if (alarmIntervalSec == null || alarmIntervalSec <= 0)
        {
            alarmIntervalSec = DEFAULT_ALARM_INTERVAL_SEC;
        }
        boolean pushEnabled = Boolean.TRUE.equals(request.getPushEnabled());
        boolean frontendOverlayEnabled = normalizeFrontendOverlayEnabled(pushEnabled, request.getFrontendOverlayEnabled());
        boolean dwellEnabled = normalizeDwellEnabled(request.getDwellEnabled());
        long dwellThresholdMs = normalizeDwellThresholdMs(dwellEnabled, request.getDwellThresholdMs());
        boolean aiReviewEnabled = normalizeAiReviewEnabled(request.getAiReviewEnabled());
        String geometryConfig = normalizeGeometryConfig(request.getGeometryConfig());

        Date now = new Date();
        String deploymentId = generateDeploymentId();
        String streamUrl = deploymentAnalyzerClient.buildStreamUrl(request.getDeviceId());
        if (StringUtils.isEmpty(streamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成streamUrl");
        }
        String pushStreamUrl = pushEnabled
            ? deploymentAnalyzerClient.buildPushStreamUrl(request.getDeviceId(), deploymentId)
            : null;
        if (pushEnabled && StringUtils.isEmpty(pushStreamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成pushStreamUrl");
        }
        String algorithmStreamUrl = pushEnabled
            ? deploymentAnalyzerClient.buildAlgorithmStreamUrl(request.getDeviceId(), deploymentId)
            : null;
        if (pushEnabled && StringUtils.isEmpty(algorithmStreamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成algorithmStreamUrl");
        }

        DeploymentTask task = new DeploymentTask();
        task.setDeploymentId(deploymentId);
        task.setTaskName(request.getTaskName());
        task.setDeviceId(request.getDeviceId());
        applyPrimaryAlgorithmFields(task, algorithmTasks);
        task.setAlgorithmTasks(algorithmTasks);
        task.setPushEnabled(pushEnabled);
        task.setFrontendOverlayEnabled(frontendOverlayEnabled);
        task.setRecordEngine(recordEngine);
        task.setAlarmIntervalSec(alarmIntervalSec);
        task.setDwellEnabled(dwellEnabled);
        task.setDwellThresholdMs(dwellThresholdMs);
        task.setAiReviewEnabled(aiReviewEnabled);
        task.setAiReviewPrompt(request.getAiReviewPrompt());
        task.setRemark(request.getRemark());
        task.setGeometryConfig(geometryConfig);
        task.setStreamUrl(streamUrl);
        task.setPushStreamUrl(pushStreamUrl);
        task.setAlgorithmStreamUrl(algorithmStreamUrl);
        task.setStatus("CREATED");
        task.setCreateTime(now);
        task.setUpdateTime(now);

        int rows = deploymentTaskService.insertDeploymentTask(task);
        if (rows <= 0)
        {
            return AjaxResult.error("布控任务创建失败");
        }

        DeploymentTask saved = deploymentTaskService.selectDeploymentTaskById(task.getDeploymentId());
        return AjaxResult.success(toDataMap(saved));
    }

    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable("id") String id, @RequestBody CreateDeploymentRequest request)
    {
        DeploymentTask record = deploymentTaskService.selectDeploymentTaskById(id);
        if (record == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        if (request == null || StringUtils.isEmpty(request.getTaskName()))
        {
            return AjaxResult.error("taskName不能为空");
        }
        if (StringUtils.isEmpty(request.getDeviceId()))
        {
            return AjaxResult.error("deviceId不能为空");
        }

        List<DeploymentTaskAlgorithm> algorithmTasks = normalizeAlgorithmTasks(request);
        if (algorithmTasks.isEmpty())
        {
            return AjaxResult.error("至少需要配置一个算法及检测目标");
        }

        String recordEngine = normalizeRecordEngine(request.getRecordEngine());
        if (recordEngine == null)
        {
            return AjaxResult.error("recordEngine仅支持算法服务器或媒体服务器");
        }

        Integer alarmIntervalSec = request.getAlarmIntervalSec();
        if (alarmIntervalSec == null || alarmIntervalSec <= 0)
        {
            alarmIntervalSec = DEFAULT_ALARM_INTERVAL_SEC;
        }
        boolean pushEnabled = Boolean.TRUE.equals(request.getPushEnabled());
        boolean frontendOverlayEnabled = normalizeFrontendOverlayEnabled(pushEnabled, request.getFrontendOverlayEnabled());
        boolean dwellEnabled = normalizeDwellEnabled(request.getDwellEnabled());
        long dwellThresholdMs = normalizeDwellThresholdMs(dwellEnabled, request.getDwellThresholdMs());
        boolean aiReviewEnabled = normalizeAiReviewEnabled(request.getAiReviewEnabled());
        String geometryConfig = normalizeGeometryConfig(request.getGeometryConfig());

        String streamUrl = deploymentAnalyzerClient.buildStreamUrl(request.getDeviceId());
        if (StringUtils.isEmpty(streamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成streamUrl");
        }
        String pushStreamUrl = pushEnabled
            ? deploymentAnalyzerClient.buildPushStreamUrl(request.getDeviceId(), id)
            : null;
        if (pushEnabled && StringUtils.isEmpty(pushStreamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成pushStreamUrl");
        }
        String algorithmStreamUrl = pushEnabled
            ? deploymentAnalyzerClient.buildAlgorithmStreamUrl(request.getDeviceId(), id)
            : null;
        if (pushEnabled && StringUtils.isEmpty(algorithmStreamUrl))
        {
            return AjaxResult.error("未绑定可用ZLM/SVA服务器或配置缺失，无法生成algorithmStreamUrl");
        }

        DeploymentTask task = new DeploymentTask();
        task.setDeploymentId(id);
        task.setTaskName(request.getTaskName());
        task.setDeviceId(request.getDeviceId());
        applyPrimaryAlgorithmFields(task, algorithmTasks);
        task.setAlgorithmTasks(algorithmTasks);
        task.setPushEnabled(pushEnabled);
        task.setFrontendOverlayEnabled(frontendOverlayEnabled);
        task.setRecordEngine(recordEngine);
        task.setAlarmIntervalSec(alarmIntervalSec);
        task.setDwellEnabled(dwellEnabled);
        task.setDwellThresholdMs(dwellThresholdMs);
        task.setAiReviewEnabled(aiReviewEnabled);
        task.setAiReviewPrompt(request.getAiReviewPrompt());
        task.setRemark(request.getRemark());
        task.setGeometryConfig(geometryConfig);
        task.setStreamUrl(streamUrl);
        task.setPushStreamUrl(pushStreamUrl);
        task.setAlgorithmStreamUrl(algorithmStreamUrl);
        task.setUpdateTime(new Date());

        int rows = deploymentTaskService.updateDeploymentTask(task);
        if (rows <= 0)
        {
            return AjaxResult.error("布控任务更新失败");
        }

        return AjaxResult.success(toDataMap(deploymentTaskService.selectDeploymentTaskById(id)));
    }

    @PostMapping("/{id}/start")
    public AjaxResult start(@PathVariable("id") String id)
    {
        DeploymentTask record = deploymentTaskService.selectDeploymentTaskById(id);
        if (record == null)
        {
            return buildActionResult(false, "启动", "布控任务不存在", "布控任务不存在", null);
        }

        if (StringUtils.isEmpty(record.getDeviceId()))
        {
            return buildActionResult(false, "启动", "deviceId不能为空", "deviceId不能为空", record);
        }
        if (StringUtils.isEmpty(record.getStreamUrl()))
        {
            return buildActionResult(false, "启动", "streamUrl不能为空", "streamUrl不能为空", record);
        }
        if (Boolean.TRUE.equals(record.getPushEnabled()) && StringUtils.isEmpty(record.getPushStreamUrl()))
        {
            return buildActionResult(false, "启动", "已启用推流但pushStreamUrl为空", "已启用推流但pushStreamUrl为空", record);
        }
        if (Boolean.TRUE.equals(record.getPushEnabled()) && StringUtils.isEmpty(record.getAlgorithmStreamUrl()))
        {
            return buildActionResult(false, "启动", "已启用推流但algorithmStreamUrl为空", "已启用推流但algorithmStreamUrl为空", record);
        }

        List<Point> points = extractPrimaryRegionPoints(record.getGeometryConfig());
        if (points == null || points.size() < 3)
        {
            return buildActionResult(false, "启动", "geometryConfig中至少需要一个3点以上的主区域", "geometryConfig中至少需要一个3点以上的主区域", record);
        }

        String recognitionRegion = toRecognitionRegion(points);
        if (StringUtils.isEmpty(recognitionRegion))
        {
            return buildActionResult(false, "启动", "geometryConfig主区域格式不正确", "geometryConfig主区域格式不正确", record);
        }

        DeploymentAnalyzerClient.AnalyzerResult analyzerResult =
            deploymentAnalyzerClient.addControl(record, recognitionRegion);
        if (!analyzerResult.isSuccess())
        {
            return buildActionResult(false, "启动", analyzerResult.getMessage(), analyzerResult.getDetailMessage(), record);
        }

        int rows = deploymentTaskService.startDeploymentTask(id);
        if (rows <= 0)
        {
            DeploymentTask current = deploymentTaskService.selectDeploymentTaskById(id);
            if (current == null)
            {
                current = record;
            }
            return buildActionResult(false, "启动", "状态更新失败", "状态更新失败", current);
        }

        DeploymentTask latest = deploymentTaskService.selectDeploymentTaskById(id);
        if (latest == null)
        {
            latest = record;
        }
        return buildActionResult(true, "启动", analyzerResult.getMessage(), analyzerResult.getDetailMessage(), latest);
    }

    @PostMapping("/{id}/stop")
    public AjaxResult stop(@PathVariable("id") String id)
    {
        DeploymentTask record = deploymentTaskService.selectDeploymentTaskById(id);
        if (record == null)
        {
            return buildActionResult(false, "停止", "布控任务不存在", "布控任务不存在", null);
        }

        DeploymentAnalyzerClient.AnalyzerResult analyzerResult =
            deploymentAnalyzerClient.cancelControl(record);
        if (!analyzerResult.isSuccess())
        {
            return buildActionResult(false, "停止", analyzerResult.getMessage(), analyzerResult.getDetailMessage(), record);
        }

        int rows = deploymentTaskService.stopDeploymentTask(id);
        if (rows <= 0)
        {
            DeploymentTask current = deploymentTaskService.selectDeploymentTaskById(id);
            if (current == null)
            {
                current = record;
            }
            return buildActionResult(false, "停止", "状态更新失败", "状态更新失败", current);
        }

        DeploymentTask latest = deploymentTaskService.selectDeploymentTaskById(id);
        if (latest == null)
        {
            latest = record;
        }
        return buildActionResult(true, "停止", analyzerResult.getMessage(), analyzerResult.getDetailMessage(), latest);
    }

    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable("id") String id)
    {
        DeploymentTask record = deploymentTaskService.selectDeploymentTaskById(id);
        if (record == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        return AjaxResult.success(toDataMap(record));
    }

    @GetMapping
    public AjaxResult list(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "taskName", required = false) String taskName,
        @RequestParam(value = "deploymentId", required = false) String deploymentId)
    {
        List<DeploymentTask> records = deploymentTaskService.selectDeploymentTaskList(status, taskName, deploymentId);
        if (records == null)
        {
            records = new ArrayList<>();
        }

        List<Map<String, Object>> dataList = new ArrayList<>(records.size());
        for (DeploymentTask record : records)
        {
            dataList.add(toDataMap(record));
        }
        return AjaxResult.success(dataList);
    }

    @GetMapping("/{id}/event-pool")
    public AjaxResult listEventPool(@PathVariable("id") String id)
    {
        DeploymentTask task = deploymentTaskService.selectDeploymentTaskById(id);
        if (task == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        JsonNode geometryConfigNode = parseGeometryConfigText(task.getGeometryConfig());
        return AjaxResult.success(buildEventPoolList(geometryConfigNode));
    }

    @GetMapping("/{id}/event-orchestrations")
    public AjaxResult listEventOrchestrations(@PathVariable("id") String id)
    {
        DeploymentTask task = deploymentTaskService.selectDeploymentTaskById(id);
        if (task == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        List<DeploymentTaskEvent> records = deploymentTaskEventMapper.selectByDeploymentId(id);
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (DeploymentTaskEvent record : records)
        {
            if (!isEventOrchestrationRecord(record))
            {
                continue;
            }
            dataList.add(toEventOrchestrationMap(record));
        }
        return AjaxResult.success(dataList);
    }

    @PostMapping("/{id}/event-orchestrations")
    public AjaxResult createEventOrchestration(@PathVariable("id") String id,
        @RequestBody EventOrchestrationRequest request)
    {
        DeploymentTask task = deploymentTaskService.selectDeploymentTaskById(id);
        if (task == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        JsonNode geometryConfigNode = parseGeometryConfigText(task.getGeometryConfig());
        EventOrchestrationRequest normalizedRequest = normalizeEventOrchestrationRequest(request);
        String validateMessage = validateEventOrchestrationRequest(normalizedRequest, geometryConfigNode);
        if (!StringUtils.isEmpty(validateMessage))
        {
            return AjaxResult.error(validateMessage);
        }

        Date now = new Date();
        String eventKey = EVENT_ORCHESTRATION_KEY_PREFIX + randomAlphaNumeric(16);
        DeploymentTaskEvent event = new DeploymentTaskEvent();
        event.setDeploymentId(id);
        event.setEventKey(eventKey);
        event.setEventName(normalizedRequest.getName());
        event.setEnabled(Boolean.TRUE.equals(normalizedRequest.getEnabled()));
        event.setSortOrder(resolveNextOrchestrationSortOrder(id));
        event.setParameterValuesJson(buildOrchestrationParameterValuesJson(normalizedRequest));
        event.setCompiledRuleIdsJson(null);
        event.setCompiledRuleSnapshotJson(null);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        int rows = deploymentTaskEventMapper.insertDeploymentTaskEvents(singletonEventList(event));
        if (rows <= 0)
        {
            return AjaxResult.error("事件编排创建失败");
        }

        List<DeploymentTaskEvent> records = deploymentTaskEventMapper.selectByDeploymentId(id);
        for (DeploymentTaskEvent record : records)
        {
            if (record != null && StringUtils.equals(eventKey, record.getEventKey()))
            {
                return AjaxResult.success(toEventOrchestrationMap(record));
            }
        }
        return AjaxResult.success();
    }

    @PutMapping("/{id}/event-orchestrations/{orchestrationId}")
    public AjaxResult updateEventOrchestration(@PathVariable("id") String id,
        @PathVariable("orchestrationId") String orchestrationId,
        @RequestBody EventOrchestrationRequest request)
    {
        DeploymentTask task = deploymentTaskService.selectDeploymentTaskById(id);
        if (task == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        Long targetId = parseLongId(orchestrationId);
        if (targetId == null || targetId <= 0L)
        {
            return AjaxResult.error("orchestrationId不合法");
        }
        DeploymentTaskEvent existing = deploymentTaskEventMapper.selectById(targetId);
        if (existing == null || !StringUtils.equals(id, existing.getDeploymentId()) || !isEventOrchestrationRecord(existing))
        {
            return AjaxResult.error("事件编排不存在");
        }

        JsonNode geometryConfigNode = parseGeometryConfigText(task.getGeometryConfig());
        EventOrchestrationRequest normalizedRequest = normalizeEventOrchestrationRequest(request);
        String validateMessage = validateEventOrchestrationRequest(normalizedRequest, geometryConfigNode);
        if (!StringUtils.isEmpty(validateMessage))
        {
            return AjaxResult.error(validateMessage);
        }

        existing.setEventName(normalizedRequest.getName());
        existing.setEnabled(Boolean.TRUE.equals(normalizedRequest.getEnabled()));
        existing.setParameterValuesJson(buildOrchestrationParameterValuesJson(normalizedRequest));
        existing.setUpdateTime(new Date());
        int rows = deploymentTaskEventMapper.updateDeploymentTaskEvent(existing);
        if (rows <= 0)
        {
            return AjaxResult.error("事件编排更新失败");
        }
        return AjaxResult.success(toEventOrchestrationMap(deploymentTaskEventMapper.selectById(targetId)));
    }

    @DeleteMapping("/{id}/event-orchestrations/{orchestrationId}")
    public AjaxResult deleteEventOrchestration(@PathVariable("id") String id,
        @PathVariable("orchestrationId") String orchestrationId)
    {
        DeploymentTask task = deploymentTaskService.selectDeploymentTaskById(id);
        if (task == null)
        {
            return AjaxResult.error("布控任务不存在");
        }
        Long targetId = parseLongId(orchestrationId);
        if (targetId == null || targetId <= 0L)
        {
            return AjaxResult.error("orchestrationId不合法");
        }
        DeploymentTaskEvent existing = deploymentTaskEventMapper.selectById(targetId);
        if (existing == null || !StringUtils.equals(id, existing.getDeploymentId()) || !isEventOrchestrationRecord(existing))
        {
            return AjaxResult.error("事件编排不存在");
        }
        int rows = deploymentTaskEventMapper.deleteById(targetId);
        if (rows <= 0)
        {
            return AjaxResult.error("事件编排删除失败");
        }
        return AjaxResult.success();
    }

    private String normalizeRecordEngine(String recordEngine)
    {
        if (StringUtils.isEmpty(recordEngine))
        {
            return ENGINE_M_SERVER;
        }
        if (Objects.equals(ENGINE_A_SERVER, recordEngine) || Objects.equals(ENGINE_M_SERVER, recordEngine))
        {
            return recordEngine;
        }
        return null;
    }

    private boolean normalizeFrontendOverlayEnabled(Boolean pushEnabled, Boolean frontendOverlayEnabled)
    {
        if (Boolean.TRUE.equals(pushEnabled))
        {
            return false;
        }
        return frontendOverlayEnabled == null || frontendOverlayEnabled;
    }

    private boolean normalizeAiReviewEnabled(Boolean aiReviewEnabled)
    {
        return Boolean.TRUE.equals(aiReviewEnabled);
    }

    private boolean normalizeDwellEnabled(Boolean dwellEnabled)
    {
        return dwellEnabled != null && dwellEnabled;
    }

    private long normalizeDwellThresholdMs(boolean dwellEnabled, Long dwellThresholdMs)
    {
        long normalized = dwellThresholdMs == null ? DEFAULT_DWELL_THRESHOLD_MS : dwellThresholdMs;
        if (normalized < 0L)
        {
            normalized = 0L;
        }
        if (normalized > MAX_DWELL_THRESHOLD_MS)
        {
            normalized = MAX_DWELL_THRESHOLD_MS;
        }
        if (dwellEnabled && normalized <= 0L)
        {
            normalized = DEFAULT_DWELL_THRESHOLD_MS;
        }
        return normalized;
    }

    private String generateDeploymentId()
    {
        String id;
        do
        {
            id = DEPLOYMENT_ID_PREFIX + randomAlphaNumeric(DEPLOYMENT_ID_RANDOM_LEN);
        }
        while (deploymentTaskService.selectDeploymentTaskById(id) != null);
        return id;
    }

    private String randomAlphaNumeric(int len)
    {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder builder = new StringBuilder(len);
        for (int i = 0; i < len; i++)
        {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            builder.append(chars.charAt(index));
        }
        return builder.toString();
    }

    private void applyPrimaryAlgorithmFields(DeploymentTask task, List<DeploymentTaskAlgorithm> algorithmTasks)
    {
        if (task == null || algorithmTasks == null || algorithmTasks.isEmpty())
        {
            return;
        }
        DeploymentTaskAlgorithm first = algorithmTasks.get(0);
        task.setAlgorithmCode(first.getAlgorithmCode());
        task.setAlgorithmName(first.getAlgorithmName());
        task.setTargetCode(first.getPrimaryTargetCode());
    }

    private List<DeploymentTaskAlgorithm> normalizeAlgorithmTasks(CreateDeploymentRequest request)
    {
        List<DeploymentTaskAlgorithm> normalized = new ArrayList<>();
        if (request == null)
        {
            return normalized;
        }

        List<AlgorithmTaskRequest> requestTasks = request.getAlgorithmTasks();
        if (requestTasks == null || requestTasks.isEmpty())
        {
            return normalized;
        }

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < requestTasks.size(); ++i)
        {
            AlgorithmTaskRequest item = requestTasks.get(i);
            if (item == null)
            {
                continue;
            }
            String algorithmCode = StringUtils.trimToEmpty(item.getAlgorithmCode());
            List<String> targetCodes = normalizeTargetCodes(item.getTargetCodes());
            if (StringUtils.isBlank(algorithmCode) || targetCodes.isEmpty())
            {
                return new ArrayList<>();
            }
            if (!seen.add(algorithmCode))
            {
                return new ArrayList<>();
            }

            DeploymentTaskAlgorithm algorithm = new DeploymentTaskAlgorithm();
            algorithm.setAlgorithmCode(algorithmCode);
            algorithm.setDetectFps(normalizeDetectFps(item.getDetectFps()));
            algorithm.setScoreThreshold(normalizeThreshold(item.getScoreThreshold()));
            algorithm.setNmsThreshold(normalizeThreshold(item.getNmsThreshold()));
            algorithm.setTargetCodes(targetCodes);
            algorithm.setSortOrder(i);

            String algorithmName = StringUtils.trimToEmpty(item.getAlgorithmName());
            if (StringUtils.isEmpty(algorithmName))
            {
                algorithmName = StringUtils.nvl(hAlgorithmService.getNameByCode(algorithmCode), algorithmCode);
            }
            algorithm.setAlgorithmName(algorithmName);
            normalized.add(algorithm);
        }
        return normalized;
    }

    private Float normalizeDetectFps(Float detectFps)
    {
        if (detectFps == null)
        {
            return 8.0f;
        }
        float value = detectFps.floatValue();
        if (!Float.isFinite(value))
        {
            return 8.0f;
        }
        if (value < 0.0f)
        {
            value = 0.0f;
        }
        if (value > 30.0f)
        {
            value = 30.0f;
        }
        return value;
    }

    private Float normalizeThreshold(Float threshold)
    {
        if (threshold == null)
        {
            return null;
        }
        float value = threshold.floatValue();
        if (!Float.isFinite(value))
        {
            return null;
        }
        if (value < 0.0f)
        {
            value = 0.0f;
        }
        if (value > 1.0f)
        {
            value = 1.0f;
        }
        return value;
    }

    private List<String> normalizeTargetCodes(List<String> targetCodes)
    {
        List<String> normalized = new ArrayList<>();
        if (targetCodes == null || targetCodes.isEmpty())
        {
            return normalized;
        }

        Set<String> seen = new HashSet<>();
        for (String value : targetCodes)
        {
            String normalizedValue = StringUtils.trimToEmpty(value).toLowerCase();
            if (StringUtils.isEmpty(normalizedValue) || !seen.add(normalizedValue))
            {
                continue;
            }
            normalized.add(normalizedValue);
        }
        return normalized;
    }

    private String normalizeGeometryConfig(JsonNode geometryConfigNode)
    {
        JsonNode normalizedNode = normalizeGeometryConfigNode(geometryConfigNode);
        if (normalizedNode == null || normalizedNode.isNull())
        {
            return null;
        }
        try
        {
            return OBJECT_MAPPER.writeValueAsString(normalizedNode);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private JsonNode normalizeGeometryConfigNode(JsonNode geometryConfigNode)
    {
        JsonNode parsedNode = parseGeometryConfigNode(geometryConfigNode);
        ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
        ArrayNode regionsNode = OBJECT_MAPPER.createArrayNode();
        ArrayNode linesNode = OBJECT_MAPPER.createArrayNode();
        ArrayNode behaviorRulesNode = OBJECT_MAPPER.createArrayNode();

        if (parsedNode != null)
        {
            if (parsedNode.isObject())
            {
                JsonNode sourceRegions = parsedNode.get("regions");
                if (sourceRegions != null && sourceRegions.isArray())
                {
                    regionsNode.addAll((ArrayNode) sourceRegions.deepCopy());
                }
                JsonNode sourceLines = parsedNode.get("lines");
                if (sourceLines != null && sourceLines.isArray())
                {
                    linesNode.addAll((ArrayNode) sourceLines.deepCopy());
                }
                JsonNode sourceBehaviorRules = parsedNode.get("behaviorRules");
                if (sourceBehaviorRules != null && sourceBehaviorRules.isArray())
                {
                    behaviorRulesNode.addAll((ArrayNode) sourceBehaviorRules.deepCopy());
                }
            }
            else if (parsedNode.isArray())
            {
                regionsNode.addAll((ArrayNode) parsedNode.deepCopy());
            }
        }

        if (regionsNode.isEmpty() && linesNode.isEmpty() && behaviorRulesNode.isEmpty())
        {
            return null;
        }

        normalized.set("regions", regionsNode);
        normalized.set("lines", linesNode);
        normalized.set("behaviorRules", behaviorRulesNode);
        return normalized;
    }

    private JsonNode parseGeometryConfigNode(JsonNode geometryConfigNode)
    {
        if (geometryConfigNode == null || geometryConfigNode.isNull())
        {
            return null;
        }
        if (geometryConfigNode.isObject() || geometryConfigNode.isArray())
        {
            return geometryConfigNode.deepCopy();
        }
        if (geometryConfigNode.isTextual())
        {
            return parseGeometryConfigText(geometryConfigNode.asText());
        }
        return null;
    }

    private JsonNode parseGeometryConfigText(String geometryConfig)
    {
        if (StringUtils.isEmpty(geometryConfig))
        {
            return null;
        }
        try
        {
            return OBJECT_MAPPER.readTree(geometryConfig);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private List<Point> extractPrimaryRegionPoints(String geometryConfig)
    {
        return extractPrimaryRegionPoints(parseGeometryConfigText(geometryConfig));
    }

    private List<Point> extractPrimaryRegionPoints(JsonNode geometryConfigNode)
    {
        if (geometryConfigNode == null || !geometryConfigNode.isObject())
        {
            return null;
        }

        JsonNode regionsNode = geometryConfigNode.get("regions");
        if (regionsNode == null || !regionsNode.isArray())
        {
            return null;
        }

        List<Point> fallback = null;
        for (JsonNode regionNode : regionsNode)
        {
            List<Point> points = parseRegionPoints(regionNode);
            if (points == null || points.size() < 3)
            {
                continue;
            }
            if (fallback == null)
            {
                fallback = points;
            }
            if (regionNode.path("primary").asBoolean(false) || regionNode.path("isPrimary").asBoolean(false))
            {
                return points;
            }
        }
        return fallback;
    }

    private List<Point> parseRegionPoints(JsonNode regionNode)
    {
        if (regionNode == null || regionNode.isNull())
        {
            return null;
        }
        JsonNode pointsNode = regionNode.get("points");
        if (pointsNode == null || pointsNode.isNull())
        {
            pointsNode = regionNode.get("polygon");
        }
        if (pointsNode == null || pointsNode.isNull())
        {
            pointsNode = regionNode.get("vertices");
        }
        return parsePointArray(pointsNode);
    }

    private List<Point> parsePointArray(JsonNode pointsNode)
    {
        if (pointsNode == null || !pointsNode.isArray() || pointsNode.size() == 0)
        {
            return null;
        }

        List<Point> points = new ArrayList<>();
        JsonNode first = pointsNode.get(0);
        if (first != null && (first.isObject() || first.isArray()))
        {
            for (JsonNode pointNode : pointsNode)
            {
                Point point = parsePoint(pointNode);
                if (point != null)
                {
                    points.add(point);
                }
            }
        }
        else
        {
            for (int i = 0; i + 1 < pointsNode.size(); i += 2)
            {
                Point point = new Point();
                point.setX(readDouble(pointsNode.get(i)));
                point.setY(readDouble(pointsNode.get(i + 1)));
                if (point.getX() != null && point.getY() != null)
                {
                    points.add(point);
                }
            }
        }

        return points.size() >= 3 ? points : null;
    }

    private Point parsePoint(JsonNode pointNode)
    {
        if (pointNode == null || pointNode.isNull())
        {
            return null;
        }
        Point point = new Point();
        if (pointNode.isObject())
        {
            point.setX(readDouble(pointNode.get("x")));
            point.setY(readDouble(pointNode.get("y")));
        }
        else if (pointNode.isArray() && pointNode.size() >= 2)
        {
            point.setX(readDouble(pointNode.get(0)));
            point.setY(readDouble(pointNode.get(1)));
        }
        if (point.getX() == null || point.getY() == null)
        {
            return null;
        }
        return point;
    }

    private Double readDouble(JsonNode valueNode)
    {
        if (valueNode == null || valueNode.isNull())
        {
            return null;
        }
        if (valueNode.isNumber())
        {
            return valueNode.asDouble();
        }
        if (valueNode.isTextual())
        {
            try
            {
                return Double.valueOf(valueNode.asText());
            }
            catch (Exception ex)
            {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> toDataMap(DeploymentTask record)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (record == null)
        {
            return data;
        }
        data.put("deploymentId", record.getDeploymentId());
        data.put("taskName", record.getTaskName());
        data.put("deviceId", record.getDeviceId());
        data.put("algorithmCode", record.getAlgorithmCode());
        data.put("algorithmName", record.getAlgorithmName());
        data.put("targetCode", record.getTargetCode());
        data.put("algorithmTasks", record.getAlgorithmTasks() == null ? new ArrayList<DeploymentTaskAlgorithm>() : record.getAlgorithmTasks());
        data.put("pushEnabled", Boolean.TRUE.equals(record.getPushEnabled()));
        data.put("frontendOverlayEnabled",
            normalizeFrontendOverlayEnabled(record.getPushEnabled(), record.getFrontendOverlayEnabled()));
        data.put("recordEngine", record.getRecordEngine());
        data.put("alarmIntervalSec", record.getAlarmIntervalSec());
        data.put("dwellEnabled", Boolean.TRUE.equals(record.getDwellEnabled()));
        data.put("dwellThresholdMs", record.getDwellThresholdMs());
        data.put("aiReviewEnabled", normalizeAiReviewEnabled(record.getAiReviewEnabled()));
        data.put("aiReviewPrompt", record.getAiReviewPrompt());
        data.put("remark", record.getRemark());
        data.put("geometryConfig", parseGeometryConfigText(record.getGeometryConfig()));
        data.put("streamUrl", record.getStreamUrl());
        data.put("pushStreamUrl", record.getPushStreamUrl());
        data.put("algorithmStreamUrl", record.getAlgorithmStreamUrl());
        data.put("status", record.getStatus());
        data.put("startTime", record.getStartTime());
        data.put("stopTime", record.getStopTime());
        data.put("createTime", record.getCreateTime());
        data.put("updateTime", record.getUpdateTime());
        return data;
    }

    private AjaxResult buildActionResult(boolean success, String action, String message, String detailMessage,
        DeploymentTask task)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        String defaultMessage = success ? action + "成功" : action + "失败";
        payload.put("shortMessage", StringUtils.isEmpty(message) ? defaultMessage : message);
        payload.put("detailMessage", StringUtils.isEmpty(detailMessage) ? StringUtils.nvl(message, "") : detailMessage);
        payload.put("data", task == null ? null : toDataMap(task));
        return AjaxResult.success(payload);
    }

    private String toRecognitionRegion(List<Point> points)
    {
        if (points == null)
        {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int validPointCount = 0;
        for (Point point : points)
        {
            if (point == null || point.getX() == null || point.getY() == null)
            {
                continue;
            }

            if (builder.length() > 0)
            {
                builder.append(',');
            }
            builder.append(point.getX()).append(',').append(point.getY());
            validPointCount++;
        }

        if (validPointCount < 3)
        {
            return null;
        }
        return builder.toString();
    }

    private List<Map<String, Object>> buildEventPoolList(JsonNode geometryConfigNode)
    {
        List<Map<String, Object>> pool = new ArrayList<>();
        if (geometryConfigNode == null || !geometryConfigNode.isObject())
        {
            return pool;
        }
        JsonNode behaviorRulesNode = geometryConfigNode.get("behaviorRules");
        if (behaviorRulesNode == null || !behaviorRulesNode.isArray())
        {
            return pool;
        }
        int fallbackIndex = 1;
        for (JsonNode ruleNode : behaviorRulesNode)
        {
            if (ruleNode == null || !ruleNode.isObject())
            {
                continue;
            }
            if (!ruleNode.path("enabled").asBoolean(true))
            {
                continue;
            }
            String outputMode = StringUtils.trimToEmpty(ruleNode.path("outputMode").asText());
            if (StringUtils.isEmpty(outputMode))
            {
                outputMode = StringUtils.trimToEmpty(ruleNode.path("output_mode").asText());
            }
            if (!StringUtils.equals("condition_only", outputMode))
            {
                continue;
            }
            String ruleId = StringUtils.trimToEmpty(ruleNode.path("id").asText());
            if (StringUtils.isEmpty(ruleId))
            {
                ruleId = "rule_" + fallbackIndex;
            }
            fallbackIndex++;

            String behaviorType = StringUtils.trimToEmpty(ruleNode.path("behaviorType").asText());
            if (StringUtils.isEmpty(behaviorType))
            {
                behaviorType = StringUtils.trimToEmpty(ruleNode.path("type").asText());
            }
            String eventName = StringUtils.trimToEmpty(ruleNode.path("customEventName").asText());
            if (StringUtils.isEmpty(eventName))
            {
                eventName = StringUtils.trimToEmpty(ruleNode.path("custom_event_name").asText());
            }
            if (StringUtils.isEmpty(eventName))
            {
                eventName = StringUtils.isEmpty(behaviorType) ? ruleId : behaviorType;
            }
            String eventKey = ruleId + ":" + eventName;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventKey", eventKey);
            item.put("displayName", eventName + " (" + ruleId + ")");
            item.put("eventName", eventName);
            item.put("ruleId", ruleId);
            item.put("behaviorType", behaviorType);
            item.put("geometryId", StringUtils.trimToEmpty(ruleNode.path("geometryId").asText()));
            pool.add(item);
        }
        return pool;
    }

    private boolean isEventOrchestrationRecord(DeploymentTaskEvent event)
    {
        return event != null && !StringUtils.isEmpty(event.getEventKey())
            && event.getEventKey().startsWith(EVENT_ORCHESTRATION_KEY_PREFIX);
    }

    private Map<String, Object> toEventOrchestrationMap(DeploymentTaskEvent event)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (event == null)
        {
            return data;
        }
        ObjectNode parameterValues = parseObjectNode(event.getParameterValuesJson());
        data.put("id", event.getId() == null ? "" : String.valueOf(event.getId()));
        data.put("name", StringUtils.nvl(event.getEventName(), ""));
        data.put("enabled", event.getEnabled() == null || event.getEnabled());
        data.put("logicMode", normalizeLogicMode(parameterValues.path("logicMode").asText()));
        data.put("timeWindowMs", normalizeRange(parameterValues.path("timeWindowMs").asLong(EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW),
            EVENT_ORCHESTRATION_TIME_WINDOW_MIN, EVENT_ORCHESTRATION_TIME_WINDOW_MAX, EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW));
        data.put("cooldownMs", normalizeRange(parameterValues.path("cooldownMs").asLong(EVENT_ORCHESTRATION_DEFAULT_COOLDOWN),
            EVENT_ORCHESTRATION_COOLDOWN_MIN, EVENT_ORCHESTRATION_COOLDOWN_MAX, EVENT_ORCHESTRATION_DEFAULT_COOLDOWN));
        data.put("outputAlarmName", StringUtils.trimToEmpty(parameterValues.path("outputAlarmName").asText()));
        data.put("conditionKeys", parseStringArray(parameterValues.path("conditionKeys")));
        return data;
    }

    private ObjectNode parseObjectNode(String json)
    {
        if (StringUtils.isEmpty(json))
        {
            return OBJECT_MAPPER.createObjectNode();
        }
        try
        {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node != null && node.isObject())
            {
                return (ObjectNode) node;
            }
        }
        catch (Exception ex)
        {
            // ignore invalid json and fallback to empty object
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private String buildOrchestrationParameterValuesJson(EventOrchestrationRequest request)
    {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("logicMode", normalizeLogicMode(request.getLogicMode()));
        node.put("timeWindowMs", normalizeRange(
            request.getTimeWindowMs() == null ? EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW : request.getTimeWindowMs(),
            EVENT_ORCHESTRATION_TIME_WINDOW_MIN,
            EVENT_ORCHESTRATION_TIME_WINDOW_MAX,
            EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW));
        node.put("cooldownMs", normalizeRange(
            request.getCooldownMs() == null ? EVENT_ORCHESTRATION_DEFAULT_COOLDOWN : request.getCooldownMs(),
            EVENT_ORCHESTRATION_COOLDOWN_MIN,
            EVENT_ORCHESTRATION_COOLDOWN_MAX,
            EVENT_ORCHESTRATION_DEFAULT_COOLDOWN));
        node.put("outputAlarmName", StringUtils.trimToEmpty(request.getOutputAlarmName()));
        ArrayNode conditionKeys = OBJECT_MAPPER.createArrayNode();
        for (String value : normalizeConditionKeys(request.getConditionKeys()))
        {
            conditionKeys.add(value);
        }
        node.set("conditionKeys", conditionKeys);
        try
        {
            return OBJECT_MAPPER.writeValueAsString(node);
        }
        catch (Exception ex)
        {
            return "{}";
        }
    }

    private EventOrchestrationRequest normalizeEventOrchestrationRequest(EventOrchestrationRequest request)
    {
        EventOrchestrationRequest normalized = request == null ? new EventOrchestrationRequest() : request;
        normalized.setName(StringUtils.trimToEmpty(normalized.getName()));
        normalized.setLogicMode(normalizeLogicMode(normalized.getLogicMode()));
        normalized.setOutputAlarmName(StringUtils.trimToEmpty(normalized.getOutputAlarmName()));
        normalized.setConditionKeys(normalizeConditionKeys(normalized.getConditionKeys()));
        if (normalized.getEnabled() == null)
        {
            normalized.setEnabled(Boolean.TRUE);
        }
        if (normalized.getTimeWindowMs() == null)
        {
            normalized.setTimeWindowMs(EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW);
        }
        if (normalized.getCooldownMs() == null)
        {
            normalized.setCooldownMs(EVENT_ORCHESTRATION_DEFAULT_COOLDOWN);
        }
        return normalized;
    }

    private String validateEventOrchestrationRequest(EventOrchestrationRequest request, JsonNode geometryConfigNode)
    {
        if (request == null)
        {
            return "请求体不能为空";
        }
        if (StringUtils.isEmpty(request.getName()))
        {
            return "name不能为空";
        }
        if (request.getName().length() > EVENT_ORCHESTRATION_NAME_MAX_LEN)
        {
            return "name长度不能超过64";
        }
        if (StringUtils.isEmpty(request.getOutputAlarmName()))
        {
            return "outputAlarmName不能为空";
        }
        if (request.getOutputAlarmName().length() > EVENT_ORCHESTRATION_OUTPUT_ALARM_NAME_MAX_LEN)
        {
            return "outputAlarmName长度不能超过64";
        }
        if (!StringUtils.equals("all", normalizeLogicMode(request.getLogicMode())))
        {
            return "logicMode当前仅支持all";
        }
        long timeWindowMs = request.getTimeWindowMs() == null ? EVENT_ORCHESTRATION_DEFAULT_TIME_WINDOW : request.getTimeWindowMs();
        if (timeWindowMs < EVENT_ORCHESTRATION_TIME_WINDOW_MIN || timeWindowMs > EVENT_ORCHESTRATION_TIME_WINDOW_MAX)
        {
            return "timeWindowMs范围应为1~600000";
        }
        long cooldownMs = request.getCooldownMs() == null ? EVENT_ORCHESTRATION_DEFAULT_COOLDOWN : request.getCooldownMs();
        if (cooldownMs < EVENT_ORCHESTRATION_COOLDOWN_MIN || cooldownMs > EVENT_ORCHESTRATION_COOLDOWN_MAX)
        {
            return "cooldownMs范围应为0~3600000";
        }

        List<String> conditionKeys = normalizeConditionKeys(request.getConditionKeys());
        if (conditionKeys.size() < 2)
        {
            return "conditionKeys至少需要2条";
        }
        Set<String> validKeySet = new HashSet<>();
        List<Map<String, Object>> eventPool = buildEventPoolList(geometryConfigNode);
        for (Map<String, Object> item : eventPool)
        {
            if (item == null)
            {
                continue;
            }
            String eventKey = StringUtils.trimToEmpty(String.valueOf(item.get("eventKey")));
            if (!StringUtils.isEmpty(eventKey))
            {
                validKeySet.add(eventKey);
            }
        }
        for (String key : conditionKeys)
        {
            if (!validKeySet.contains(key))
            {
                return "conditionKeys包含无效事件: " + key;
            }
        }
        return null;
    }

    private List<String> normalizeConditionKeys(List<String> conditionKeys)
    {
        List<String> normalized = new ArrayList<>();
        if (conditionKeys == null || conditionKeys.isEmpty())
        {
            return normalized;
        }
        Set<String> seen = new HashSet<>();
        for (String value : conditionKeys)
        {
            String normalizedValue = StringUtils.trimToEmpty(value);
            if (StringUtils.isEmpty(normalizedValue) || !seen.add(normalizedValue))
            {
                continue;
            }
            normalized.add(normalizedValue);
        }
        return normalized;
    }

    private List<String> parseStringArray(JsonNode arrayNode)
    {
        List<String> values = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray())
        {
            return values;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : arrayNode)
        {
            String value = StringUtils.trimToEmpty(item == null ? null : item.asText());
            if (StringUtils.isEmpty(value) || !seen.add(value))
            {
                continue;
            }
            values.add(value);
        }
        return values;
    }

    private String normalizeLogicMode(String logicMode)
    {
        return StringUtils.equals("all", StringUtils.trimToEmpty(logicMode)) ? "all" : "all";
    }

    private long normalizeRange(long value, long min, long max, long fallback)
    {
        long normalized = value;
        if (normalized < min || normalized > max)
        {
            normalized = fallback;
        }
        if (normalized < min)
        {
            return min;
        }
        if (normalized > max)
        {
            return max;
        }
        return normalized;
    }

    private int resolveNextOrchestrationSortOrder(String deploymentId)
    {
        List<DeploymentTaskEvent> records = deploymentTaskEventMapper.selectByDeploymentId(deploymentId);
        int maxSort = -1;
        for (DeploymentTaskEvent record : records)
        {
            if (!isEventOrchestrationRecord(record))
            {
                continue;
            }
            int currentSort = record != null && record.getSortOrder() != null ? record.getSortOrder() : 0;
            if (currentSort > maxSort)
            {
                maxSort = currentSort;
            }
        }
        return maxSort + 1;
    }

    private Long parseLongId(String value)
    {
        String text = StringUtils.trimToEmpty(value);
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        try
        {
            return Long.valueOf(text);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private List<DeploymentTaskEvent> singletonEventList(DeploymentTaskEvent event)
    {
        List<DeploymentTaskEvent> list = new ArrayList<>(1);
        list.add(event);
        return list;
    }

    public static class CreateDeploymentRequest
    {
        private String taskName;
        private String deviceId;
        private String algorithmCode;
        private String algorithmName;
        private List<AlgorithmTaskRequest> algorithmTasks;
        private Boolean pushEnabled;
        private Boolean frontendOverlayEnabled;
        private String recordEngine;
        private Integer alarmIntervalSec;
        private Boolean dwellEnabled;
        private Long dwellThresholdMs;
        private Boolean aiReviewEnabled;
        private String aiReviewPrompt;
        private String remark;
        private JsonNode geometryConfig;
        private String streamUrl;

        public String getTaskName()
        {
            return taskName;
        }

        public void setTaskName(String taskName)
        {
            this.taskName = taskName;
        }

        public String getDeviceId()
        {
            return deviceId;
        }

        public void setDeviceId(String deviceId)
        {
            this.deviceId = deviceId;
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

        public List<AlgorithmTaskRequest> getAlgorithmTasks()
        {
            return algorithmTasks;
        }

        public void setAlgorithmTasks(List<AlgorithmTaskRequest> algorithmTasks)
        {
            this.algorithmTasks = algorithmTasks;
        }

        public Boolean getPushEnabled()
        {
            return pushEnabled;
        }

        public void setPushEnabled(Boolean pushEnabled)
        {
            this.pushEnabled = pushEnabled;
        }

        public Boolean getFrontendOverlayEnabled()
        {
            return frontendOverlayEnabled;
        }

        public void setFrontendOverlayEnabled(Boolean frontendOverlayEnabled)
        {
            this.frontendOverlayEnabled = frontendOverlayEnabled;
        }

        public String getRecordEngine()
        {
            return recordEngine;
        }

        public void setRecordEngine(String recordEngine)
        {
            this.recordEngine = recordEngine;
        }

        public Integer getAlarmIntervalSec()
        {
            return alarmIntervalSec;
        }

        public void setAlarmIntervalSec(Integer alarmIntervalSec)
        {
            this.alarmIntervalSec = alarmIntervalSec;
        }

        public Boolean getDwellEnabled()
        {
            return dwellEnabled;
        }

        public void setDwellEnabled(Boolean dwellEnabled)
        {
            this.dwellEnabled = dwellEnabled;
        }

        public Long getDwellThresholdMs()
        {
            return dwellThresholdMs;
        }

        public void setDwellThresholdMs(Long dwellThresholdMs)
        {
            this.dwellThresholdMs = dwellThresholdMs;
        }

        public Boolean getAiReviewEnabled()
        {
            return aiReviewEnabled;
        }

        public void setAiReviewEnabled(Boolean aiReviewEnabled)
        {
            this.aiReviewEnabled = aiReviewEnabled;
        }

        public String getAiReviewPrompt()
        {
            return aiReviewPrompt;
        }

        public void setAiReviewPrompt(String aiReviewPrompt)
        {
            this.aiReviewPrompt = aiReviewPrompt;
        }

        public String getRemark()
        {
            return remark;
        }

        public void setRemark(String remark)
        {
            this.remark = remark;
        }

        public JsonNode getGeometryConfig()
        {
            return geometryConfig;
        }

        public void setGeometryConfig(JsonNode geometryConfig)
        {
            this.geometryConfig = geometryConfig;
        }

        public String getStreamUrl()
        {
            return streamUrl;
        }

        public void setStreamUrl(String streamUrl)
        {
            this.streamUrl = streamUrl;
        }
    }

    public static class AlgorithmTaskRequest
    {
        private String algorithmCode;
        private String algorithmName;
        private Float detectFps;
        private Float scoreThreshold;
        private Float nmsThreshold;
        private List<String> targetCodes;

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
            return targetCodes;
        }

        public void setTargetCodes(List<String> targetCodes)
        {
            this.targetCodes = targetCodes;
        }
    }

    public static class EventOrchestrationRequest
    {
        private String name;
        private Boolean enabled;
        private String logicMode;
        private Long timeWindowMs;
        private Long cooldownMs;
        private String outputAlarmName;
        private List<String> conditionKeys;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Boolean getEnabled()
        {
            return enabled;
        }

        public void setEnabled(Boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getLogicMode()
        {
            return logicMode;
        }

        public void setLogicMode(String logicMode)
        {
            this.logicMode = logicMode;
        }

        public Long getTimeWindowMs()
        {
            return timeWindowMs;
        }

        public void setTimeWindowMs(Long timeWindowMs)
        {
            this.timeWindowMs = timeWindowMs;
        }

        public Long getCooldownMs()
        {
            return cooldownMs;
        }

        public void setCooldownMs(Long cooldownMs)
        {
            this.cooldownMs = cooldownMs;
        }

        public String getOutputAlarmName()
        {
            return outputAlarmName;
        }

        public void setOutputAlarmName(String outputAlarmName)
        {
            this.outputAlarmName = outputAlarmName;
        }

        public List<String> getConditionKeys()
        {
            return conditionKeys;
        }

        public void setConditionKeys(List<String> conditionKeys)
        {
            this.conditionKeys = conditionKeys;
        }
    }

    public static class Point
    {
        private Double x;
        private Double y;

        public Double getX()
        {
            return x;
        }

        public void setX(Double x)
        {
            this.x = x;
        }

        public Double getY()
        {
            return y;
        }

        public void setY(Double y)
        {
            this.y = y;
        }
    }
}
