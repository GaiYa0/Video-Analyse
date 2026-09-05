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
                    if (StringUtils.isBlank(streamId) || skipRtpResidue(streamId) || alreadyCovered(seen, streamId)
                        || !seen.add(streamId)) {
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
                    if (StringUtils.isBlank(streamId) || skipRtpResidue(streamId) || alreadyCovered(seen, streamId)
                        || !seen.add(streamId)) {
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
                    boolean online = deviceOnline && channelOnline(ch);
                    String playStream = deviceId + "_" + channelId;
                    seen.add(deviceId);
                    seen.add(playStream);
                    String urlStream = playStream;
                    if (!mediaHasStream(mediaList, playStream) && mediaHasStream(mediaList, channelId)) {
                        urlStream = channelId;
                    }
                    int[] counts = upsertOne(zlmServer, platformId, channelId, urlStream, chName, online);
                    inserted += counts[0];
                    updated += counts[1];
                    failed += counts[2];
                }
            } else if (seen.add(deviceId)) {
                int[] counts = upsertOne(zlmServer, platformId, deviceId, deviceId, deviceName, deviceOnline);
                inserted += counts[0];
                updated += counts[1];
                failed += counts[2];
            }
        }
        return new int[]{inserted, updated, failed};
    }

    @Override
    public boolean ensureRtpReady(String streamId) {
        return ensureRtpReadyInternal(streamId, true);
    }

    @Override
    public void warmRtp(String streamId) {
        if (StringUtils.isBlank(streamId)) {
            return;
        }
        Thread t = new Thread(() -> ensureRtpReadyInternal(streamId, false), "gb-warm-" + streamId);
        t.setDaemon(true);
        t.start();
    }

    private boolean ensureRtpReadyInternal(String streamId, boolean wait) {
        if (StringUtils.isBlank(streamId)) {
            return false;
        }
        ZlmServer zlmServer = resolveEnabledZlmServer();
        if (zlmServer == null) {
            log.warn("ensureRtpReady 无可用 ZLM, stream={}", streamId);
            return false;
        }
        try {
            if (zlmHasRtpStream(zlmServer, streamId)) {
                return true;
            }
            log.info("ZLM 无 rtp 流，触发 on_stream_not_found: {}", streamId);
            triggerStreamNotFound(zlmServer, streamId);
            if (wait && waitForRtpStream(zlmServer, streamId, 3)) {
                return true;
            }
            if (wvpEnabled) {
                log.info("hook 等待未就绪，回退 WVP play/start: {}", streamId);
                try {
                    playViaWvp(streamId);
                } catch (Exception ex) {
                    log.warn("WVP 点播失败 stream={}: {}", streamId, ex.getMessage());
                }
            }
            if (!wait) {
                return zlmHasRtpStream(zlmServer, streamId);
            }
            return waitForRtpStream(zlmServer, streamId, 8);
        } catch (Exception ex) {
            log.warn("ensureRtpReady 失败 stream={}: {}", streamId, ex.getMessage());
            return false;
        }
    }

    private boolean zlmHasRtpStream(ZlmServer zlmServer, String streamId) {
        try {
            JsonNode mediaList = getJson(buildApiUrl(zlmServer, "/index/api/getMediaList"));
            return parseCode(mediaList.path("code")) == 0 && mediaHasStream(mediaList, streamId);
        } catch (Exception ex) {
            log.debug("getMediaList 失败: {}", ex.getMessage());
            return false;
        }
    }

    private boolean waitForRtpStream(ZlmServer zlmServer, String streamId, int seconds) {
        for (int i = 0; i < seconds; i++) {
            if (zlmHasRtpStream(zlmServer, streamId)) {
                log.info("国标 rtp 已就绪 stream={} after {}s", streamId, i + 1);
                return true;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void triggerStreamNotFound(ZlmServer zlmServer, String streamId) {
        String host = zlmServer.getHost() == null ? "127.0.0.1" : zlmServer.getHost().trim();
        Integer httpPort = zlmServer.getMedia_http_port() != null
            ? zlmServer.getMedia_http_port()
            : zlmServer.getApi_port();
        if (httpPort == null) {
            return;
        }
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        String flv = "http://" + host + ":" + httpPort + "/" + RTP_APP + "/" + streamId + ".live.flv";
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(flv).openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.connect();
            conn.getResponseCode();
        } catch (Exception ignore) {
            // 短超时故意打断下载；ZLM 已发 on_stream_not_found
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void playViaWvp(String streamId) throws Exception {
        String deviceId;
        String channelId;
        int u = streamId.indexOf('_');
        if (u > 0) {
            deviceId = streamId.substring(0, u);
            channelId = streamId.substring(u + 1);
        } else {
            deviceId = streamId;
            channelId = streamId;
        }
        String token = loginWvp();
        String stopPath = "/api/play/stop/" + deviceId + "/" + channelId;
        String startPath = "/api/play/start/" + deviceId + "/" + channelId;
        Thread waiter = new Thread(() -> {
            try {
                try {
                    wvpGet(stopPath, token);
                    Thread.sleep(400L);
                } catch (Exception ignore) {
                    // 没有在播的会话时 stop 可能失败，继续 start
                }
                wvpGet(startPath, token);
            } catch (Exception ex) {
                log.warn("WVP play/start 异步调用结束 stream={}: {}", streamId, ex.getMessage());
            }
        }, "wvp-play-" + streamId);
        waiter.setDaemon(true);
        waiter.start();
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

    private boolean skipRtpResidue(String streamId) {
        // ZLM 在 WVP stream_replace 前会用 8 位 SSRC hex 当 stream_id
        return streamId.matches("(?i)^[0-9a-f]{8}$");
    }

    private boolean alreadyCovered(Set<String> seen, String streamId) {
        if (seen.contains(streamId)) {
            return true;
        }
        int u = streamId.indexOf('_');
        if (u > 0) {
            String left = streamId.substring(0, u);
            String right = streamId.substring(u + 1);
            return seen.contains(left) || seen.contains(right);
        }
        return false;
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

    private boolean channelOnline(JsonNode channel) {
        if (hasOnlineHint(channel)) {
            return asOnline(channel);
        }
        return true;
    }

    private boolean hasOnlineHint(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        for (String field : new String[]{"onLine", "online", "on_line", "status"}) {
            JsonNode child = node.get(field);
            if (child != null && !child.isNull() && !child.isMissingNode()) {
                return true;
            }
        }
        return false;
    }

    private boolean asOnline(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        for (String field : new String[]{"onLine", "online", "on_line"}) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                return parseOnlineValue(value);
            }
        }
        JsonNode status = node.get("status");
        if (status != null && !status.isNull() && !status.isMissingNode()) {
            return parseOnlineValue(status);
        }
        return false;
    }

    private boolean parseOnlineValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() == 1;
        }
        String t = value.asText("").trim();
        return "1".equals(t) || "true".equalsIgnoreCase(t) || "ON".equalsIgnoreCase(t)
            || "ONLINE".equalsIgnoreCase(t) || "online".equalsIgnoreCase(t);
    }

    private String buildPlayUrl(ZlmServer zlmServer, String streamId) {
        String host = zlmServer.getHost();
        if (StringUtils.isBlank(host)) {
            host = "127.0.0.1";
        }
        // 浏览器走 Nginx :8080，不要直连 ZLM 9992（Clash / 局域网都过不去）
        return "ws://" + host.trim() + ":8080/" + RTP_APP + "/" + streamId + ".live.flv";
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
