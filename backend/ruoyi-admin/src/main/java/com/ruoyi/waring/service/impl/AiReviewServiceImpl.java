package com.ruoyi.waring.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.waring.domain.AiReviewResult;
import com.ruoyi.waring.domain.AiReviewServer;
import com.ruoyi.waring.domain.AiReviewTask;
import com.ruoyi.waring.domain.HWaring;
import com.ruoyi.waring.mapper.AiReviewResultMapper;
import com.ruoyi.waring.mapper.AiReviewServerMapper;
import com.ruoyi.waring.mapper.AiReviewTaskMapper;
import com.ruoyi.waring.mapper.HWaringMapper;
import com.ruoyi.waring.service.IAiReviewService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReviewServiceImpl implements IAiReviewService
{
    private static final Logger log = LoggerFactory.getLogger(AiReviewServiceImpl.class);

    private static final String CONFIG_KEY_ENABLED = "ai.review.enabled";
    private static final String SERVER_TYPE_OPENAI = "openai";
    private static final String SERVER_TYPE_ALIYUN = "aliyun";
    private static final String DEFAULT_OPENAI_MODEL = "qwen3-vl";
    private static final String DEFAULT_ALIYUN_MODEL = "qwen-vl-max";
    private static final String DEFAULT_ALIYUN_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    /** 演示机 Analyzer / Nginx 告警落盘根目录；application.yml 默认仍是 Windows 的 D:/ruoyi/uploadPath。 */
    private static final String DEMO_UPLOAD_DIR = "/var/www/SVA-web/upload";
    private static final int MAX_INLINE_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final long RETRY_DELAY_MILLIS = 60_000L;

    /** 系统提示词：约束输出结构，两个服务端分支共用。 */
    private static final String REVIEW_SYSTEM_PROMPT =
        "你是工业安全告警复核助手。请仅输出JSON对象，不要输出Markdown代码块。"
            + "JSON字段必须包含decision、confidence、observed、pose_match、summary、reason。"
            + "decision只允许true_alarm、false_alarm、uncertain。"
            + "observed必须是你在图中真实看到的内容，不确定就写不确定，禁止编造画面里没有的物体或场景。";

    @Resource
    private AiReviewTaskMapper aiReviewTaskMapper;

    @Resource
    private AiReviewResultMapper aiReviewResultMapper;

    @Resource
    private AiReviewServerMapper aiReviewServerMapper;

    @Resource
    private HWaringMapper hWaringMapper;

    @Resource
    private ISysConfigService sysConfigService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public void createImageReviewTask(HWaring waring)
    {
        if (waring == null || waring.getW_id() == null)
        {
            return;
        }
        if (!isAiReviewEnabled())
        {
            return;
        }
        if (Boolean.FALSE.equals(waring.getAi_review_enabled()))
        {
            return;
        }
        String mediaUrl = StringUtils.trimToEmpty(waring.getPicture_absolute_url());
        if (StringUtils.isEmpty(mediaUrl))
        {
            mediaUrl = StringUtils.trimToEmpty(waring.getPicture_url());
        }
        if (StringUtils.isEmpty(mediaUrl))
        {
            return;
        }
        if (aiReviewTaskMapper.countOpenTasksByWarningId(waring.getW_id()) > 0)
        {
            return;
        }

        Date now = new Date();
        AiReviewTask task = new AiReviewTask();
        task.setWId(waring.getW_id());
        task.setReviewType("image");
        task.setMediaUrl(mediaUrl);
        task.setPromptSnapshot(normalizePromptSnapshot(waring.getAi_review_prompt()));
        task.setStatus(AiReviewTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetries(DEFAULT_MAX_RETRIES);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        aiReviewTaskMapper.insertTask(task);
    }

    @Override
    public int scanAndDispatchPendingTasks()
    {
        if (!isAiReviewEnabled())
        {
            return 0;
        }

        Date now = new Date();
        List<AiReviewTask> tasks = aiReviewTaskMapper.selectPendingBatch(now, DEFAULT_BATCH_SIZE);
        if (tasks == null || tasks.isEmpty())
        {
            return 0;
        }

        int claimed = 0;
        for (AiReviewTask task : tasks)
        {
            boolean claimedCurrent = claimTask(task, now);
            if (!claimedCurrent)
            {
                continue;
            }
            claimed++;
            threadPoolTaskExecutor.execute(() -> processTask(task.getId()));
        }
        return claimed;
    }

    private boolean claimTask(AiReviewTask task, Date now)
    {
        Date updateTime = new Date();
        if (AiReviewTask.STATUS_PENDING.equals(task.getStatus()))
        {
            return aiReviewTaskMapper.claimPendingTask(task.getId(), now, updateTime) > 0;
        }
        if (AiReviewTask.STATUS_FAILED.equals(task.getStatus()))
        {
            return aiReviewTaskMapper.claimRetryTask(task.getId(), now, now, updateTime) > 0;
        }
        return false;
    }

    private void processTask(Long taskId)
    {
        if (taskId == null)
        {
            return;
        }

        AiReviewTask task = aiReviewTaskMapper.selectById(taskId);
        if (task == null)
        {
            return;
        }
        if (!AiReviewTask.STATUS_RUNNING.equals(task.getStatus()))
        {
            return;
        }

        if (!isAiReviewEnabled())
        {
            markSkipped(taskId, "AI review disabled");
            return;
        }

        AiReviewServer server = aiReviewServerMapper.selectFirstEnabled();
        if (server == null)
        {
            markRetryableFailure(task, null, "No enabled ai_review_server configured");
            return;
        }

        HWaring waring = hWaringMapper.selectWaringByWId(task.getWId());
        if (waring == null)
        {
            markSkipped(taskId, "Warning not found");
            return;
        }

        try
        {
            ReviewOutcome outcome = invokeReview(server, waring, task);
            AiReviewResult result = new AiReviewResult();
            result.setTaskId(taskId);
            result.setWId(task.getWId());
            result.setDecision(outcome.decision);
            result.setConfidence(outcome.confidence);
            result.setFalsePositiveScore(outcome.falsePositiveScore);
            result.setSummary(outcome.summary);
            result.setReason(outcome.reason);
            result.setRawResponseJson(outcome.rawResponseJson);
            result.setCreateTime(new Date());
            aiReviewResultMapper.insertResult(result);

            Date now = new Date();
            aiReviewTaskMapper.markSuccess(taskId, server.getId(), now, now);
        }
        catch (Exception ex)
        {
            log.warn("AI review failed, taskId={}, wId={}", taskId, task.getWId(), ex);
            markRetryableFailure(task, server.getId(), ex.getMessage());
        }
    }

    private void markRetryableFailure(AiReviewTask task, Long serverId, String errorMessage)
    {
        Date now = new Date();
        int nextRetryCount = Convert.toInt(task.getRetryCount()) + 1;
        boolean terminalFailed = nextRetryCount >= Convert.toInt(task.getMaxRetries(), DEFAULT_MAX_RETRIES);
        Date nextRetryTime = terminalFailed ? null : new Date(now.getTime() + RETRY_DELAY_MILLIS);
        aiReviewTaskMapper.markFailed(task.getId(), serverId, nextRetryCount, task.getMaxRetries(), nextRetryTime,
            trimError(errorMessage), now, now, terminalFailed);
    }

    private void markSkipped(Long taskId, String message)
    {
        Date now = new Date();
        aiReviewTaskMapper.markSkipped(taskId, trimError(message), now, now);
    }

    private String trimError(String message)
    {
        if (message == null)
        {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private boolean isAiReviewEnabled()
    {
        return Convert.toBool(sysConfigService.selectConfigByKey(CONFIG_KEY_ENABLED), false);
    }

    private ReviewOutcome invokeReview(AiReviewServer server, HWaring waring, AiReviewTask task) throws JsonProcessingException
    {
        String serverType = StringUtils.defaultIfBlank(server.getServerType(), SERVER_TYPE_OPENAI).trim().toLowerCase();
        if (SERVER_TYPE_ALIYUN.equals(serverType))
        {
            return invokeAliyunReview(server, waring, task);
        }
        return invokeOpenAiCompatibleReview(server, waring, task);
    }

    private ReviewOutcome invokeOpenAiCompatibleReview(AiReviewServer server, HWaring waring, AiReviewTask task) throws JsonProcessingException
    {
        RestTemplate restTemplate = buildRestTemplate(server.getTimeoutMs());
        String url = buildServerUrl(server);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", StringUtils.defaultIfBlank(server.getModel(), DEFAULT_OPENAI_MODEL));
        payload.put("temperature", 0);
        payload.put("messages", buildOpenAiMessages(waring, task));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = StringUtils.trimToEmpty(server.getApiKey());
        if (StringUtils.isNotEmpty(apiKey))
        {
            headers.setBearerAuth(apiKey);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String response = restTemplate.postForObject(url, entity, String.class);
        if (StringUtils.isEmpty(response))
        {
            throw new IllegalStateException("empty review response");
        }
        return parseReviewOutcome(response);
    }

    private ReviewOutcome invokeAliyunReview(AiReviewServer server, HWaring waring, AiReviewTask task) throws JsonProcessingException
    {
        String apiKey = StringUtils.trimToEmpty(server.getApiKey());
        if (StringUtils.isEmpty(apiKey))
        {
            throw new IllegalStateException("Aliyun DashScope api_key is empty");
        }

        RestTemplate restTemplate = buildRestTemplate(server.getTimeoutMs());
        String url = StringUtils.defaultIfBlank(server.getEndpointUrl(), DEFAULT_ALIYUN_ENDPOINT);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("messages", buildAliyunMessages(waring, task, server));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("temperature", 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", StringUtils.defaultIfBlank(server.getModel(), DEFAULT_ALIYUN_MODEL));
        payload.put("input", input);
        payload.put("parameters", parameters);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("X-DashScope-OssResourceResolve", "enable");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        String response = restTemplate.postForObject(url, entity, String.class);
        if (StringUtils.isEmpty(response))
        {
            throw new IllegalStateException("empty review response");
        }
        return parseReviewOutcome(response);
    }

    private RestTemplate buildRestTemplate(Integer timeoutMs)
    {
        int timeout = timeoutMs == null || timeoutMs <= 0 ? 15000 : timeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String buildServerUrl(AiReviewServer server)
    {
        String endpointUrl = StringUtils.trimToEmpty(server.getEndpointUrl());
        if (StringUtils.isNotEmpty(endpointUrl))
        {
            return endpointUrl;
        }
        throw new IllegalStateException("AI review endpoint_url is empty");
    }

    private List<Map<String, Object>> buildOpenAiMessages(HWaring waring, AiReviewTask task)
    {
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", REVIEW_SYSTEM_PROMPT);
        messages.add(system);

        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", buildReviewPrompt(waring, task));
        content.add(textPart);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", task.getMediaUrl());
        imagePart.put("image_url", imageUrl);
        content.add(imagePart);

        user.put("content", content);
        messages.add(user);
        return messages;
    }

    private List<Map<String, Object>> buildAliyunMessages(HWaring waring, AiReviewTask task, AiReviewServer server)
    {
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> systemText = new LinkedHashMap<>();
        systemText.put("text", REVIEW_SYSTEM_PROMPT);
        List<Map<String, Object>> systemContent = new ArrayList<>();
        systemContent.add(systemText);
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", systemContent);
        messages.add(system);

        List<Map<String, Object>> userContent = new ArrayList<>();
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("image", buildAliyunImageDataUrl(task.getMediaUrl(), server.getTimeoutMs()));
        userContent.add(imagePart);
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", buildReviewPrompt(waring, task));
        userContent.add(textPart);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", userContent);
        messages.add(user);
        return messages;
    }

    private String buildAliyunImageDataUrl(String mediaUrl, Integer timeoutMs)
    {
        String source = StringUtils.trimToEmpty(mediaUrl);
        if (StringUtils.isEmpty(source))
        {
            throw new IllegalStateException("AI review mediaUrl is empty");
        }
        if (source.startsWith("data:image/"))
        {
            return source;
        }

        ImagePayload payload = loadImagePayload(source, timeoutMs);
        if (payload.bytes.length > MAX_INLINE_IMAGE_BYTES)
        {
            throw new IllegalStateException("AI review image too large: " + payload.bytes.length + " bytes");
        }
        String base64 = Base64.getEncoder().encodeToString(payload.bytes);
        return "data:" + payload.mediaType + ";base64," + base64;
    }

    private ImagePayload loadImagePayload(String source, Integer timeoutMs)
    {
        if (isHttpUrl(source))
        {
            try
            {
                return downloadImagePayload(source, timeoutMs);
            }
            catch (Exception ex)
            {
                ImagePayload fallback = tryLoadLocalImagePayload(source);
                if (fallback != null)
                {
                    return fallback;
                }
                throw new IllegalStateException("failed to load AI review image from url: " + source, ex);
            }
        }

        ImagePayload payload = tryLoadLocalImagePayload(source);
        if (payload != null)
        {
            return payload;
        }

        String publicPath = extractPublicMediaPath(source);
        if (StringUtils.isNotEmpty(publicPath))
        {
            Exception last = null;
            for (String base : new String[] {"http://127.0.0.1", "http://127.0.0.1:8080"})
            {
                try
                {
                    return downloadImagePayload(base + publicPath, timeoutMs);
                }
                catch (Exception ex)
                {
                    last = ex;
                }
            }
            throw new IllegalStateException("failed to load AI review image: " + source, last);
        }
        throw new IllegalStateException("failed to load AI review image: " + source);
    }

    private ImagePayload downloadImagePayload(String url, Integer timeoutMs)
    {
        RestTemplate restTemplate = buildRestTemplate(timeoutMs);
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0)
        {
            throw new IllegalStateException("empty image response");
        }
        MediaType contentType = response.getHeaders().getContentType();
        String mediaType = contentType == null ? guessImageMediaType(url) : contentType.toString();
        if (!mediaType.startsWith("image/"))
        {
            mediaType = guessImageMediaType(url);
        }
        return new ImagePayload(body, mediaType);
    }

    private ImagePayload tryLoadLocalImagePayload(String source)
    {
        List<Path> candidates = buildLocalImageCandidates(source);
        for (Path candidate : candidates)
        {
            try
            {
                if (candidate != null && Files.isRegularFile(candidate))
                {
                    return new ImagePayload(Files.readAllBytes(candidate), guessImageMediaType(candidate.toString()));
                }
            }
            catch (IOException ex)
            {
                log.warn("failed to read AI review local image, path={}", candidate, ex);
            }
        }
        return null;
    }

    private List<Path> buildLocalImageCandidates(String source)
    {
        List<Path> candidates = new ArrayList<>();
        addLocalImageCandidate(candidates, source);

        String pathPart = source;
        if (isHttpUrl(source))
        {
            try
            {
                pathPart = URI.create(source).getPath();
            }
            catch (Exception ignored)
            {
                pathPart = "";
            }
        }

        if (StringUtils.isNotEmpty(pathPart))
        {
            if (pathPart.startsWith(Constants.RESOURCE_PREFIX))
            {
                addProfileCandidate(candidates, pathPart.substring(Constants.RESOURCE_PREFIX.length()));
            }
            addProfileCandidate(candidates, pathPart);
            if (pathPart.startsWith("/"))
            {
                addProfileCandidate(candidates, pathPart.substring(1));
            }
            addDemoUploadCandidates(candidates, pathPart);
        }
        addDemoUploadCandidates(candidates, source);
        return candidates;
    }

    /**
     * 库里的封面图是 Nginx 路径 {@code /alarm/...}，不是若依 {@code /profile/...}。
     * 演示机文件在 {@code /var/www/SVA-web/upload/alarm/}。
     */
    private void addDemoUploadCandidates(List<Path> candidates, String source)
    {
        String publicPath = extractPublicMediaPath(source);
        if (StringUtils.isEmpty(publicPath))
        {
            return;
        }
        addLocalImageCandidate(candidates, DEMO_UPLOAD_DIR + publicPath);
        String profile = StringUtils.trimToEmpty(RuoYiConfig.getProfile());
        if (StringUtils.isNotEmpty(profile) && !DEMO_UPLOAD_DIR.equals(profile.replace('\\', '/').replaceAll("/+$", "")))
        {
            addLocalImageCandidate(candidates, Paths.get(profile, publicPath.substring(1)).toString());
        }
    }

    private String extractPublicMediaPath(String source)
    {
        String normalized = StringUtils.trimToEmpty(source).replace('\\', '/');
        if (StringUtils.isEmpty(normalized))
        {
            return null;
        }
        if (isHttpUrl(normalized))
        {
            try
            {
                normalized = StringUtils.trimToEmpty(URI.create(normalized).getPath());
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
        int alarm = normalized.indexOf("/alarm/");
        if (alarm >= 0)
        {
            return normalized.substring(alarm);
        }
        if (normalized.startsWith("alarm/"))
        {
            return "/" + normalized;
        }
        int zlm = normalized.indexOf("/zlm/");
        if (zlm >= 0)
        {
            return normalized.substring(zlm);
        }
        if (normalized.startsWith("zlm/"))
        {
            return "/" + normalized;
        }
        return null;
    }

    private void addLocalImageCandidate(List<Path> candidates, String value)
    {
        String normalized = StringUtils.trimToEmpty(value);
        if (StringUtils.isEmpty(normalized) || isHttpUrl(normalized))
        {
            return;
        }
        try
        {
            candidates.add(Paths.get(normalized));
        }
        catch (Exception ignored)
        {
        }
    }

    private void addProfileCandidate(List<Path> candidates, String value)
    {
        String normalized = StringUtils.trimToEmpty(value);
        if (StringUtils.isEmpty(normalized))
        {
            return;
        }
        while (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }
        String profile = StringUtils.trimToEmpty(RuoYiConfig.getProfile());
        if (StringUtils.isEmpty(profile))
        {
            return;
        }
        try
        {
            candidates.add(Paths.get(profile, normalized));
        }
        catch (Exception ignored)
        {
        }
    }

    private boolean isHttpUrl(String value)
    {
        String normalized = StringUtils.trimToEmpty(value).toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String guessImageMediaType(String value)
    {
        String lower = StringUtils.trimToEmpty(value).toLowerCase();
        if (lower.endsWith(".png"))
        {
            return "image/png";
        }
        if (lower.endsWith(".webp"))
        {
            return "image/webp";
        }
        if (lower.endsWith(".gif"))
        {
            return "image/gif";
        }
        if (lower.endsWith(".bmp"))
        {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    /**
     * 复核提示词。
     *
     * 原版只给「类型 / 设备 / 时间」三个字段，模型看不到算法已经算出的量化证据，
     * 只能凭图猜——实测中 qwen-vl-max 会把工位截图说成「有人在打网球」。
     * 这里把俯仰角、持续时长、质量分、档位一并交给模型，并写清「什么算误报」。
     */
    private String buildReviewPrompt(HWaring waring)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("你是工业安全告警复核助手。请只输出JSON对象，不要输出Markdown代码块。\n\n");

        builder.append("【告警信息】\n");
        builder.append("行为类型：").append(StringUtils.defaultIfBlank(waring.getAlarm_type_name(), "未知")).append('\n');
        builder.append("设备：").append(StringUtils.defaultIfBlank(waring.getDevice_name(), waring.getDevice_id())).append('\n');
        builder.append("时间：").append(StringUtils.defaultIfBlank(waring.getAlarm_time(), "未知")).append('\n');

        // 算法证据：模型看图看不出「低了多少度、低了多久」，这些只有算法知道。
        boolean hasEvidence = false;
        StringBuilder evidence = new StringBuilder();
        if (waring.getSva_pitch_degree() != null)
        {
            evidence.append("- 头部俯仰角：").append(String.format("%.0f", waring.getSva_pitch_degree()))
                .append("°（进入低头阈值 32°，越接近 90° 越像头贴桌面）\n");
            hasEvidence = true;
        }
        if (waring.getDuration_ms() != null && waring.getDuration_ms() > 0)
        {
            evidence.append("- 持续低头时长：").append(String.format("%.1f", waring.getDuration_ms() / 1000.0))
                .append(" 秒（确认档 5 秒，严重档 15 秒）\n");
            hasEvidence = true;
        }
        if (waring.getSva_sleep_score() != null)
        {
            evidence.append("- 睡岗质量分：").append(String.format("%.0f", waring.getSva_sleep_score()))
                .append("/100（峰值角 30% + 占空比 25% + 时长 25% + 头点静止 20%）\n");
            hasEvidence = true;
        }
        if (StringUtils.isNotBlank(waring.getAlarm_level_name()))
        {
            evidence.append("- 告警档位：").append(waring.getAlarm_level_name()).append('\n');
            hasEvidence = true;
        }
        if (hasEvidence)
        {
            builder.append("\n【算法判定证据】（由姿态模型计算，供你参考，不要盲从）\n").append(evidence);
        }

        builder.append("\n【任务】结合图片判断这条告警是否误报。\n");
        builder.append("判定标准：\n");
        builder.append("- 图中人确实趴在桌上/头长时间深低且没有在做别的事 → decision=true_alarm\n");
        builder.append("- 图中人在低头看手机、看键盘、写字、捡东西、抬头看屏幕 → decision=false_alarm\n");
        builder.append("- 画面里没有对应的人、或图片模糊看不清 → decision=uncertain\n");
        builder.append("- 算法证据与图片矛盾时（例如角度很深但人在正常坐姿），以图片为准并说明矛盾\n");

        builder.append("\n【输出JSON字段】\n");
        builder.append("decision：true_alarm / false_alarm / uncertain\n");
        builder.append("confidence：0~1 的把握度\n");
        builder.append("observed：你在图中实际看到的内容，一句话，不要编造\n");
        builder.append("pose_match：boolean，图中人的姿态是否与算法判定一致\n");
        builder.append("summary：一句话结论\n");
        builder.append("reason：判断依据，说明你看到了什么、为什么这样判\n");
        return builder.toString();
    }

    private String buildReviewPrompt(HWaring waring, AiReviewTask task)
    {
        String basePrompt = buildReviewPrompt(waring);
        String customPrompt = task == null ? null : normalizePromptSnapshot(task.getPromptSnapshot());
        if (StringUtils.isEmpty(customPrompt))
        {
            return basePrompt;
        }
        return basePrompt + " 用户补充要求：" + customPrompt;
    }

    private String normalizePromptSnapshot(String prompt)
    {
        String normalized = StringUtils.trimToEmpty(prompt);
        if (StringUtils.isEmpty(normalized))
        {
            return null;
        }
        return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
    }

    private ReviewOutcome parseReviewOutcome(String response) throws JsonProcessingException
    {
        JsonNode root = objectMapper.readTree(response);
        JsonNode directDecision = root.get("decision");
        if (directDecision != null)
        {
            return buildOutcomeFromJson(root, response);
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty())
        {
            choices = root.path("output").path("choices");
        }
        if (!choices.isArray() || choices.isEmpty())
        {
            throw new IllegalStateException("missing choices in review response");
        }
        JsonNode message = choices.get(0).path("message");
        String content = extractMessageContent(message.path("content"));
        if (StringUtils.isEmpty(content))
        {
            throw new IllegalStateException("empty message content in review response");
        }

        String normalized = normalizeJsonContent(content);
        try
        {
            JsonNode contentJson = objectMapper.readTree(normalized);
            return buildOutcomeFromJson(contentJson, response);
        }
        catch (Exception ignored)
        {
            ReviewOutcome fallback = new ReviewOutcome();
            fallback.decision = AiReviewResult.DECISION_UNCERTAIN;
            fallback.confidence = 0D;
            fallback.falsePositiveScore = 0D;
            fallback.summary = content.length() > 2000 ? content.substring(0, 2000) : content;
            fallback.reason = "model returned non-json content";
            fallback.rawResponseJson = response;
            return fallback;
        }
    }

    private ReviewOutcome buildOutcomeFromJson(JsonNode json, String rawResponse) throws JsonProcessingException
    {
        ReviewOutcome outcome = new ReviewOutcome();
        outcome.decision = normalizeDecision(json.path("decision").asText(AiReviewResult.DECISION_UNCERTAIN));
        outcome.confidence = json.path("confidence").isNumber() ? json.path("confidence").asDouble() : 0D;
        outcome.falsePositiveScore = json.path("false_positive_score").isNumber() ? json.path("false_positive_score").asDouble() : 0D;
        outcome.summary = truncate(json.path("summary").asText(""), 2000);

        // observed / pose_match 不新增数据库列，拼进 reason 一起留档，
        // 这样前端不用改也能看到「模型到底看到了什么」。
        String reason = json.path("reason").asText("");
        String observed = json.path("observed").asText("");
        if (StringUtils.isNotEmpty(observed))
        {
            reason = "看到：" + observed + (StringUtils.isEmpty(reason) ? "" : "；判断依据：" + reason);
        }
        if (json.has("pose_match") && !json.path("pose_match").isNull())
        {
            boolean poseMatch = json.path("pose_match").asBoolean(true);
            reason = (poseMatch ? "姿态与算法一致" : "姿态与算法不一致") + (StringUtils.isEmpty(reason) ? "" : "；" + reason);
        }
        outcome.reason = truncate(reason, 4000);
        outcome.rawResponseJson = rawResponse;
        return outcome;
    }

    private String extractMessageContent(JsonNode contentNode)
    {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull())
        {
            return "";
        }
        if (contentNode.isTextual())
        {
            return contentNode.asText();
        }
        if (contentNode.isArray())
        {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode)
            {
                if (item.path("text").isTextual())
                {
                    if (builder.length() > 0)
                    {
                        builder.append('\n');
                    }
                    builder.append(item.path("text").asText());
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    private String normalizeJsonContent(String content)
    {
        String trimmed = StringUtils.trimToEmpty(content);
        if (trimmed.startsWith("```") && trimmed.endsWith("```"))
        {
            trimmed = trimmed.replaceFirst("^```json", "").replaceFirst("^```", "");
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private String normalizeDecision(String decision)
    {
        if (AiReviewResult.DECISION_TRUE_ALARM.equals(decision)
            || AiReviewResult.DECISION_FALSE_ALARM.equals(decision)
            || AiReviewResult.DECISION_UNCERTAIN.equals(decision))
        {
            return decision;
        }
        return AiReviewResult.DECISION_UNCERTAIN;
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static class ReviewOutcome
    {
        private String decision;
        private Double confidence;
        private Double falsePositiveScore;
        private String summary;
        private String reason;
        private String rawResponseJson;
    }

    private static class ImagePayload
    {
        private final byte[] bytes;
        private final String mediaType;

        private ImagePayload(byte[] bytes, String mediaType)
        {
            this.bytes = bytes;
            this.mediaType = mediaType;
        }
    }
}