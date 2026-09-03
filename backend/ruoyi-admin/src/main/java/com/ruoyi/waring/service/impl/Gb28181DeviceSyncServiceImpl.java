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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 ZLM 拉国标 RTP 收流并写入 h_device（冻字段 device_type/gb_*）。
 * 演示机 MediaServer 含 GB28181Process 与 listRtpServer，无内置 SIP UAS；
 * 国标 IPC / 模拟器经 openRtpServer 推 PS 后，本接口即可同步。
 */
@Service
public class Gb28181DeviceSyncServiceImpl implements IGb28181DeviceSyncService {

    private static final Logger log = LoggerFactory.getLogger(Gb28181DeviceSyncServiceImpl.class);

    private static final String DEVICE_TYPE_GB28181 = "gb28181";
    private static final String STREAM_SOURCE_PLATFORM = "PLATFORM";
    private static final String DEFAULT_PLATFORM_PREFIX = "zlm-";
    private static final String RTP_APP = "rtp";

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

        String platformId = DEFAULT_PLATFORM_PREFIX + (zlmServer.getId() == null ? "1" : zlmServer.getId());
        Set<String> seen = new HashSet<>();

        if (rtpOk) {
            JsonNode rtpData = rtpList.path("data");
            if (rtpData.isArray()) {
                for (JsonNode item : rtpData) {
                    String streamId = firstText(item, "stream_id", "stream", "streamId");
                    if (StringUtils.isBlank(streamId) || !seen.add(streamId)) {
                        continue;
                    }
                    int[] counts = upsertOne(zlmServer, platformId, streamId, mediaHasStream(mediaList, streamId));
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
                    int[] counts = upsertOne(zlmServer, platformId, streamId, true);
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
            result.put("message",
                "ZLM 已连通，当前无国标 RTP 收流。请先调用 openRtpServer（或脚本 scripts/open_gb_rtp.sh）再同步。");
        } else {
            result.put("message", "已从 ZLM listRtpServer/getMediaList 同步国标设备（device_type=gb28181）");
        }
        return result;
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
    private int[] upsertOne(ZlmServer zlmServer, String platformId, String streamId, boolean online) {
        try {
            boolean existed = hDeviceMapper.selectDeviceByGbDeviceId(streamId, platformId) != null;
            HDevice probe = new HDevice();
            probe.setGb_device_id(streamId);
            probe.setGb_platform_id(platformId);
            probe.setDevice_type(DEVICE_TYPE_GB28181);
            probe.setStream_source_type(STREAM_SOURCE_PLATFORM);
            probe.setName("国标-" + streamId);
            probe.setIs_online(online ? "1" : "0");
            probe.setZlm_server_id(zlmServer.getId() == null ? 1L : zlmServer.getId());
            probe.setSva_server_id(1L);
            probe.setOrg_name("国标同步");
            probe.setOrg_index("10");
            probe.setPlay_url(buildPlayUrl(zlmServer, streamId));
            if (!existed) {
                probe.setApe_id(buildApeId(streamId));
            }
            hDeviceService.upsertGb28181Device(probe);
            return existed ? new int[]{0, 1, 0} : new int[]{1, 0, 0};
        } catch (Exception ex) {
            log.warn("同步国标设备失败 streamId={}: {}", streamId, ex.getMessage());
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
            restTemplate = new RestTemplate();
        }
        return restTemplate;
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
