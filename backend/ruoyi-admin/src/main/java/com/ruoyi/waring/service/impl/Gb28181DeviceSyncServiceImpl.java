package com.ruoyi.waring.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.domain.ZlmServer;
import com.ruoyi.waring.mapper.HDeviceMapper;
import com.ruoyi.waring.mapper.ZlmServerMapper;
import com.ruoyi.waring.service.HDeviceService;
import com.ruoyi.waring.service.IGb28181DeviceSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 国标设备同步：优先拉 WVP 已注册设备/通道；并保留 ZLM listRtpServer 媒体兜底。
 * 演示机 ZLM 无内置 SIP；WVP 提供 5060，ZLM 只做媒体。
 */
@Service
public class Gb28181DeviceSyncServiceImpl implements IGb28181DeviceSyncService {

    private static final Logger log = LoggerFactory.getLogger(Gb28181DeviceSyncServiceImpl.class);

    private static final String DEVICE_TYPE_GB28181 = "gb28181";
    private static final String STREAM_SOURCE_PLATFORM = "PLATFORM";
    private static final String DEFAULT_PLATFORM_PREFIX = "zlm-";
    private static final String RTP_APP = "rtp";

    @Value("${gb28181.wvp.enabled:false}")
    private boolean wvpEnabled;

    @Value("${gb28181.wvp.base-url:http://127.0.0.1:18080}")
    private String wvpBaseUrl;

    @Value("${gb28181.wvp.username:admin}")
    private String wvpUsername;

    @Value("${gb28181.wvp.password:admin}")
    private String wvpPassword;

    @Value("${gb28181.wvp.platform-id:}")
    private String wvpPlatformId;

    @Autowired
    private ZlmServerMapper zlmServerMapper;

    @Autowired
    private HDeviceMapper hDeviceMapper;

    @Autowired
    private HDeviceService hDeviceService;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> syncFromZlm() {
        Map<String, Object> result = new LinkedHashMap<>();
        int inserted = 0;
        int updated = 0;
        int failed = 0;

        ZlmServer zlmServer = resolveEnabledZlmServer();
        if (zlmServer == null) {
            return notReady(result, "未找到可用 ZLM 服务器配置（zlm_server.enabled=1）");
        }

        // 先探活 ZLM（验收要求 backend 调 ZLM）
        JsonNode rtpList;
        JsonNode mediaList;
        try {
            rtpList = getJson(buildApiUrl(zlmServer, "/index/api/listRtpServer"));
            mediaList = getJson(buildApiUrl(zlmServer, "/index/api/getMediaList"));
        } catch (Exception ex) {
            log.warn("调用 ZLM 国标同步 API 失败: {}", ex.getMessage());
            return notReady(result, "ZLM 不可达或 API 失败: " + ex.getMessage());
        }

        boolean rtpOk = parseCode(rtpList.path("code")) == 0;
        boolean mediaOk = parseCode(mediaList.path("code")) == 0;
        if (!rtpOk && !mediaOk) {
            return notReady(result, "ZLM 返回错误，无法同步国标设备");
        }

        Set<String> seen = new HashSet<>();
        String mode = wvpEnabled ? "WVP+RTP" : "RTP";

        if (wvpEnabled) {
            try {
                int[] wvpCounts = syncFromWvp(zlmServer, mediaList, seen);
                inserted += wvpCounts[0];
                updated += wvpCounts[1];
                failed += wvpCounts[2];
            } catch (Exception ex) {
                log.warn("WVP 国标目录同步失败，回退 listRtpServer: {}", ex.getMessage());
                mode = "RTP(WVP不可用:" + ex.getMessage() + ")";
            }
        }

        String platformId = StringUtils.isNotBlank(wvpPlatformId)
            ? wvpPlatformId
            : DEFAULT_PLATFORM_PREFIX + (zlmServer.getId() == null ? "1" : zlmServer.getId());

        if (rtpOk) {
            JsonNode rtpData = rtpList.path("data");
            if (rtpData.isArray()) {
                for (JsonNode item : rtpData) {
                    String streamId = firstText(item, "stream_id", "stream", "streamId");
                    if (StringUtils.isBlank(streamId) || !seen.add(streamId)) {
                        continue;
                    }
                    int[] counts = upsertOne(zlmServer, platformId, streamId, streamId,
                        "国标-" + streamId, mediaHasStream(mediaList, streamId));
                    inserted += counts[0];
                    updated += counts[1];
                    failed += counts[2];
                }
            }
        }

        if (mediaOk) {
            JsonNode mediaData = mediaList.path("data");
            if (mediaData.isArray()) {
                for (JsonNode item : mediaData) {
                    if (!RTP_APP.equalsIgnoreCase(firstText(item, "app"))) {
                        continue;
                    }
                    String streamId = firstText(item, "stream");
                    if (StringUtils.isBlank(streamId) || !seen.add(streamId)) {
                        continue;
                    }
                    int[] counts = upsertOne(zlmServer, platformId, streamId, streamId,
                        "国标-" + streamId, true);
                    inserted += counts[0];
                    updated += counts[1];
                    failed += counts[2];
                }
            }
        }

        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("ready", true);
        if (seen.isEmpty()) {
            if (wvpEnabled) {
                result.put("message",
                    "ZLM 已连通；WVP 已启用但暂无设备。请用模拟器注册到 SIP 5060，或先 openRtpServer 做媒体兜底。");
            } else {
                result.put("message",
                    "ZLM 已连通，当前无国标 RTP 收流。请先调用 openRtpServer（或脚本 scripts/open_gb_rtp.sh）再同步。");
            }
        } else {
            result.put("message",
                "已同步国标设备（mode=" + mode + "，device_type=gb28181）");
        }
        return result;
    }

