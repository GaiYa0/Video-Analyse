package com.ruoyi.web.service.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.domain.ZlmServer;
import com.ruoyi.waring.mapper.HDeviceMapper;
import com.ruoyi.waring.mapper.ZlmServerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MediaStreamMonitorService
{
    private static final Logger log = LoggerFactory.getLogger(MediaStreamMonitorService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern STREAM_NAME_PATTERN = Pattern.compile("[^A-Za-z0-9_-]");
    private static final Pattern PLAY_URL_STREAM_PATTERN = Pattern.compile("/([^/\\?#]+?)(?:\\.[^/\\?#.]+)*(?:\\?.*)?$");

    @Autowired
    private HDeviceMapper hDeviceMapper;

    @Autowired
    private ZlmServerMapper zlmServerMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    public Map<String, Object> listStreams()
    {
        List<ZlmServer> servers = zlmServerMapper.selectEnabledList();
        Map<String, String> streamDeviceNameMap = buildStreamDeviceNameMap();
        Map<String, StreamAggregate> streamAggregateMap = new LinkedHashMap<>();

        int serverTotal = servers.size();
        int serverSuccess = 0;
        int serverFailed = 0;

        for (ZlmServer server : servers)
        {
            String url = buildMediaListUrl(server);
            if (StringUtils.isBlank(url))
            {
                serverFailed++;
                log.warn("查询媒体流失败，ZLM配置不完整, serverId={}, serverName={}",
                    server.getId(), server.getName());
                continue;
            }

            try
            {
                ResponseEntity<String> response = ensureRestTemplate().getForEntity(url, String.class);
                if (!response.getStatusCode().is2xxSuccessful())
                {
                    serverFailed++;
                    log.warn("查询媒体流失败，HTTP状态码异常, serverId={}, serverName={}, status={}, url={}",
                        server.getId(), server.getName(), response.getStatusCode().value(), maskSensitiveUrl(url));
                    continue;
                }

                String body = response.getBody();
                if (StringUtils.isBlank(body))
                {
                    serverFailed++;
                    log.warn("查询媒体流失败，响应为空, serverId={}, serverName={}, url={}",
                        server.getId(), server.getName(), maskSensitiveUrl(url));
                    continue;
                }

                JsonNode root = OBJECT_MAPPER.readTree(body);
                int code = parseCode(root.path("code"));
                if (code != 0)
                {
                    serverFailed++;
                    log.warn("查询媒体流失败，业务返回异常, serverId={}, serverName={}, code={}, msg={}, url={}",
                        server.getId(), server.getName(), code, root.path("msg").asText(""), maskSensitiveUrl(url));
                    continue;
                }

                JsonNode dataNode = root.path("data");
                if (dataNode.isArray())
                {
                    for (JsonNode item : dataNode)
                    {
                        mergeMediaItem(item, streamAggregateMap);
                    }
                }
                serverSuccess++;
            }
            catch (Exception ex)
            {
                serverFailed++;
                log.error("查询媒体流异常, serverId={}, serverName={}, url={}",
                    server.getId(), server.getName(), maskSensitiveUrl(url), ex);
            }
        }

        List<Map<String, Object>> streams = new ArrayList<>();
        for (StreamAggregate aggregate : streamAggregateMap.values())
        {
            Map<String, Object> streamItem = aggregate.toMap();
            streamItem.put("deviceName", streamDeviceNameMap.get(aggregate.getStream()));
            streams.add(streamItem);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("streamTotal", streams.size());
        result.put("serverTotal", serverTotal);
        result.put("serverSuccess", serverSuccess);
        result.put("serverFailed", serverFailed);
        result.put("streams", streams);
        return result;
    }

    private Map<String, String> buildStreamDeviceNameMap()
    {
        Map<String, String> streamDeviceNameMap = new LinkedHashMap<>();
        List<HDevice> devices = hDeviceMapper.selectDeviceList(new HDevice());
        if (devices == null || devices.isEmpty())
        {
            return streamDeviceNameMap;
        }

        for (HDevice device : devices)
        {
            if (device == null || StringUtils.isBlank(device.getName()))
            {
                continue;
            }
            String deviceName = device.getName().trim();

            bindDeviceName(streamDeviceNameMap, device.getApe_id(), deviceName);
            bindDeviceName(streamDeviceNameMap, sanitizeStreamName(device.getApe_id()), deviceName);
            bindDeviceName(streamDeviceNameMap, parseStreamFromPlayUrl(device.getPlay_url()), deviceName);
        }

        return streamDeviceNameMap;
    }

    private void bindDeviceName(Map<String, String> targetMap, String stream, String deviceName)
    {
        if (StringUtils.isBlank(stream) || StringUtils.isBlank(deviceName))
        {
            return;
        }
        targetMap.putIfAbsent(stream.trim(), deviceName);
    }

    private String sanitizeStreamName(String stream)
    {
        if (stream == null)
        {
            return null;
        }
        String sanitized = STREAM_NAME_PATTERN.matcher(stream).replaceAll("");
        return StringUtils.isBlank(sanitized) ? null : sanitized;
    }

    private String parseStreamFromPlayUrl(String playUrl)
    {
        if (StringUtils.isBlank(playUrl))
        {
            return null;
        }

        Matcher matcher = PLAY_URL_STREAM_PATTERN.matcher(playUrl.trim());
        if (!matcher.find())
        {
            return null;
        }

        String stream = matcher.group(1);
        return StringUtils.isBlank(stream) ? null : stream;
    }

    private void mergeMediaItem(JsonNode item, Map<String, StreamAggregate> streamAggregateMap)
    {
        String stream = item.path("stream").asText("");
        if (StringUtils.isBlank(stream))
        {
            return;
        }

        StreamAggregate aggregate = streamAggregateMap.computeIfAbsent(stream, StreamAggregate::new);
        aggregate.mergeName(firstText(item, "name", "stream", "streamName", "displayName"));

        long onlineCount = firstLong(item, "totalReaderCount", "readerCount");
        aggregate.onlineCount = Math.max(aggregate.onlineCount, onlineCount);

        long ingressBps = firstLong(item, "bytesSpeed");
        aggregate.ingressBps = Math.max(aggregate.ingressBps, ingressBps);

        JsonNode tracksNode = item.path("tracks");
        if (!tracksNode.isArray())
        {
            return;
        }

        for (JsonNode trackNode : tracksNode)
        {
            int codecType = parseCodecType(trackNode.path("codec_type"));
            if (codecType == 0)
            {
                aggregate.mergeVideoInfo(extractVideoInfo(trackNode));
            }
            else if (codecType == 1)
            {
                aggregate.mergeAudioInfo(extractAudioInfo(trackNode));
            }
        }
    }

    private Map<String, Object> extractVideoInfo(JsonNode trackNode)
    {
        Map<String, Object> videoInfo = new LinkedHashMap<>();
        putIfPresent(videoInfo, "codec", firstText(trackNode, "codec", "codec_id_name", "codecIdName"));
        putIfPresent(videoInfo, "width", firstLong(trackNode, "width"));
        putIfPresent(videoInfo, "height", firstLong(trackNode, "height"));
        putIfPresent(videoInfo, "fps", firstDouble(trackNode, "fps"));
        return videoInfo;
    }

    private Map<String, Object> extractAudioInfo(JsonNode trackNode)
    {
        Map<String, Object> audioInfo = new LinkedHashMap<>();
        putIfPresent(audioInfo, "codec", firstText(trackNode, "codec", "codec_id_name", "codecIdName"));
        putIfPresent(audioInfo, "sampleRate", firstLong(trackNode, "sample_rate", "sampleRate"));
        putIfPresent(audioInfo, "channels", firstLong(trackNode, "channels", "channel"));
        putIfPresent(audioInfo, "sampleBit", firstLong(trackNode, "sample_bit", "sampleBit"));
        return audioInfo;
    }

    private String buildMediaListUrl(ZlmServer server)
    {
        if (server == null || StringUtils.isBlank(server.getHost()) || server.getApi_port() == null)
        {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString("http://" + server.getHost().trim() + ":" + server.getApi_port() + "/index/api/getMediaList");

        if (StringUtils.isNotBlank(server.getSecret()))
        {
            builder.queryParam("secret", server.getSecret());
        }

        return builder.build(true).toUriString();
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

    private int parseCodecType(JsonNode codecTypeNode)
    {
        if (codecTypeNode == null || codecTypeNode.isMissingNode() || codecTypeNode.isNull())
        {
            return -1;
        }
        if (codecTypeNode.isInt() || codecTypeNode.isLong())
        {
            return codecTypeNode.asInt();
        }
        String value = codecTypeNode.asText("").trim();
        if ("video".equalsIgnoreCase(value))
        {
            return 0;
        }
        if ("audio".equalsIgnoreCase(value))
        {
            return 1;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    private long firstLong(JsonNode node, String... fieldNames)
    {
        for (String fieldName : fieldNames)
        {
            JsonNode fieldNode = node.path(fieldName);
            if (fieldNode.isMissingNode() || fieldNode.isNull())
            {
                continue;
            }
            if (fieldNode.isNumber())
            {
                return Math.max(0L, fieldNode.asLong());
            }
            String text = fieldNode.asText("").trim();
            if (StringUtils.isBlank(text))
            {
                continue;
            }
            try
            {
                if (text.contains("."))
                {
                    return Math.max(0L, (long) Double.parseDouble(text));
                }
                return Math.max(0L, Long.parseLong(text));
            }
            catch (NumberFormatException ex)
            {
                // ignore invalid numeric field
            }
        }
        return 0L;
    }

    private Double firstDouble(JsonNode node, String... fieldNames)
    {
        for (String fieldName : fieldNames)
        {
            JsonNode fieldNode = node.path(fieldName);
            if (fieldNode.isMissingNode() || fieldNode.isNull())
            {
                continue;
            }
            if (fieldNode.isNumber())
            {
                return fieldNode.asDouble();
            }
            String text = fieldNode.asText("").trim();
            if (StringUtils.isBlank(text))
            {
                continue;
            }
            try
            {
                return Double.parseDouble(text);
            }
            catch (NumberFormatException ex)
            {
                // ignore invalid numeric field
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fieldNames)
    {
        for (String fieldName : fieldNames)
        {
            JsonNode fieldNode = node.path(fieldName);
            if (fieldNode.isMissingNode() || fieldNode.isNull())
            {
                continue;
            }
            String value = fieldNode.asText("").trim();
            if (StringUtils.isNotBlank(value))
            {
                return value;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value)
    {
        if (value == null)
        {
            return;
        }
        if (value instanceof String && StringUtils.isBlank((String) value))
        {
            return;
        }
        target.put(key, value);
    }

    private String maskSensitiveUrl(String url)
    {
        if (StringUtils.isBlank(url))
        {
            return url;
        }
        return url.replaceAll("(?i)([?&](secret|token|access_token|auth|sign|signature)=)[^&]*", "$1***");
    }

    private static final class StreamAggregate
    {
        private final String stream;
        private String name;
        private long onlineCount;
        private long ingressBps;
        private Map<String, Object> videoInfo;
        private Map<String, Object> audioInfo;

        private StreamAggregate(String stream)
        {
            this.stream = stream;
            this.name = stream;
            this.onlineCount = 0L;
            this.ingressBps = 0L;
        }

        private void mergeName(String incoming)
        {
            if (StringUtils.isBlank(incoming))
            {
                return;
            }
            if (StringUtils.isBlank(this.name) || this.stream.equals(this.name))
            {
                this.name = incoming;
            }
        }

        private void mergeVideoInfo(Map<String, Object> incoming)
        {
            this.videoInfo = mergeInfo(this.videoInfo, incoming);
        }

        private void mergeAudioInfo(Map<String, Object> incoming)
        {
            this.audioInfo = mergeInfo(this.audioInfo, incoming);
        }

        private Map<String, Object> toMap()
        {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stream", stream);
            result.put("name", StringUtils.isNotBlank(name) ? name : stream);
            result.put("onlineCount", onlineCount);
            result.put("ingressBps", ingressBps);
            result.put("videoInfo", videoInfo);
            result.put("audioInfo", audioInfo);
            return result;
        }

        private Map<String, Object> mergeInfo(Map<String, Object> current, Map<String, Object> incoming)
        {
            if (incoming == null || incoming.isEmpty())
            {
                return current;
            }
            if (current == null)
            {
                return new LinkedHashMap<>(incoming);
            }
            for (Map.Entry<String, Object> entry : incoming.entrySet())
            {
                if (!current.containsKey(entry.getKey()) || current.get(entry.getKey()) == null)
                {
                    current.put(entry.getKey(), entry.getValue());
                }
            }
            return current;
        }

        private String getStream()
        {
            return stream;
        }
    }
}