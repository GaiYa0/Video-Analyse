package com.ruoyi.waring.service.impl;


import com.github.pagehelper.PageHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.domain.ZlmServer;
import com.ruoyi.waring.mapper.HDeviceMapper;
import com.ruoyi.waring.mapper.ZlmServerMapper;
import com.ruoyi.waring.service.HDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;


@Service
@Component
public class HDeviceServiceImpl implements HDeviceService {

    private static final Logger log = LoggerFactory.getLogger(HDeviceServiceImpl.class);

    private static final String STREAM_SOURCE_TYPE_DIRECT = "DIRECT";
    private static final String STREAM_SOURCE_TYPE_PLATFORM = "PLATFORM";
    private static final int MAX_APE_ID_GENERATE_RETRY = 20;
    private static final Pattern STREAM_NAME_PATTERN = Pattern.compile("[^A-Za-z0-9_-]");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MONITOR_STATUS_RUNNING = "RUNNING";
    private static final String MONITOR_STATUS_STOPPED = "STOPPED";
    private static final String MONITOR_STATUS_STARTING = "STARTING";
    private static final String MONITOR_STATUS_STOPPING = "STOPPING";
    private static final String MONITOR_STATUS_ERROR = "ERROR";
    private static final long DEFAULT_SERVER_ID = 1L;
    private static final String DEFAULT_ZLM_APP = "live";

    @Autowired
    HDeviceMapper hDeviceMapper;

    @Autowired
    SysUserMapper userMapper;

    @Autowired
    SysDeptMapper sysDeptMapper;

    @Autowired
    ZlmServerMapper zlmServerMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @PostConstruct
    private void initRestTemplate() {
        if (restTemplate == null) {
            restTemplate = new RestTemplate();
        }
    }

    @Override
    public void insertDevice(HDevice device) {
        hDeviceMapper.insertDevice(device);
    }

    @Override
    public void deleteDevice() {
        hDeviceMapper.deleteDevice();
    }

    @Override
    public HDevice selectDeviceByApeId(String apeId) {
        return hDeviceMapper.selectDeviceByApeId(apeId);
    }

    @Override
    public int insertDeviceCrud(HDevice device) {
        normalizeStreamSourceType(device, null);
        validateStreamSourceRule(device, null);
        if (StringUtils.isBlank(device.getOrg_name())) {
            throw new ServiceException("组织名称不能为空");
        }
        device.setOrg_index(normalizeOrgIndex(device.getOrg_index()));

        if (StringUtils.isBlank(device.getApe_id())) {
            device.setApe_id(generateUniqueApeId());
        } else if (hDeviceMapper.selectDeviceByApeId(device.getApe_id()) != null) {
            throw new ServiceException("设备编码已存在: " + device.getApe_id());
        }

        return hDeviceMapper.insertDeviceCrud(device);
    }

    @Override
    public int updateDevice(HDevice device) {
        if (StringUtils.isBlank(device.getApe_id())) {
            throw new ServiceException("设备编码不能为空");
        }

        HDevice existedDevice = hDeviceMapper.selectDeviceByApeId(device.getApe_id());
        if (existedDevice == null) {
            throw new ServiceException("设备不存在: " + device.getApe_id());
        }

        normalizeStreamSourceType(device, existedDevice);
        validateStreamSourceRule(device, existedDevice);
        device.setOrg_index(normalizeOrgIndex(device.getOrg_index()));

        return hDeviceMapper.updateDevice(device);
    }

    private void normalizeStreamSourceType(HDevice device, HDevice existedDevice) {
        String streamSourceType = device.getStream_source_type();
        if (StringUtils.isBlank(streamSourceType) && existedDevice != null) {
            streamSourceType = existedDevice.getStream_source_type();
        }
        if (StringUtils.isBlank(streamSourceType)) {
            streamSourceType = STREAM_SOURCE_TYPE_DIRECT;
        }

        streamSourceType = StringUtils.upperCase(streamSourceType.trim());
        if (!STREAM_SOURCE_TYPE_DIRECT.equals(streamSourceType) && !STREAM_SOURCE_TYPE_PLATFORM.equals(streamSourceType)) {
            throw new ServiceException("stream_source_type 仅支持 PLATFORM 或 DIRECT");
        }
        device.setStream_source_type(streamSourceType);
    }