    /**
     * 从 WVP 拉设备+通道目录。
     * @return int[3] = {inserted, updated, failed}
     */
    private int[] syncFromWvp(ZlmServer zlmServer, JsonNode mediaList, Set<String> seen) throws Exception {
        int inserted = 0;
        int updated = 0;
        int failed = 0;

        // 探活：勿请求 /（WVP 根路径常 404）；直接登录即可
        String token = loginWvp();
        String platformId = StringUtils.isNotBlank(wvpPlatformId)
            ? wvpPlatformId
            : "wvp-" + (zlmServer.getId() == null ? "1" : zlmServer.getId());

        JsonNode devices = wvpGet("/api/device/query/devices?page=1&count=200", token);
        if (parseCode(devices.path("code")) != 0) {
            throw new IllegalStateException("devices API code=" + devices.path("code").asText());
        }
        JsonNode list = devices.path("data").path("list");
        if (!list.isArray()) {
            list = devices.path("data");
        }
        if (!list.isArray()) {
            return new int[]{0, 0, 0};
        }

        for (JsonNode device : list) {
            String deviceId = firstText(device, "deviceId", "device_id", "id");
            if (StringUtils.isBlank(deviceId)) {
                continue;
            }
            boolean deviceOnline = asOnline(device);
            String deviceName = firstText(device, "name", "deviceName");
            if (StringUtils.isBlank(deviceName)) {
                deviceName = "国标-" + deviceId;
            }

            JsonNode channelsResp = wvpGet(
                "/api/device/query/devices/" + deviceId + "/channels?page=1&count=200", token);
            JsonNode channels = channelsResp.path("data").path("list");
            if (!channels.isArray()) {
                channels = channelsResp.path("data");
            }

            if (channels.isArray() && channels.size() > 0) {
                for (JsonNode ch : channels) {
                    String channelId = firstText(ch, "channelId", "channel_id", "deviceId", "id");
                    if (StringUtils.isBlank(channelId) || !seen.add(channelId)) {
                        continue;
                    }
                    String chName = firstText(ch, "name", "channelName");
                    if (StringUtils.isBlank(chName)) {
                        chName = deviceName + "-" + channelId;
                    }
                    boolean online = deviceOnline && asOnline(ch);
                    boolean mediaAlive = mediaHasStream(mediaList, channelId)
                        || mediaHasStream(mediaList, deviceId);
                    int[] counts = upsertOne(zlmServer, platformId, channelId, deviceId, chName,
                        online || mediaAlive);
                    inserted += counts[0];
                    updated += counts[1];
                    failed += counts[2];
                }
            } else if (seen.add(deviceId)) {
                int[] counts = upsertOne(zlmServer, platformId, deviceId, deviceId, deviceName,
                    deviceOnline || mediaHasStream(mediaList, deviceId));
                inserted += counts[0];
                updated += counts[1];
                failed += counts[2];
            }
        }
        return new int[]{inserted, updated, failed};
    }

