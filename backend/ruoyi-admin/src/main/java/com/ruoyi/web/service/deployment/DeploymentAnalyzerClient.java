package com.ruoyi.web.service.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.DeploymentTask;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.domain.SvaServer;
import com.ruoyi.waring.domain.ZlmServer;
import com.ruoyi.waring.mapper.HDeviceMapper;
import com.ruoyi.waring.mapper.SvaServerMapper;
import com.ruoyi.waring.mapper.ZlmServerMapper;
import com.ruoyi.waring.service.HAlgorithmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.DeploymentTaskAlgorithm;

@Service
public class DeploymentAnalyzerClient
{
    private static final Logger log = LoggerFactory.getLogger(DeploymentAnalyzerClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long DEFAULT_SERVER_ID = 1L;
    private static final String ENGINE_A_SERVER = "A-SERVER";
    private static final String ENGINE_M_SERVER = "M-SERVER";
    private static final String DEFAULT_ZLM_APP = "live";
    private static final String DEFAULT_SVA_APP = "analyzer";
    private static final int DEFAULT_ALARM_INTERVAL_SEC = 180;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private HAlgorithmService hAlgorithmService;

    @Autowired
    private HDeviceMapper hDeviceMapper;

    @Autowired
    private ZlmServerMapper zlmServerMapper;

    @Autowired
    private SvaServerMapper svaServerMapper;

    public AnalyzerResult addControl(DeploymentTask task, String recognitionRegion)
    {
        if (task == null)
        {
            return AnalyzerResult.fail("布控任务不存在");
        }

        BindingConfig bindingConfig = resolveBinding(task.getDeviceId());
        if (bindingConfig == null)
        {
            return AnalyzerResult.fail("未绑定可用服务器或配置缺失");
        }

        String apeId = task.getDeviceId();
        String streamUrl = buildStreamUrl(bindingConfig, apeId);

        boolean pushStream = Boolean.TRUE.equals(task.getPushEnabled());
        boolean frontendOverlayEnabled = Boolean.TRUE.equals(task.getFrontendOverlayEnabled());
        String pushStreamUrl = buildPushStreamUrl(bindingConfig, task.getDeploymentId());
        String algorithmStreamUrl = buildAlgorithmStreamUrl(bindingConfig, task.getDeploymentId());
        if (pushStream && StringUtils.isEmpty(pushStreamUrl))
        {
            return AnalyzerResult.fail("pushStreamUrl不能为空");
        }

        String analyzerAddUrl = bindingConfig.getAnalyzerBaseUrl() + "/api/control/add";
        if (log.isDebugEnabled())
        {
            log.debug("布控启动URL, deploymentId={}, deviceId={}, streamUrl={}, pushStreamUrl={}, algorithmStreamUrl={}, analyzerUrl={}",
                task.getDeploymentId(), apeId, maskSensitiveUrl(streamUrl),
                pushStream ? maskSensitiveUrl(pushStreamUrl) : "", maskSensitiveUrl(algorithmStreamUrl),
                maskSensitiveUrl(analyzerAddUrl));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", task.getDeploymentId());
        payload.put("streamCode", apeId);
        payload.put("streamApp", bindingConfig.zlmApp);
        payload.put("streamName", apeId);
        payload.put("streamUrl", streamUrl);
        payload.put("pushStream", pushStream);
        if (pushStream)
        {
            payload.put("pushStreamUrl", pushStreamUrl);
        }
        String renderMode = pushStream ? "server_overlay" : (frontendOverlayEnabled ? "ws_overlay" : "detect_only");
        payload.put("renderMode", renderMode);
        payload.put("serverOverlayEnabled", pushStream);
        payload.put("wsOverlayEnabled", !pushStream && frontendOverlayEnabled);
        payload.put("saveImageEnabled", true);
        payload.put("saveVideoEnabled", !ENGINE_M_SERVER.equals(normalizeRecordEngine(task.getRecordEngine())));

        List<Map<String, Object>> algorithmTasks = buildAlgorithmTasks(task);
        if (algorithmTasks.isEmpty())
        {
            return AnalyzerResult.fail("algorithmTasks不能为空");
        }

        DeploymentTaskAlgorithm primaryTask = resolvePrimaryTask(task);
        if (primaryTask == null)
        {
            return AnalyzerResult.fail("algorithmTasks不能为空");
        }

        payload.put("algorithmCode", StringUtils.nvl(primaryTask.getAlgorithmCode(), ""));
        payload.put("objectCodes", primaryTask.getTargetCodes());
        payload.put("recognitionRegion", recognitionRegion);
        appendGeometryPayload(payload, task);
        Integer minInterval = task.getAlarmIntervalSec();
        payload.put("minInterval", minInterval == null || minInterval <= 0 ? DEFAULT_ALARM_INTERVAL_SEC : minInterval);
        payload.put("dwellEnabled", Boolean.TRUE.equals(task.getDwellEnabled()));
        Long dwellThresholdMs = task.getDwellThresholdMs();
        payload.put("dwellThresholdMs", dwellThresholdMs == null ? 5000L : dwellThresholdMs);

        String objectStr = StringUtils.nvl(hAlgorithmService.getObjectStrByCode(primaryTask.getAlgorithmCode()), "");
        String apiUrl = StringUtils.nvl(hAlgorithmService.getApiUrlByCode(primaryTask.getAlgorithmCode()), "");
        payload.put("object_str", objectStr);
        payload.put("api_url", apiUrl);
        payload.put("algorithmTasks", algorithmTasks);

        return postJson(analyzerAddUrl, payload, "add");
    }

    private void appendGeometryPayload(Map<String, Object> payload, DeploymentTask task)
    {
        if (payload == null || task == null || StringUtils.isBlank(task.getGeometryConfig()))
        {
            return;
        }
        try
        {
            JsonNode geometryNode = OBJECT_MAPPER.readTree(task.getGeometryConfig());
            if (!geometryNode.isObject())
            {
                return;
            }
            payload.put("geometryConfig", OBJECT_MAPPER.convertValue(geometryNode, Object.class));
            JsonNode regionsNode = geometryNode.get("regions");
            if (regionsNode != null && regionsNode.isArray())
            {
                payload.put("regions", OBJECT_MAPPER.convertValue(regionsNode, Object.class));
            }
            JsonNode linesNode = geometryNode.get("lines");
            if (linesNode != null && linesNode.isArray())
            {
                payload.put("lines", OBJECT_MAPPER.convertValue(linesNode, Object.class));
            }
        }
        catch (Exception ex)
        {
            log.warn("解析geometryConfig失败，deploymentId={}", task.getDeploymentId(), ex);
        }
    }

    private DeploymentTaskAlgorithm resolvePrimaryTask(DeploymentTask task)
    {
        if (task == null)
        {
            return null;
        }
        if (task.getAlgorithmTasks() != null)
        {
            for (DeploymentTaskAlgorithm item : task.getAlgorithmTasks())
            {
                if (item != null && StringUtils.isNotBlank(item.getAlgorithmCode()) && item.getTargetCodes() != null && !item.getTargetCodes().isEmpty())
                {
                    return item;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildAlgorithmTasks(DeploymentTask task)
    {
        List<Map<String, Object>> items = new ArrayList<>();
        if (task == null)
        {
            return items;
        }

        List<DeploymentTaskAlgorithm> sourceTasks = task.getAlgorithmTasks();
        if (sourceTasks == null || sourceTasks.isEmpty())
        {
            return items;
        }

        for (DeploymentTaskAlgorithm item : sourceTasks)
        {
            List<String> targetCodes = item == null ? Collections.<String>emptyList() : item.getTargetCodes();
            if (item == null || StringUtils.isBlank(item.getAlgorithmCode()) || targetCodes == null || targetCodes.isEmpty())
            {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("algorithmCode", item.getAlgorithmCode());
            if (item.getDetectFps() != null)
            {
                row.put("detectFps", item.getDetectFps());
            }
            if (item.getScoreThreshold() != null)
            {
                row.put("scoreThreshold", item.getScoreThreshold());
            }
            if (item.getNmsThreshold() != null)
            {
                row.put("nmsThreshold", item.getNmsThreshold());
            }
            row.put("objectCodes", targetCodes);
            row.put("object_str", StringUtils.nvl(hAlgorithmService.getObjectStrByCode(item.getAlgorithmCode()), ""));
            row.put("api_url", StringUtils.nvl(hAlgorithmService.getApiUrlByCode(item.getAlgorithmCode()), ""));
            items.add(row);
        }
        return items;
    }

    public AnalyzerResult cancelControl(DeploymentTask task)
    {
        if (task == null)
        {
            return AnalyzerResult.fail("布控任务不存在");
        }
        if (StringUtils.isEmpty(task.getDeploymentId()))
        {
            return AnalyzerResult.fail("deploymentId不能为空");
        }

        BindingConfig bindingConfig = resolveBinding(task.getDeviceId());
        if (bindingConfig == null)
        {
            return AnalyzerResult.fail("未绑定可用服务器或配置缺失");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", task.getDeploymentId());
        return postJson(bindingConfig.getAnalyzerBaseUrl() + "/api/control/cancel", payload, "cancel");
    }

    public AnalyzerResult cancelControl(String deploymentId)
    {
        return AnalyzerResult.fail("缺少deviceId，无法定位绑定的SVA服务器");
    }

    public String buildStreamUrl(String apeId)
    {
        BindingConfig bindingConfig = resolveBinding(apeId);
        if (bindingConfig == null)
        {
            return null;
        }
        return buildStreamUrl(bindingConfig, apeId);
    }

    public String buildPushStreamUrl(String apeId, String deploymentId)
    {
        BindingConfig bindingConfig = resolveBinding(apeId);
        if (bindingConfig == null)
        {
            return null;
        }
        return buildPushStreamUrl(bindingConfig, deploymentId);
    }

    public String buildAlgorithmStreamUrl(String apeId, String deploymentId)
    {
        BindingConfig bindingConfig = resolveBinding(apeId);
        if (bindingConfig == null)
        {
            return null;
        }
        return buildAlgorithmStreamUrl(bindingConfig, deploymentId);
    }

    private AnalyzerResult postJson(String url, Map<String, Object> payload, String action)
    {
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful())
            {
                String detail = "HTTP状态码=" + response.getStatusCode().value()
                    + ", 响应=" + StringUtils.nvl(body, "");
                return AnalyzerResult.fail("调用失败，HTTP状态码=" + response.getStatusCode().value(), detail);
            }

            if (StringUtils.isEmpty(body))
            {
                return AnalyzerResult.fail("调用失败，响应为空", "响应为空");
            }

            JsonNode root = OBJECT_MAPPER.readTree(body);
            int code = root.path("code").asInt(0);
            String msg = root.path("msg").asText("");
            String shortMessage = resolveShortMessage(action, code, msg);
            if (code == 1000)
            {
                return AnalyzerResult.ok(shortMessage, body);
            }
            return AnalyzerResult.fail(shortMessage, body);
        }
        catch (ResourceAccessException ex)
        {
            String detail = buildExceptionDetail(ex);
            if (isTimeoutException(ex))
            {
                log.error("调用analyzer接口超时, url={}", url, ex);
                return AnalyzerResult.fail("连接超时", detail);
            }
            log.error("调用analyzer接口失败, url={}", url, ex);
            return AnalyzerResult.fail("调用算法服务接口异常", detail);
        }
        catch (Exception ex)
        {
            log.error("调用analyzer接口失败, url={}", url, ex);
            return AnalyzerResult.fail("调用算法服务接口异常", buildExceptionDetail(ex));
        }
    }

    private boolean isTimeoutException(Throwable ex)
    {
        Throwable current = ex;
        while (current != null)
        {
            if (current instanceof SocketTimeoutException)
            {
                return true;
            }
            String className = current.getClass().getName();
            if (className != null && className.toLowerCase().contains("timeout"))
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildExceptionDetail(Exception ex)
    {
        String message = ex.getMessage();
        if (StringUtils.isEmpty(message))
        {
            return ex.getClass().getName();
        }
        return ex.getClass().getName() + ": " + message;
    }

    private String resolveShortMessage(String action, int code, String msg)
    {
        String normalizedAction = normalizeText(action);
        String normalizedMsg = normalizeText(msg);

        if ("add".equals(normalizedAction) && code == 1000)
        {
            if ("add success".equals(normalizedMsg))
            {
                return "创建成功，布控已启动";
            }
            if ("the control is running".equals(normalizedMsg))
            {
                return "该布控已在运行，无需重复创建";
            }
        }

        if ("add".equals(normalizedAction) && code == 0
            && "push stream connect error".equals(normalizedMsg))
        {
            return "推送失败，请稍后再试！";
        }

        if ("add".equals(normalizedAction)
            && normalizedMsg.contains("pull stream connect error"))
        {
            return "读取视频流失败，请确认设备启动了视频流";
        }

        if ("cancel".equals(normalizedAction))
        {
            if (code == 1000 && "control is running, cancel success".equals(normalizedMsg))
            {
                return "停止成功，布控已取消";
            }
            if (code == 0 && "there is no such control".equals(normalizedMsg))
            {
                return "未找到该布控，可能已停止或不存在";
            }
        }

        if (code == 1000)
        {
            return "操作成功";
        }
        return StringUtils.isEmpty(msg) ? "操作失败" : msg;
    }

    private String normalizeText(String text)
    {
        if (StringUtils.isBlank(text))
        {
            return "";
        }
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String normalizeRecordEngine(String recordEngine)
    {
        if (StringUtils.isBlank(recordEngine))
        {
            return ENGINE_A_SERVER;
        }

        String normalized = recordEngine.trim();
        if (ENGINE_M_SERVER.equals(normalized))
        {
            return ENGINE_M_SERVER;
        }
        return ENGINE_A_SERVER;
    }

    private BindingConfig resolveBinding(String apeId)
    {
        if (StringUtils.isBlank(apeId))
        {
            return null;
        }

        HDevice device = hDeviceMapper.selectDeviceByApeId(apeId);
        if (device == null)
        {
            return null;
        }

        Long zlmServerId = device.getZlm_server_id() == null ? DEFAULT_SERVER_ID : device.getZlm_server_id();
        Long svaServerId = device.getSva_server_id() == null ? DEFAULT_SERVER_ID : device.getSva_server_id();

        ZlmServer zlmServer = zlmServerMapper.selectEnabledById(zlmServerId);
        SvaServer svaServer = svaServerMapper.selectEnabledById(svaServerId);
        if (zlmServer == null || svaServer == null)
        {
            return null;
        }

        if (StringUtils.isBlank(zlmServer.getHost())
            || zlmServer.getMedia_rtsp_port() == null
            || zlmServer.getMedia_http_port() == null)
        {
            return null;
        }

        if (StringUtils.isBlank(svaServer.getHost()) || svaServer.getAnalyzer_port() == null)
        {
            return null;
        }

        String zlmApp = StringUtils.isBlank(zlmServer.getApp()) ? DEFAULT_ZLM_APP : zlmServer.getApp().trim();
        String svaApp = StringUtils.isBlank(svaServer.getApp()) ? DEFAULT_SVA_APP : svaServer.getApp().trim();
        return new BindingConfig(zlmServer.getHost().trim(), zlmApp, zlmServer.getMedia_rtsp_port(),
            zlmServer.getMedia_http_port(), svaServer.getHost().trim(), svaApp, svaServer.getAnalyzer_port());
    }

    private String buildStreamUrl(BindingConfig config, String apeId)
    {
        if (StringUtils.isBlank(apeId))
        {
            return null;
        }
        return "rtsp://" + config.zlmHost + ":" + config.zlmMediaRtspPort + "/" + config.zlmApp + "/" + apeId;
    }

    private String buildPushStreamUrl(BindingConfig config, String deploymentId)
    {
        if (StringUtils.isBlank(deploymentId))
        {
            return null;
        }
        return "rtsp://" + config.zlmHost + ":" + config.zlmMediaRtspPort + "/" + config.svaApp + "/" + deploymentId;
    }

    private String buildAlgorithmStreamUrl(BindingConfig config, String deploymentId)
    {
        if (StringUtils.isBlank(deploymentId))
        {
            return null;
        }
        return "ws://" + config.zlmHost + ":" + config.zlmMediaHttpPort + "/" + config.svaApp + "/"
            + deploymentId + ".live.flv";
    }

    private String maskSensitiveUrl(String url)
    {
        if (StringUtils.isBlank(url))
        {
            return url;
        }
        return url.replaceAll("(?i)([?&](secret|token|access_token|auth|sign|signature)=)[^&]*", "$1***");
    }

    private static class BindingConfig
    {
        private final String zlmHost;
        private final String zlmApp;
        private final int zlmMediaRtspPort;
        private final int zlmMediaHttpPort;
        private final String svaHost;
        private final String svaApp;
        private final int svaAnalyzerPort;

        private BindingConfig(String zlmHost, String zlmApp, int zlmMediaRtspPort, int zlmMediaHttpPort,
            String svaHost, String svaApp, int svaAnalyzerPort)
        {
            this.zlmHost = zlmHost;
            this.zlmApp = zlmApp;
            this.zlmMediaRtspPort = zlmMediaRtspPort;
            this.zlmMediaHttpPort = zlmMediaHttpPort;
            this.svaHost = svaHost;
            this.svaApp = svaApp;
            this.svaAnalyzerPort = svaAnalyzerPort;
        }

        private String getAnalyzerBaseUrl()
        {
            return "http://" + svaHost + ":" + svaAnalyzerPort;
        }
    }

    public static class AnalyzerResult
    {
        private final boolean success;
        private final String message;
        private final String detailMessage;

        private AnalyzerResult(boolean success, String message, String detailMessage)
        {
            this.success = success;
            this.message = message;
            this.detailMessage = detailMessage;
        }

        public static AnalyzerResult ok(String message)
        {
            return new AnalyzerResult(true, message, message);
        }

        public static AnalyzerResult ok(String message, String detailMessage)
        {
            return new AnalyzerResult(true, message, detailMessage);
        }

        public static AnalyzerResult fail(String message)
        {
            return new AnalyzerResult(false, message, message);
        }

        public static AnalyzerResult fail(String message, String detailMessage)
        {
            return new AnalyzerResult(false, message, detailMessage);
        }

        public boolean isSuccess()
        {
            return success;
        }

        public String getMessage()
        {
            return message;
        }

        public String getDetailMessage()
        {
            return detailMessage;
        }
    }
}