    private void validateStreamSourceRule(HDevice device, HDevice existedDevice) {
        if (!STREAM_SOURCE_TYPE_DIRECT.equals(device.getStream_source_type())) {
            return;
        }

        String finalName = pickFinalValue(device.getName(), existedDevice == null ? null : existedDevice.getName());
        if (StringUtils.isBlank(finalName)) {
            throw new ServiceException("DIRECT 设备类型下，name 不能为空");
        }

        String finalDirectSourceUrl = pickFinalValue(device.getDirect_source_url(), existedDevice == null ? null : existedDevice.getDirect_source_url());
        if (StringUtils.isBlank(finalDirectSourceUrl)) {
            throw new ServiceException("DIRECT 设备类型下，direct_source_url 不能为空");
        }
    }

    private String pickFinalValue(String incomingValue, String existedValue) {
        if (incomingValue != null) {
            return incomingValue;
        }
        return existedValue;
    }

    private String generateUniqueApeId() {
        for (int i = 0; i < MAX_APE_ID_GENERATE_RETRY; i++) {
            String candidate = "cam" + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
            if (hDeviceMapper.selectDeviceByApeId(candidate) == null) {
                return candidate;
            }
        }
        throw new ServiceException("自动生成设备编码失败，请稍后重试");
    }

    @Override
    public int deleteDeviceByApeIds(String[] apeIds) {
        return hDeviceMapper.deleteDeviceByApeIds(apeIds);
    }

    @Override
    public Map<String, Object> getDirectLiveUrl(String apeId) {
        if (StringUtils.isBlank(apeId)) {
            throw new ServiceException("apeId 不能为空");
        }

        HDevice device = hDeviceMapper.selectDeviceByApeId(apeId);
        if (device == null) {
            throw new ServiceException("设备不存在: " + apeId);
        }

        if (!STREAM_SOURCE_TYPE_DIRECT.equalsIgnoreCase(device.getStream_source_type())) {
            throw new ServiceException("仅支持 DIRECT 设备类型");
        }

        if (StringUtils.isBlank(device.getDirect_source_url())) {
            throw new ServiceException("DIRECT 设备类型下，direct_source_url 不能为空");
        }

        Long zlmServerId = device.getZlm_server_id() == null ? DEFAULT_SERVER_ID : device.getZlm_server_id();
        ZlmServer zlmServer = zlmServerMapper.selectEnabledById(zlmServerId);
        if (zlmServer == null) {
            throw new ServiceException("设备未绑定可用ZLM服务器");
        }
        if (StringUtils.isBlank(zlmServer.getHost()) || zlmServer.getApi_port() == null || zlmServer.getMedia_http_port() == null) {
            throw new ServiceException("可用ZLM服务器配置缺失");
        }

        String zlmApp = StringUtils.isBlank(zlmServer.getApp()) ? DEFAULT_ZLM_APP : zlmServer.getApp().trim();

        String stream = sanitizeStreamName(apeId);
        String addProxyUrl = UriComponentsBuilder
            .fromUriString("http://" + zlmServer.getHost() + ":" + zlmServer.getApi_port() + "/index/api/addStreamProxy")
                .queryParam("vhost", "__defaultVhost__")
                .queryParam("app", zlmApp)
                .queryParam("stream", stream)
                .queryParam("url", device.getDirect_source_url())
            .queryParam("enable_mp4", 1)
            .queryParam("auto_close", 0)
                .queryParamIfPresent("secret", StringUtils.isNotBlank(zlmServer.getSecret())
                        ? java.util.Optional.of(zlmServer.getSecret())
                        : java.util.Optional.empty())
                .build(true)
                .toUriString();

            if (log.isDebugEnabled()) {
                log.debug("调用ZLM addStreamProxy, apeId={}, url={}", apeId, maskSensitiveUrl(addProxyUrl));
            }

        ResponseEntity<String> response = restTemplate.getForEntity(addProxyUrl, String.class);
        String body = response.getBody();
        if (StringUtils.isBlank(body)) {
            throw new ServiceException("调用 ZLM addStreamProxy 失败: empty response");
        }

        int code;
        String msg;
        String zlmProxyKey;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            code = parseCode(root.path("code").asText());
            msg = root.path("msg").asText("");
            zlmProxyKey = root.path("data").path("key").asText("");
        } catch (Exception e) {
            throw new ServiceException("调用 ZLM addStreamProxy 失败: 响应解析异常");
        }