    private String loginWvp() throws Exception {
        String url = trimSlash(wvpBaseUrl) + "/api/user/login";
        // WVP 2.7.x 登录多为 GET，password 为 32 位 MD5；也兼容明文 / POST JSON
        String[] passwords = new String[]{wvpPassword, md5Hex(wvpPassword)};
        JsonNode root = null;
        for (String pass : passwords) {
            String qUrl = UriComponentsBuilder.fromUriString(url)
                .queryParam("username", wvpUsername)
                .queryParam("password", pass)
                .build(true).toUriString();
            try {
                String qBody = ensureRestTemplate().getForObject(qUrl, String.class);
                root = objectMapper.readTree(qBody == null ? "{}" : qBody);
                if (parseCode(root.path("code")) == 0) {
                    break;
                }
            } catch (Exception ignore) {
                // try next
            }
            try {
                Map<String, String> body = new HashMap<>();
                body.put("username", wvpUsername);
                body.put("password", pass);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<String> resp = ensureRestTemplate().exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
                root = objectMapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
                if (parseCode(root.path("code")) == 0) {
                    break;
                }
            } catch (Exception ignore) {
                // try next
            }
        }
        if (root == null) {
            throw new IllegalStateException("WVP 登录失败");
        }
        String token = firstText(root.path("data"), "accessToken", "token", "Authorization");
        if (StringUtils.isBlank(token)) {
            token = firstText(root, "accessToken", "token");
        }
        if (StringUtils.isBlank(token)) {
            throw new IllegalStateException("WVP 登录失败，无 token: " + root.path("msg").asText());
        }
        return token;
    }

    private String md5Hex(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        // 已是 32 位 hex 则视为已哈希
        if (raw.matches("(?i)^[a-f0-9]{32}$")) {
            return raw;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return raw;
        }
    }

    private JsonNode wvpGet(String pathAndQuery, String token) throws Exception {
        String url = trimSlash(wvpBaseUrl) + pathAndQuery;
        HttpHeaders headers = new HttpHeaders();
        headers.set("access-token", token);
        headers.setBearerAuth(token);
        ResponseEntity<String> resp = ensureRestTemplate().exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return objectMapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
    }

    private Map<String, Object> notReady(Map<String, Object> result, String message) {
        result.put("inserted", 0);
        result.put("updated", 0);
        result.put("failed", 0);
        result.put("ready", false);
        result.put("message", message);
        return result;
    }

    /**
     * @return int[3] = {inserted, updated, failed}
     */
    private int[] upsertOne(ZlmServer zlmServer, String platformId, String gbDeviceId,
                            String streamId, String name, boolean online) {
        try {
            boolean existed = hDeviceMapper.selectDeviceByGbDeviceId(gbDeviceId, platformId) != null;
            HDevice probe = new HDevice();
            probe.setGb_device_id(gbDeviceId);
            probe.setGb_platform_id(platformId);
            probe.setDevice_type(DEVICE_TYPE_GB28181);
            probe.setStream_source_type(STREAM_SOURCE_PLATFORM);
            probe.setName(name);
            probe.setIs_online(online ? "1" : "0");
            probe.setZlm_server_id(zlmServer.getId() == null ? 1L : zlmServer.getId());
            probe.setSva_server_id(1L);
            probe.setOrg_name("国标同步");
            probe.setOrg_index("10");
            probe.setPlay_url(buildPlayUrl(zlmServer, streamId));
            if (!existed) {
                probe.setApe_id(buildApeId(gbDeviceId));
            }
            hDeviceService.upsertGb28181Device(probe);
            return existed ? new int[]{0, 1, 0} : new int[]{1, 0, 0};
        } catch (Exception ex) {
            log.warn("同步国标设备失败 gbDeviceId={}: {}", gbDeviceId, ex.getMessage());
            return new int[]{0, 0, 1};
        }
    }

