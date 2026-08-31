package com.ruoyi.web.service.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.waring.domain.SvaServer;
import com.ruoyi.waring.mapper.SvaServerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlgorithmServerMonitorService
{
    private static final Logger log = LoggerFactory.getLogger(AlgorithmServerMonitorService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private SvaServerMapper svaServerMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    public Map<String, Object> listControls()
    {
        List<SvaServer> servers = svaServerMapper.selectEnabledList();
        List<Map<String, Object>> controls = new ArrayList<>();

        int serverTotal = servers.size();
        int serverSuccess = 0;
        int serverFailed = 0;

        for (SvaServer server : servers)
        {
            String url = buildControlsUrl(server);
            if (StringUtils.isBlank(url))
            {
                serverFailed++;
                log.warn("查询算法服务器布控失败，SVA配置不完整, serverId={}, serverName={}",
                    server.getId(), server.getName());
                continue;
            }

            try
            {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> requestEntity = new HttpEntity<>("{}", headers);
                ResponseEntity<String> response = ensureRestTemplate().exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
                if (!response.getStatusCode().is2xxSuccessful())
                {
                    serverFailed++;
                    log.warn("查询算法服务器布控失败，HTTP状态码异常, serverId={}, serverName={}, status={}, url={}",
                        server.getId(), server.getName(), response.getStatusCode().value(), maskSensitiveUrl(url));
                    continue;
                }

                String body = response.getBody();
                if (StringUtils.isBlank(body))
                {
                    serverFailed++;
                    log.warn("查询算法服务器布控失败，响应为空, serverId={}, serverName={}, url={}",
                        server.getId(), server.getName(), maskSensitiveUrl(url));
                    continue;
                }

                JsonNode root = OBJECT_MAPPER.readTree(body);
                int code = parseCode(root.path("code"));
                if (code != 1000)
                {
                    serverFailed++;
                    log.warn("查询算法服务器布控失败，业务返回异常, serverId={}, serverName={}, code={}, msg={}, url={}",
                        server.getId(), server.getName(), code, root.path("msg").asText(""), maskSensitiveUrl(url));
                    continue;
                }

                JsonNode dataNode = root.path("data");
                if (!dataNode.isArray())
                {
                    serverFailed++;
                    log.warn("查询算法服务器布控失败，data非数组, serverId={}, serverName={}, url={}",
                        server.getId(), server.getName(), maskSensitiveUrl(url));
                    continue;
                }

                for (JsonNode item : dataNode)
                {
                    controls.add(toControlItem(item, server));
                }
                serverSuccess++;
            }
            catch (Exception ex)
            {
                serverFailed++;
                log.error("查询算法服务器布控异常, serverId={}, serverName={}, url={}",
                    server.getId(), server.getName(), maskSensitiveUrl(url), ex);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverTotal", serverTotal);
        result.put("serverSuccess", serverSuccess);
        result.put("serverFailed", serverFailed);
        result.put("taskTotal", controls.size());
        result.put("controls", controls);
        return result;
    }

    private Map<String, Object> toControlItem(JsonNode item, SvaServer server)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", readJsonValue(item, "code"));
        result.put("streamUrl", readJsonValue(item, "streamUrl"));
        result.put("algorithmCode", readJsonValue(item, "algorithmCode"));
        result.put("checkFps", readJsonValue(item, "checkFps"));
        result.put("detectFps", readJsonValue(item, "detectFps"));
        result.put("startTimestamp", readJsonValue(item, "startTimestamp"));
        result.put("pushStream", readJsonValue(item, "pushStream"));
        result.put("deployment_id", readJsonValueWithFallback(item, "deployment_id", "deploymentId"));
        result.put("task_name", readJsonValueWithFallback(item, "task_name", "taskName"));
        result.put("device_id", readJsonValueWithFallback(item, "device_id", "deviceId"));
        result.put("serverId", server == null ? null : server.getId());
        result.put("serverName", server == null ? null : server.getName());
        return result;
    }

    private String buildControlsUrl(SvaServer server)
    {
        if (server == null || StringUtils.isBlank(server.getHost()) || server.getAnalyzer_port() == null)
        {
            return null;
        }
        return "http://" + server.getHost().trim() + ":" + server.getAnalyzer_port() + "/api/controls";
    }

    private RestTemplate ensureRestTemplate()
    {
        if (restTemplate == null)
        {
            restTemplate = new RestTemplate();
        }
        return restTemplate;
    }

    private int parseCode(JsonNode codeNode)
    {
        if (codeNode == null || codeNode.isMissingNode() || codeNode.isNull())
        {
            return -1;
        }
        if (codeNode.isInt() || codeNode.isLong())
        {
            return codeNode.asInt();
        }
        try
        {
            return Integer.parseInt(codeNode.asText("-1"));
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    private Object readJsonValue(JsonNode node, String fieldName)
    {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull())
        {
            return null;
        }
        if (fieldNode.isTextual())
        {
            return fieldNode.asText();
        }
        if (fieldNode.isInt() || fieldNode.isLong())
        {
            return fieldNode.asLong();
        }
        if (fieldNode.isFloat() || fieldNode.isDouble() || fieldNode.isBigDecimal())
        {
            return fieldNode.asDouble();
        }
        if (fieldNode.isBoolean())
        {
            return fieldNode.asBoolean();
        }
        return fieldNode.toString();
    }

    private Object readJsonValueWithFallback(JsonNode node, String primaryFieldName, String fallbackFieldName)
    {
        Object primaryValue = readJsonValue(node, primaryFieldName);
        if (primaryValue != null)
        {
            return primaryValue;
        }
        return readJsonValue(node, fallbackFieldName);
    }

    private String maskSensitiveUrl(String url)
    {
        if (StringUtils.isBlank(url))
        {
            return url;
        }
        return url.replaceAll("(?i)([?&](secret|token|access_token|auth|sign|signature)=)[^&]*", "$1***");
    }
}
