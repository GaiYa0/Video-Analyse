package com.ruoyi.waring.service;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.waring.domain.HWaring;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 严重睡岗（15s 档）的站外通知。
 *
 * 只在等级升级到「严重」时推一次，走企业微信群机器人格式。
 * 没配地址就只打日志，绝不因为通知失败影响告警落库。
 */
@Service
public class SvaSleepNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(SvaSleepNotifyService.class);

    /** 总开关，取值 true / false。 */
    public static final String CONFIG_KEY_ENABLED = "sva.sleep.webhook.enabled";
    /** 群机器人地址，留空表示不推送。 */
    public static final String CONFIG_KEY_URL = "sva.sleep.webhook.url";

    private static final int TIMEOUT_MS = 5000;

    @Resource
    private ISysConfigService sysConfigService;

    @Resource
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 异步推送严重睡岗。调用方不等待，也不因为失败回滚落库。
     */
    public void notifySevere(HWaring waring, Long durationMs)
    {
        if (waring == null)
        {
            return;
        }
        if (!isEnabled())
        {
            log.debug("严重睡岗通知未启用, 跳过: eventId={}", waring.getId());
            return;
        }
        final String url = StringUtils.trimToEmpty(sysConfigService.selectConfigByKey(CONFIG_KEY_URL));
        if (url.isEmpty())
        {
            log.info("严重睡岗通知未配置 webhook 地址, 仅记录日志: eventId={} device={}",
                waring.getId(), waring.getDevice_name());
            return;
        }
        final String content = buildContent(waring, durationMs);
        try
        {
            threadPoolTaskExecutor.execute(() -> post(url, content));
        }
        catch (Exception ex)
        {
            // 线程池拒绝也不能影响告警落库。
            log.warn("严重睡岗通知提交失败: eventId={} {}", waring.getId(), ex.getMessage());
        }
    }

    private boolean isEnabled()
    {
        return Convert.toBool(sysConfigService.selectConfigByKey(CONFIG_KEY_ENABLED), false);
    }

    private String buildContent(HWaring waring, Long durationMs)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("【严重睡岗】");
        builder.append("设备：").append(StringUtils.defaultIfBlank(waring.getDevice_name(), waring.getDevice_id()));
        if (StringUtils.isNotBlank(waring.getOrg_name()))
        {
            builder.append("（").append(waring.getOrg_name()).append("）");
        }
        builder.append("\n时间：").append(StringUtils.trimToEmpty(waring.getAlarm_time()));
        if (durationMs != null && durationMs > 0)
        {
            builder.append("\n持续：").append(String.format("%.1f", durationMs / 1000.0)).append(" 秒");
        }
        if (waring.getSva_pitch_degree() != null)
        {
            builder.append("\n俯仰角：").append(String.format("%.0f", waring.getSva_pitch_degree())).append("°");
        }
        builder.append("\n事件：").append(StringUtils.trimToEmpty(waring.getId()));
        return builder.toString();
    }

    private void post(String url, String content)
    {
        try
        {
            Map<String, Object> text = new HashMap<>();
            text.put("content", content);
            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "text");
            payload.put("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            String response = buildRestTemplate().postForObject(URI.create(url), entity, String.class);
            log.info("严重睡岗通知已发送: response={}", response);
        }
        catch (Exception ex)
        {
            log.warn("严重睡岗通知发送失败: {}", ex.getMessage());
        }
    }

    private RestTemplate buildRestTemplate()
    {
        // 演示机开着 Clash 时，代理会劫持本机 webhook 地址。
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        System.setProperty("https.nonProxyHosts", "localhost|127.*|[::1]");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(TIMEOUT_MS));
        return new RestTemplate(factory);
    }
}