    private String buildApeId(String streamId) {
        String apeId = "camgb" + Integer.toHexString(streamId.hashCode()).replace("-", "n");
        return apeId.length() > 32 ? apeId.substring(0, 32) : apeId;
    }

    private boolean mediaHasStream(JsonNode mediaList, String streamId) {
        JsonNode data = mediaList.path("data");
        if (!data.isArray() || StringUtils.isBlank(streamId)) {
            return false;
        }
        for (JsonNode item : data) {
            if (streamId.equals(firstText(item, "stream"))) {
                return true;
            }
        }
        return false;
    }

    private boolean asOnline(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        JsonNode onLine = node.get("onLine");
        if (onLine != null && !onLine.isNull()) {
            if (onLine.isBoolean()) {
                return onLine.asBoolean();
            }
            String t = onLine.asText("").trim();
            return "1".equals(t) || "true".equalsIgnoreCase(t) || "online".equalsIgnoreCase(t);
        }
        JsonNode status = node.get("status");
        if (status != null && !status.isNull()) {
            if (status.isBoolean()) {
                return status.asBoolean();
            }
            String t = status.asText("").trim();
            return "1".equals(t) || "ON".equalsIgnoreCase(t) || "ONLINE".equalsIgnoreCase(t);
        }
        return false;
    }

    private String buildPlayUrl(ZlmServer zlmServer, String streamId) {
        String host = zlmServer.getHost();
        Integer httpPort = zlmServer.getMedia_http_port() != null
            ? zlmServer.getMedia_http_port()
            : zlmServer.getApi_port();
        return "ws://" + host + ":" + httpPort + "/" + RTP_APP + "/" + streamId + ".live.flv";
    }

    private ZlmServer resolveEnabledZlmServer() {
        List<ZlmServer> list = zlmServerMapper.selectEnabledList();
        if (list == null || list.isEmpty()) {
            return zlmServerMapper.selectEnabledById(1L);
        }
        return list.get(0);
    }

    private String buildApiUrl(ZlmServer server, String path) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString("http://" + server.getHost().trim() + ":" + server.getApi_port() + path);
        if (StringUtils.isNotBlank(server.getSecret())) {
            builder.queryParam("secret", server.getSecret());
        }
        return builder.build(true).toUriString();
    }

    private JsonNode getJson(String url) throws Exception {
        String body = ensureRestTemplate().getForObject(url, String.class);
        if (StringUtils.isBlank(body)) {
            throw new IllegalStateException("empty response from " + url);
        }
        return objectMapper.readTree(body);
    }

    private RestTemplate ensureRestTemplate() {
        if (restTemplate == null) {
            // 避开演示机 Clash/系统代理劫持 127.0.0.1
            System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
            System.setProperty("https.nonProxyHosts", "localhost|127.*|[::1]");
            restTemplate = new RestTemplate();
        }
        return restTemplate;
    }

    private String trimSlash(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private int parseCode(JsonNode codeNode) {
        if (codeNode == null || codeNode.isMissingNode() || codeNode.isNull()) {
            return -1;
        }
        if (codeNode.isNumber()) {
            return codeNode.asInt();
        }
        try {
            return Integer.parseInt(codeNode.asText("-1"));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String field : fields) {
            JsonNode child = node.path(field);
            if (!child.isMissingNode() && !child.isNull()) {
                String text = child.asText("").trim();
                if (StringUtils.isNotBlank(text)) {
                    return text;
                }
            }
        }
        return "";
    }
}