        boolean addProxySuccess = code == 0;
        boolean addProxyAlreadyExists = code != 0 && isAddProxyAlreadyExists(msg);

        if (!addProxySuccess && !addProxyAlreadyExists) {
            throw new ServiceException("调用 ZLM addStreamProxy 失败: " + msg);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("apeId", apeId);
        result.put("stream", stream);
        result.put("playUrl", "ws://" + zlmServer.getHost() + ":" + zlmServer.getMedia_http_port() + "/" + zlmApp + "/" + stream + ".live.flv");
        result.put("zlmProxyKey", StringUtils.isBlank(zlmProxyKey) ? null : zlmProxyKey);
        result.put("addProxySuccess", addProxySuccess);
        result.put("addProxyAlreadyExists", addProxyAlreadyExists);
        result.put("protocol", "ws-flv");
        return result;
    }

    @Override
    public List<HDevice> selectDeviceList(HDevice device, Long userId) {
        device.setOrg_index(normalizeOrgIndex(device.getOrg_index()));
        List<HDevice> devices;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        List<String> orgIndexs = null;
        if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId)) {
            // 如果登录账号不为admin 账号
            if (device.getOrg_index() == null && !dept.getOrgIndex().equals("10")) {
                orgIndexs = sysDeptMapper.getOrgIndex(dept.getOrgIndex());
                orgIndexs.add(dept.getOrgIndex());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else if (device.getOrg_index() != null && !dept.getOrgIndex().equals("10")) {
                orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                orgIndexs.add(device.getOrg_index());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else if (device.getOrg_index() != null) {
                if (!device.getOrg_index().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            }
        } else if (!dept.getOrgIndex().equals("10")) {
            // 如果登录账号不为 hy 账号
            if (device.getOrg_index() == null) {
                orgIndexs = sysDeptMapper.getOrgIndex(dept.getOrgIndex());
                orgIndexs.add(dept.getOrgIndex());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else {
                orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                orgIndexs.add(device.getOrg_index());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            }
        } else {
            // 如果登录账号为 hy/admin 账号
            if (device.getOrg_index() != null) {
                if (!device.getOrg_index().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            } else {
                if (!dept.getOrgIndex().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                } else if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId)) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            }
        }

        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        devices = hDeviceMapper.selectDeviceList(device);

        return devices;
    }

    @Override
    public Map<String, Object> getDeviceNum(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        int deviceNum;
        int deviceEnableNum;
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) || dept.getOrgIndex().equals("10")) {
            // 如果登录账号为 集团管理员和系统管理员 查询所有数量的设备
            deviceNum = hDeviceMapper.getDeviceNum();
            deviceEnableNum = hDeviceMapper.getDeviceEnableNum();
        } else {
            // 如果登录账号为 别的账号 根据大组织查询
            List<String> orgIndexs = sysDeptMapper.getOrgIndex(dept.getOrgIndex());
            orgIndexs.add(dept.getOrgIndex());
            String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
            HDevice device = new HDevice();
            device.getParams().put("org_indexs", org_index);
            deviceNum = hDeviceMapper.getDeviceNumByOrg(device);
            deviceEnableNum = hDeviceMapper.getDeviceEnableNumByOrg(device);
        }
        int deviceli = deviceNum - deviceEnableNum;
        Map<String, Object> map = new HashMap<>();
        map.put("deviceNum", deviceNum);
        map.put("deviceEnableNum", deviceEnableNum);
        map.put("deviceli", deviceli);
        return map;
    }

    @Override
    public List<HDevice> selectLDeviceList(HDevice device, Long userId) {
        device.setOrg_index(normalizeOrgIndex(device.getOrg_index()));
        List<HDevice> devices;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        List<String> orgIndexs = null;
        if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId)) {
            // 如果登录账号不为admin 账号
            if (device.getOrg_index() == null && !dept.getOrgIndex().equals("10")) {
                orgIndexs = sysDeptMapper.getOrgIndex(dept.getOrgIndex());
                orgIndexs.add(dept.getOrgIndex());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else if (device.getOrg_index() != null && !dept.getOrgIndex().equals("10")) {
                orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                orgIndexs.add(device.getOrg_index());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else if (device.getOrg_index() != null) {
                if (!device.getOrg_index().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            }
        } else if (!dept.getOrgIndex().equals("10")) {
            // 如果登录账号不为 hy 账号
            if (device.getOrg_index() == null) {
                orgIndexs = sysDeptMapper.getOrgIndex(dept.getOrgIndex());
                orgIndexs.add(dept.getOrgIndex());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            } else {
                orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                orgIndexs.add(device.getOrg_index());
                String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                device.getParams().put("org_indexs", org_index);
            }
        } else {
            // 如果登录账号为 hy/admin 账号
            if (device.getOrg_index() != null) {
                if (!device.getOrg_index().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            } else {
                if (!dept.getOrgIndex().equals("10")) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                } else if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId)) {
                    orgIndexs = sysDeptMapper.getOrgIndex(device.getOrg_index());
                    orgIndexs.add(device.getOrg_index());
                    String[] org_index = orgIndexs.toArray(new String[orgIndexs.size()]);
                    device.getParams().put("org_indexs", org_index);
                }
            }
        }

        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        devices = hDeviceMapper.selectLDeviceList(device);

        return devices;
    }

    @Override
    public int startMonitor(String apeId) {
        if (StringUtils.isBlank(apeId)) {
            throw new ServiceException("apeId 不能为空");
        }

        HDevice existedDevice = hDeviceMapper.selectDeviceByApeId(apeId);
        if (existedDevice == null) {
            throw new ServiceException("设备不存在: " + apeId);
        }

        String startAddProxyUrl = buildDirectAddProxyUrl(existedDevice);
        String startPlayUrl = buildDirectPlayUrl(existedDevice);
        if (isDirectDevice(existedDevice)) {
            Map<String, Object> directLiveInfo = getDirectLiveUrl(apeId);
            boolean addProxyAlreadyExists = Boolean.TRUE.equals(directLiveInfo.get("addProxyAlreadyExists"));
            if (addProxyAlreadyExists) {
                throw new ServiceException("设备监控已经启动过");
            }

            Object playUrlObj = directLiveInfo.get("playUrl");
            Object zlmProxyKeyObj = directLiveInfo.get("zlmProxyKey");
            if (playUrlObj != null) {
                startPlayUrl = String.valueOf(playUrlObj);
            }
            String zlmProxyKey = zlmProxyKeyObj == null ? null : String.valueOf(zlmProxyKeyObj);
            hDeviceMapper.updatePlayUrlByApeId(apeId, startPlayUrl);
            if (StringUtils.isNotBlank(zlmProxyKey)) {
                hDeviceMapper.updateZlmProxyKeyByApeId(apeId, zlmProxyKey);
            }
        }

        int updated = hDeviceMapper.updateMonitorStateByApeId(apeId, MONITOR_STATUS_RUNNING);
        if (updated <= 0) {
            throw new ServiceException("启动监控失败: " + apeId);
        }
        return updated;
    }

    @Override
    public int stopMonitor(String apeId) {
        if (StringUtils.isBlank(apeId)) {
            throw new ServiceException("apeId 不能为空");
        }

        HDevice existedDevice = hDeviceMapper.selectDeviceByApeId(apeId);
        if (existedDevice == null) {
            throw new ServiceException("设备不存在: " + apeId);
        }

        boolean directProxyDeleted = false;
        if (isDirectDevice(existedDevice) && StringUtils.isNotBlank(existedDevice.getZlm_proxy_key())) {
            try {
                directProxyDeleted = deleteDirectStreamProxy(existedDevice);
            } catch (Exception e) {
                log.error("调用ZLM delStreamProxy失败, apeId={}, key={}", apeId, existedDevice.getZlm_proxy_key(), e);
            }
        }

        int updated = hDeviceMapper.updateMonitorStateByApeId(apeId, MONITOR_STATUS_STOPPED);
        if (updated <= 0) {
            throw new ServiceException("停止监控失败: " + apeId);
        }

        hDeviceMapper.updatePlayUrlByApeId(apeId, null);
        if (directProxyDeleted) {
            hDeviceMapper.updateZlmProxyKeyByApeId(apeId, null);
        }
        return updated;
    }

    @Override
    public Map<String, Object> previewMonitor(String apeId) {
        if (StringUtils.isBlank(apeId)) {
            throw new ServiceException("apeId 不能为空");
        }

        HDevice device = hDeviceMapper.selectDeviceByApeId(apeId);
        if (device == null) {
            throw new ServiceException("设备不存在: " + apeId);
        }

        String previewAddProxyUrl = buildDirectAddProxyUrl(device);
        String previewPlayUrl = device.getPlay_url();
        if (StringUtils.isBlank(previewPlayUrl)) {
            previewPlayUrl = buildDirectPlayUrl(device);
        }
        if (StringUtils.isBlank(previewPlayUrl)) {
            previewPlayUrl = device.getDirect_source_url();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("apeId", device.getApe_id());
        result.put("name", device.getName());
        result.put("streamSourceType", device.getStream_source_type());
        result.put("monitorStatus", device.getMonitor_status());
        result.put("directSourceUrl", device.getDirect_source_url());
        result.put("playUrl", previewPlayUrl);
        result.put("ipAddr", device.getIp_addr());
        result.put("port", device.getPort());
        result.put("supportedMonitorStatuses", new String[] {
            MONITOR_STATUS_RUNNING,
            MONITOR_STATUS_STOPPED,
            MONITOR_STATUS_STARTING,
            MONITOR_STATUS_STOPPING,
            MONITOR_STATUS_ERROR
        });
        return result;
    }

    private boolean isDirectDevice(HDevice device) {
        return device != null && STREAM_SOURCE_TYPE_DIRECT.equalsIgnoreCase(device.getStream_source_type());
    }

    private String normalizeOrgIndex(String orgIndex) {
        if (StringUtils.isBlank(orgIndex)) {
            return orgIndex;
        }

        String trimmed = orgIndex.trim();
        if (!trimmed.matches("\\d+")) {
            return orgIndex;
        }

        try {
            SysDept dept = sysDeptMapper.selectDeptById(Long.valueOf(trimmed));
            if (dept != null && StringUtils.isNotBlank(dept.getOrgIndex())) {
                return dept.getOrgIndex();
            }
        } catch (NumberFormatException ex) {
            log.warn("org_index 不是有效 deptId，按组织编码原样使用: {}", trimmed);
            return orgIndex;
        }

        return orgIndex;
    }

    private String sanitizeStreamName(String apeId) {
        String stream = STREAM_NAME_PATTERN.matcher(apeId == null ? "" : apeId).replaceAll("");
        if (StringUtils.isBlank(stream)) {
            return "cam" + System.currentTimeMillis();
        }
        return stream;
    }

    private String buildDirectAddProxyUrl(HDevice device) {
        if (device == null || !STREAM_SOURCE_TYPE_DIRECT.equalsIgnoreCase(device.getStream_source_type())
            || StringUtils.isBlank(device.getDirect_source_url())) {
            return "";
        }

        ZlmServer zlmServer = resolveEnabledZlmServer(device);
        if (zlmServer == null || StringUtils.isBlank(zlmServer.getHost()) || zlmServer.getApi_port() == null) {
            return "";
        }

        String zlmApp = StringUtils.isBlank(zlmServer.getApp()) ? DEFAULT_ZLM_APP : zlmServer.getApp().trim();
        String stream = sanitizeStreamName(device.getApe_id());
        return UriComponentsBuilder
            .fromUriString("http://" + zlmServer.getHost() + ":" + zlmServer.getApi_port() + "/index/api/addStreamProxy")
            .queryParam("vhost", "__defaultVhost__")
            .queryParam("app", zlmApp)
            .queryParam("stream", stream)
            .queryParam("url", device.getDirect_source_url())
            .queryParam("enable_mp4", 1)
            .queryParam("auto_close", 0)
            .queryParamIfPresent("secret", StringUtils.isNotBlank(zlmServer.getSecret())
                ? java.util.Optional.of(zlmServer.getSecret())
                : java.util.Optional.empty())
            .build(true)
            .toUriString();
    }

    private String buildDirectPlayUrl(HDevice device) {
        if (device == null || !STREAM_SOURCE_TYPE_DIRECT.equalsIgnoreCase(device.getStream_source_type())) {
            return "";
        }

        ZlmServer zlmServer = resolveEnabledZlmServer(device);
        if (zlmServer == null || StringUtils.isBlank(zlmServer.getHost()) || zlmServer.getMedia_http_port() == null) {
            return "";
        }

        String zlmApp = StringUtils.isBlank(zlmServer.getApp()) ? DEFAULT_ZLM_APP : zlmServer.getApp().trim();
        String stream = sanitizeStreamName(device.getApe_id());
        return "ws://" + zlmServer.getHost() + ":" + zlmServer.getMedia_http_port() + "/" + zlmApp + "/" + stream + ".live.flv";
    }

    private ZlmServer resolveEnabledZlmServer(HDevice device) {
        if (device == null) {
            return null;
        }
        Long zlmServerId = device.getZlm_server_id() == null ? DEFAULT_SERVER_ID : device.getZlm_server_id();
        return zlmServerMapper.selectEnabledById(zlmServerId);
    }

    private boolean deleteDirectStreamProxy(HDevice device) {
        ZlmServer zlmServer = resolveEnabledZlmServer(device);
        if (zlmServer == null || StringUtils.isBlank(zlmServer.getHost()) || zlmServer.getApi_port() == null) {
            log.error("删除代理流失败，设备未绑定可用ZLM服务器或配置缺失, apeId={}", device.getApe_id());
            return false;
        }

        String delProxyUrl = UriComponentsBuilder
            .fromUriString("http://" + zlmServer.getHost() + ":" + zlmServer.getApi_port() + "/index/api/delStreamProxy")
            .queryParam("key", device.getZlm_proxy_key())
            .queryParamIfPresent("secret", StringUtils.isNotBlank(zlmServer.getSecret())
                ? java.util.Optional.of(zlmServer.getSecret())
                : java.util.Optional.empty())
            .build(true)
            .toUriString();

        if (log.isDebugEnabled()) {
            log.debug("调用ZLM delStreamProxy, apeId={}, url={}", device.getApe_id(), maskSensitiveUrl(delProxyUrl));
        }

        ResponseEntity<String> response = restTemplate.getForEntity(delProxyUrl, String.class);
        String body = response.getBody();
        if (StringUtils.isBlank(body)) {
            log.error("调用 ZLM delStreamProxy 返回空响应, apeId={}", device.getApe_id());
            return false;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            int code = parseCode(root.path("code").asText());
            boolean flag = root.path("data").path("flag").asBoolean(false);
            if (code == 0 && flag) {
                return true;
            }
            String msg = root.path("msg").asText("");
            log.error("调用 ZLM delStreamProxy 失败, apeId={}, key={}, code={}, flag={}, msg={}",
                device.getApe_id(), device.getZlm_proxy_key(), code, flag, msg);
        } catch (Exception e) {
            log.error("调用 ZLM delStreamProxy 响应解析异常, apeId={}, key={}",
                device.getApe_id(), device.getZlm_proxy_key(), e);
        }
        return false;
    }

    private boolean isAddProxyAlreadyExists(String msg) {
        if (StringUtils.isBlank(msg)) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains("already exists");
    }

    private int parseCode(Object code) {
        if (code instanceof Number) {
            return ((Number) code).intValue();
        }
        if (code == null) {
            return -1;
        }
        try {
            return Integer.parseInt(String.valueOf(code));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String maskSensitiveUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        return url.replaceAll("(?i)([?&](secret|token|access_token|auth|sign|signature)=)[^&]*", "$1***");
    }
}
