package com.ruoyi.waring.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.DeploymentTask;
import com.ruoyi.system.service.IDeploymentTaskService;
import com.ruoyi.web.service.deployment.DeploymentAnalyzerClient;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.service.HDeviceService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备健康度：把「取流到底稳不稳」从排障经验变成可看的指标。
 *
 * 数据全部来自 Analyzer 已有的 /api/health 与 /api/controls，
 * 不新增上报通道、不改 Analyzer，因此不需要重编 C++。
 */
@RestController
@RequestMapping("/waring/health")
public class DeviceHealthController extends BaseController
{
    /** 健康分档：≥90 良好、≥70 注意、其余异常。 */
    private static final int SCORE_GOOD = 90;
    private static final int SCORE_WARN = 70;
    /** deployment_task.status 的实际取值。 */
    private static final String STATUS_RUNNING = "RUNNING";

    @Resource
    private HDeviceService hDeviceService;

    @Resource
    private DeploymentAnalyzerClient deploymentAnalyzerClient;

    @Resource
    private IDeploymentTaskService deploymentTaskService;

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthController.class);

    /**
     * 单设备健康度。deviceId 为业务设备编号（ape_id）。
     */
    @PreAuthorize("@ss.hasPermi('waring:device:list')")
    @GetMapping("/device")
    @ResponseBody
    public AjaxResult device(@RequestParam String deviceId)
    {
        HDevice device = hDeviceService.selectDeviceByApeId(deviceId);
        if (device == null)
        {
            return error("设备不存在：" + deviceId);
        }
        return success(buildDeviceHealth(device));
    }

    /**
     * 全部设备健康度汇总。设备不多（演示环境个位数），逐个探活可接受；
     * 单台超时不会拖垮整体，因为探针内部已 catch。
     */
    @PreAuthorize("@ss.hasPermi('waring:device:list')")
    @GetMapping("/list")
    @ResponseBody
    public AjaxResult list()
    {
        List<HDevice> devices = hDeviceService.selectDeviceList(new HDevice(), getUserId());
        List<Map<String, Object>> rows = new ArrayList<>();
        int healthy = 0;
        int unhealthy = 0;
        for (HDevice device : devices)
        {
            if (device == null || StringUtils.isBlank(device.getApe_id()))
            {
                continue;
            }
            Map<String, Object> row = buildDeviceHealth(device);
            rows.add(row);
            if ("good".equals(row.get("healthLevel")))
            {
                healthy++;
            }
            else if (!"good".equals(row.get("healthLevel")))
            {
                unhealthy++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("healthy", healthy);
        result.put("unhealthy", unhealthy);
        result.put("devices", rows);
        return success(result);
    }

    private Map<String, Object> buildDeviceHealth(HDevice device)
    {
        String deviceId = device.getApe_id();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deviceId", deviceId);
        row.put("deviceName", StringUtils.defaultIfBlank(device.getName(), deviceId));
        row.put("deviceType", StringUtils.defaultIfBlank(device.getDevice_type(), "rtsp"));

        // Analyzer 的 /api/controls 用 deploymentId 作为 code，不返回 streamCode，
        // 所以先查该设备下「运行中」的布控任务编号再去匹配。
        Collection<String> runningDeploymentIds = resolveRunningDeploymentIds(deviceId);
        row.put("runningDeployments", runningDeploymentIds.size());

        DeploymentAnalyzerClient.AnalyzerHealth health =
            deploymentAnalyzerClient.probeHealth(deviceId, runningDeploymentIds);
        if (health == null)
        {
            // 设备没绑到可用 Analyzer：布控根本起不来。
            row.put("analyzerReachable", false);
            row.put("streamAttached", false);
            row.put("healthScore", 0);
            row.put("healthLevel", "error");
            row.put("message", "未绑定可用的分析器或配置缺失");
            return row;
        }

        int score = 100;
        List<String> issues = new ArrayList<>();
        if (!health.reachable)
        {
            score = 0;
            issues.add("分析器不可达");
        }
        else
        {
            if (!health.streamAttached)
            {
                // 没有布控在跑不等于故障，只是「未监控」。
                score -= 30;
                issues.add(runningDeploymentIds.isEmpty()
                    ? "该设备当前没有运行中的布控"
                    : "布控已下发但分析器未在执行");
            }
            else if (health.checkFps > 0 && health.checkFps < 1.0)
            {
                score -= 20;
                issues.add(String.format("推理帧率偏低 %.2f fps", health.checkFps));
            }
            if (health.detectFramePostFailed > 0)
            {
                score -= 10;
                issues.add("存在帧上报失败 " + health.detectFramePostFailed + " 次");
            }
            if (health.detectEventPostFailed > 0)
            {
                score -= 15;
                issues.add("存在事件上报失败 " + health.detectEventPostFailed + " 次");
            }
            if (health.detectPostCircuitStreams > 0)
            {
                score -= 20;
                issues.add("上报熔断中 " + health.detectPostCircuitStreams + " 路");
            }
        }
        score = Math.max(0, score);

        row.put("analyzerReachable", health.reachable);
        row.put("analyzerUrl", health.analyzerUrl);
        row.put("streamAttached", health.streamAttached);
        row.put("checkFps", health.checkFps);
        if (StringUtils.isNotBlank(health.matchedDeploymentId))
        {
            row.put("matchedDeploymentId", health.matchedDeploymentId);
            row.put("controlStreamUrl", health.controlStreamUrl);
        }
        row.put("detectFrameDropped", health.detectFrameDropped);
        row.put("detectFramePostFailed", health.detectFramePostFailed);
        row.put("detectEventPostFailed", health.detectEventPostFailed);
        row.put("detectLifecycleActive", health.detectLifecycleActive);
        row.put("detectPostCircuitStreams", health.detectPostCircuitStreams);
        row.put("healthScore", score);
        row.put("healthLevel", score >= SCORE_GOOD ? "good" : (score >= SCORE_WARN ? "warn" : "error"));
        row.put("message", issues.isEmpty() ? "正常" : String.join("；", issues));
        if (!health.reachable && StringUtils.isNotBlank(health.error))
        {
            row.put("error", health.error);
        }
        return row;
    }

    /**
     * 该设备下状态为「运行中」的布控任务编号。
     * 布控启动时 Analyzer 的 code 就是 deploymentId，用它匹配 /api/controls 的返回项。
     *
     * 注意 deployment_task.status 存的是 RUNNING / STOPPED 字样，不是 0/1。
     */
    private Collection<String> resolveRunningDeploymentIds(String deviceId)
    {
        List<String> ids = new ArrayList<>();
        try
        {
            List<DeploymentTask> tasks = deploymentTaskService.selectDeploymentTaskList(STATUS_RUNNING, null, null);
            for (DeploymentTask task : tasks)
            {
                if (task != null && deviceId.equals(StringUtils.trimToEmpty(task.getDeviceId()))
                    && StringUtils.isNotBlank(task.getDeploymentId()))
                {
                    ids.add(task.getDeploymentId().trim());
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("查询设备布控任务失败, deviceId={}, err={}", deviceId, ex.getMessage());
        }
        return ids;
    }
}
