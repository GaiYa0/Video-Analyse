package com.ruoyi.waring.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.service.SvaDetectEventConsumer;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.websocket.WebSocketUsers;
import com.ruoyi.system.domain.DeploymentTask;
import com.ruoyi.system.service.IDeploymentTaskService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.waring.Util.OpcUtil;
import com.ruoyi.waring.Util.TimeUtil;
import com.ruoyi.waring.domain.*;
import com.ruoyi.waring.mapper.SvaServerMapper;
import com.ruoyi.waring.mapper.ZlmServerMapper;
import com.ruoyi.waring.service.*;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/waring/waring")
public class HWaringController extends BaseController implements SvaDetectEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HWaringController.class);
    private static final String ENGINE_A_SERVER = "A-SERVER";
    private static final String ENGINE_M_SERVER = "M-SERVER";
    private static final long DEFAULT_SERVER_ID = 1L;
    private static final String DEFAULT_ZLM_APP = "live";
    private static final String DEFAULT_ZLM_VHOST = "__defaultVhost__";
    private static final long DEFAULT_ZLM_BACK_MS = 3000L;
    private static final long DEFAULT_ZLM_FORWARD_MS = 3000L;
    private static final String ZLM_MEDIA_STATUS_RECORD_STARTED = "record_started";
    private static final String ZLM_MEDIA_STATUS_RECORD_FAILED = "record_failed";
    private static final String SVA_CROSS_LINE_ALARM_TYPE = "SVA_CROSS_LINE";
    private static final String SVA_CROSS_LINE_ALARM_TYPE_NAME = "跨线告警";
    private static final String SVA_ENTER_REGION_ALARM_TYPE = "SVA_ENTER_REGION";
    private static final String SVA_ENTER_REGION_ALARM_TYPE_NAME = "进区告警";
    private static final String SVA_EXIT_REGION_ALARM_TYPE = "SVA_EXIT_REGION";
    private static final String SVA_EXIT_REGION_ALARM_TYPE_NAME = "出区告警";
    private static final String SVA_DWELL_ALARM_TYPE = "SVA_DWELL";
    private static final String SVA_DWELL_ALARM_TYPE_NAME = "停留告警";
    private static final String SVA_LOW_SPEED_ALARM_TYPE = "SVA_LOW_SPEED";
    private static final String SVA_LOW_SPEED_ALARM_TYPE_NAME = "低速告警";
    private static final String SVA_LOITERING_ALARM_TYPE = "SVA_LOITERING";
    private static final String SVA_LOITERING_ALARM_TYPE_NAME = "徘徊告警";
    private static final String SVA_ABSENCE_ALARM_TYPE = "SVA_ABSENCE";
    private static final String SVA_ABSENCE_ALARM_TYPE_NAME = "离岗/缺席告警";
    private static final String SVA_COUNT_THRESHOLD_ALARM_TYPE = "SVA_COUNT_THRESHOLD";
    private static final String SVA_COUNT_THRESHOLD_ALARM_TYPE_NAME = "数量阈值告警";
    private static final String SVA_OCCUPANCY_ALARM_TYPE = "SVA_OCCUPANCY";
    private static final String SVA_OCCUPANCY_ALARM_TYPE_NAME = "区域占用告警";
    private static final String SVA_DIRECTION_MOVE_ALARM_TYPE = "SVA_DIRECTION_MOVE";
    private static final String SVA_DIRECTION_MOVE_ALARM_TYPE_NAME = "定向通行告警";
    private static final String SVA_DIRECTION_REVERSE_ALARM_TYPE = "SVA_DIRECTION_REVERSE";
    private static final String SVA_DIRECTION_REVERSE_ALARM_TYPE_NAME = "逆向通行告警";
    private static final String SVA_RELATION_NEAR_ALARM_TYPE = "SVA_RELATION_NEAR";
    private static final String SVA_RELATION_NEAR_ALARM_TYPE_NAME = "目标接近告警";
    private static final String SVA_RELATION_APART_ALARM_TYPE = "SVA_RELATION_APART";
    private static final String SVA_RELATION_APART_ALARM_TYPE_NAME = "目标远离告警";
    private static final String SVA_RELATION_NOT_CONTAINS_ALARM_TYPE = "SVA_RELATION_NOT_CONTAINS";
    private static final String SVA_RELATION_NOT_CONTAINS_ALARM_TYPE_NAME = "目标未包含告警";

    @Autowired
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private HWaringService hWaringService;

    @Autowired
    private HTypeService hTypeService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private HHandleService hHandleService;

    @Autowired
    private HOpcService hOpcService;

    @Autowired
    private HBindingService hBindingService;

    @Autowired
    private HDeviceService hDeviceService;

    @Autowired
    private IDeploymentTaskService deploymentTaskService;

    @Autowired
    private ZlmServerMapper zlmServerMapper;

    @Autowired
    private SvaServerMapper svaServerMapper;

    @Value("${h3.username}")
    private String username;

    @Value("${h3.password}")
    private String password;

    @Value("${h3.w-ip}")
    private String ip;

    @Value("${h3.w-port}")
    private String port;

    @Value("${hy.opc-address}")
    private String endPointUrl;

    /**
     * 报警信息从H3平台收集并入库
     */
    @PostMapping(value = "/addWaring", produces = "application/json;charset=UTF-8")
    public void dopost(@RequestBody String datas) throws InterruptedException {
        // 处理接收数据
        JSONObject obj = JSON.parseObject(datas);
        Object Edata = obj.get("data");
        String data = Edata.toString();
        JSONObject dat = JSONObject.parseObject(data);
        // 如果遇到采集为 0 停止 不入库操作
        // 整理入库数据
        HWaring waring = new HWaring();
        waring.setId(dat.get("id").toString());
        waring.setAlarm_type(dat.get("alarm_type").toString());
        waring.setDevice_id(dat.get("device_id").toString());
        // 时间转换
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String alarm_time = format.format(dat.get("alarm_time"));
        waring.setAlarm_time(alarm_time);
        // 报警类型、报警级别转换
        HType type = hTypeService.getWaringType(dat.get("alarm_type").toString(), dat.get("device_id").toString());
        if (type == null) {
            waring.setDevice_name(dat.get("device_name").toString());
            waring.setAlarm_type_name(dat.get("alarm_type_name").toString());
            waring.setAlarm_level(dat.get("alarm_level").toString());
            waring.setAlarm_level_name(dat.get("alarm_level_name").toString());
        } else {
            waring.setDevice_name(type.getDevice_name());
            waring.setAlarm_type_name(type.getAlarm_type_name());
            waring.setAlarm_level(type.getAlarm_level());
            waring.setAlarm_level_name(type.getAlarm_level_name());
        }
        // 联动 0为不显示 1为显示
        int is_enable;
        // 转换
        if (dat.get("alarm_type").toString().equals("99158eabc762e2f75fcc325d2055343e")) {
            // 检测到检修牌
            is_enable = 0;
        } else if (dat.get("alarm_type").toString().equals("0bbfb1c17f1cc4ce5937b9ab5ef229c5")) {
            // 如果检测到胶轮车
            is_enable = 0;
        } else {
            is_enable = 1;
        }
        // 如果检查到区域入侵
        if (dat.get("alarm_type").toString().equals("E_IV_IntrusionDetect") || dat.get("alarm_type").equals("viid_videolabel_topic_33619990")) {
            // 去检查有没有2小时内的检修牌报警信息
            int num = hWaringService.getNoWaring(dat.get("device_id").toString(), alarm_time);
            if (num >= 1) {
                is_enable = 0;
            } else {
                is_enable = 1;
            }
            // 去查询皮带 的启停信息
            HOpc hopc1 = new HOpc();
            hopc1.setDevice_index(dat.get("device_id").toString());
            hopc1.setType(1);
            String identifier1 = hOpcService.getStatus(hopc1);
            if (identifier1 != null) {
                OpcUaClient client = OpcUtil.createClient(endPointUrl, null, null);
                Boolean status = null;
                if (client != null) {
                    status = (Boolean) OpcUtil.readValue(identifier1, client);
                    OpcUtil.disconnect(client);
                    Thread.sleep(500);
                    if (Boolean.FALSE.equals(status)) {
                        is_enable = 0;
                    } else {
                        is_enable = 1;
                    }
                }
            }
            // 去查询刮板机 的启停信息
            HOpc hopc2 = new HOpc();
            hopc2.setDevice_index(dat.get("device_id").toString());
            hopc2.setType(2);
            String identifier2 = hOpcService.getStatus(hopc2);
            if (identifier2 != null) {
                OpcUaClient client = OpcUtil.createClient(endPointUrl, null, null);
                int status = 0;
                if (client != null) {
                    status = Integer.parseInt(Objects.requireNonNull(OpcUtil.readValue(identifier2, client)).toString());
                    OpcUtil.disconnect(client);
                    Thread.sleep(500);
                    if (status <= 0) {
                        is_enable = 0;
                    } else {
                        is_enable = 1;
                    }
                }
            }
        } else if (dat.get("alarm_type").toString().equals("5a9cd395838884bce83b4f8a273dfb9a")) {
            // 去检查5S内是否有胶轮车算法
            int num = hWaringService.getNoCarWaring(dat.get("device_id").toString(), alarm_time);
            if (num >= 1) {
                is_enable = 1;
            } else {
                is_enable = 0;
            }
        }

        // 摄像头归属确认
        String team = hBindingService.getTeam(dat.get("device_id").toString());
        if (team != null) {
            waring.setTeam(team);
        } else {
            waring.setTeam("");
        }

        // 子组织转换为父组织
        Long parent_id = deptService.getParentId(dat.get("org_index").toString());
        if (parent_id == 100) {
            waring.setOrg_index(dat.get("org_index").toString());
            waring.setOrg_name(dat.get("org_name").toString());
        } else if (parent_id == 0) {
            waring.setOrg_index(dat.get("org_index").toString());
            waring.setOrg_name("中煤华昱");
        } else {
            SysDept dept = deptService.selectDeptById(parent_id);
            waring.setOrg_index(dept.getOrgIndex());
            waring.setOrg_name(dept.getDeptName());
        }
        waring.setLongitude("0");
        waring.setLatitude("0");
        waring.setPicture_url(dat.get("picture_url").toString());
        waring.setIs_handle(0);

        Object token = redisTemplate.boundValueOps("token").get();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token.toString());
        headers.set("User", "usercode:" + username);
        headers.set("Cookie", "usercode=" + username);
        headers.set("Content-Type", "application/json");
        // 获取图片的绝对路径
        String url1 = "http://" + ip + ":" + port + "/api/mg/v2/fms/files/url/translate";
        String[] str1 = {dat.get("picture_url").toString()};
        HttpEntity<String[]> entity1 = new HttpEntity<>(str1, headers);
        String r1 = restTemplate.postForObject(url1, entity1, String.class);
        JSONObject p = JSONObject.parseObject(r1);
        JSONArray pic;
        if (p != null) {
            pic = p.getJSONArray("data");
            waring.setPicture_absolute_url(pic.get(0).toString());
        }

        waring.setIs_enable(is_enable);

        waring.setIp(ip);

        int insert_id = hWaringService.insertWaring(waring);

        if (insert_id == 1) {
            JSONObject indexJson = new JSONObject();
            Map<String, Object> map = new HashMap<>();
            map.put("w_id", waring.getW_id());
            map.put("level", waring.getAlarm_level_name());
            map.put("type", waring.getAlarm_type_name());
            map.put("time", waring.getAlarm_time());
            map.put("device", waring.getDevice_id());
            map.put("url", waring.getPicture_absolute_url());
            map.put("team", waring.getTeam());
            indexJson.put("newWarning", map);
            WebSocketUsers.sendMessageToUsersByText(indexJson.toJSONString());
            waring.setIp(ip);
            String url2 = "http://192.168.1.29:9114/waring/waring/addWaring";
            HttpEntity<HWaring> entity2 = new HttpEntity<>(waring, headers);
            restTemplate.postForObject(url2, entity2, String.class);
        }
    }

    /**
     * 接收SVA简化告警并入库
     */
    @Anonymous
    @PostMapping(value = "/addFromSvaSimple", produces = "application/json;charset=UTF-8")
    public AjaxResult addFromSvaSimple(@RequestBody JSONObject body) {
        try {
            String controlCode = body == null ? "" : Optional.ofNullable(body.getString("control_code")).orElse("");
            String imagePath = body == null ? "" : Optional.ofNullable(body.getString("image_path")).orElse("");
            String videoPath = resolveString(body, "video_path", "videoPath");
            String mediaStatus = resolveString(body, "sva_media_status", "media_status", "status");
            String alarmTimeRaw = body == null ? "" : Optional.ofNullable(body.getString("alarm_time")).orElse("");
            String alarmTime = normalizeAlarmTime(alarmTimeRaw);
            if (imagePath.trim().isEmpty()) {
                imagePath = "";
            }

            String deploymentId = controlCode.trim();
            String deviceId = deploymentId.isEmpty() ? "" : deploymentId;
            String deviceName = "未知设备";
            String orgIndex = "";
            String orgName = "";
            DeploymentTask deploymentTask = null;
            HDevice device = null;
            boolean aiReviewEnabled = false;

            if (deploymentId.isEmpty()) {
                log.warn("SVA告警缺少control_code, 将按默认设备信息入库");
            }

            if (!deploymentId.isEmpty()) {
                deploymentTask = deploymentTaskService.selectDeploymentTaskById(deploymentId);
                if (deploymentTask == null) {
                    log.warn("SVA告警未找到deployment_task, deploymentId={}", deploymentId);
                } else {
                    aiReviewEnabled = deploymentTask.getAiReviewEnabled() == null
                        || Boolean.TRUE.equals(deploymentTask.getAiReviewEnabled());
                    String mappedDeviceId = Optional.ofNullable(deploymentTask.getDeviceId()).orElse("").trim();
                    if (mappedDeviceId.isEmpty()) {
                        log.warn("SVA告警deployment_task缺少device_id, deploymentId={}", deploymentId);
                    } else {
                        deviceId = mappedDeviceId;
                        device = hDeviceService.selectDeviceByApeId(mappedDeviceId);
                        if (device == null) {
                            deviceName = mappedDeviceId;
                            log.warn("SVA告警未找到设备详情, deploymentId={}, deviceId={}", deploymentId, mappedDeviceId);
                        } else {
                            if (device.getName() != null && !device.getName().trim().isEmpty()) {
                                deviceName = device.getName().trim();
                            } else {
                                deviceName = mappedDeviceId;
                            }
                            if (device.getOrg_index() != null && !device.getOrg_index().trim().isEmpty()) {
                                orgIndex = device.getOrg_index().trim();
                            }
                            if (device.getOrg_name() != null && !device.getOrg_name().trim().isEmpty()) {
                                orgName = device.getOrg_name().trim();
                            }
                        }
                    }
                }
            }

            HWaring waring = new HWaring();
            waring.setId(UUID.randomUUID().toString().replace("-", ""));
            waring.setAlarm_type("SVA_SIMPLE");
            String customAlarmTypeName = resolveCustomAlarmTypeName(body);
            waring.setAlarm_type_name(customAlarmTypeName.isEmpty() ? "SVA告警" : customAlarmTypeName);
            waring.setSva_business_event_id(resolveLong(body, "businessEventId", "business_event_id"));
            waring.setSva_business_event_name(customAlarmTypeName.isEmpty() ? null : customAlarmTypeName);
            waring.setSva_business_template_id(resolveString(body, "businessTemplateId", "business_template_id", "templateId", "template_id"));
            waring.setSva_business_template_version(resolveInteger(body, "businessTemplateVersion", "business_template_version", "templateVersion", "template_version"));
            waring.setAlarm_level("3");
            waring.setAlarm_level_name("一般");
            waring.setDevice_id(deviceId);
            waring.setDevice_name(deviceName);
            waring.setOrg_index(orgIndex);
            waring.setOrg_name(orgName);
            waring.setLongitude("0");
            waring.setLatitude("0");
            waring.setPicture_url(imagePath);
            waring.setPicture_absolute_url(resolveSimpleAlarmImageUrl(deploymentTask, device, imagePath));
            if (!videoPath.isEmpty()) {
                waring.setVideo_url(videoPath);
                waring.setVideo_absolute_url(resolveMediaAbsoluteUrl(deploymentTask, device, videoPath));
            }
            if (!mediaStatus.isEmpty()) {
                waring.setSva_media_status(mediaStatus);
            }
            waring.setIs_handle(0);
            waring.setIs_enable(1);
            waring.setTeam("");
            waring.setIp(ip);
            waring.setAlarm_time(alarmTime);
            waring.setControl_code(deploymentId.isEmpty() ? null : deploymentId);
            waring.setAi_review_enabled(aiReviewEnabled);
            waring.setAi_review_prompt(deploymentTask == null ? null : deploymentTask.getAiReviewPrompt());

            int insert = hWaringService.insertWaring(waring);
            if (insert == 1) {
                if (isMediaServerRecordEngine(deploymentTask)) {
                    requestZlmRecordTask(waring, deploymentTask, device);
                } else {
                    requestSvaMediaBinding(waring, deploymentTask, device, waring.getAlarm_type());
                }
                JSONObject indexJson = new JSONObject();
                Map<String, Object> map = new HashMap<>();
                map.put("w_id", waring.getW_id());
                map.put("level", waring.getAlarm_level_name());
                map.put("type", waring.getAlarm_type_name());
                map.put("time", waring.getAlarm_time());
                map.put("device", waring.getDevice_id());
                map.put("url", waring.getPicture_absolute_url());
                map.put("team", waring.getTeam());
                indexJson.put("newWarning", map);
                WebSocketUsers.sendMessageToUsersByText(indexJson.toJSONString());
                return AjaxResult.success("接收成功");
            }
            return AjaxResult.error("接收失败");
        } catch (Exception e) {
            log.warn("接收SVA简化告警异常, 将返回失败信息", e);
            return AjaxResult.error("接收失败");
        }
    }

    /**
     * 接收SVA新链路的素材回写，只更新既有告警记录。
     */
    @Anonymous
    @PostMapping(value = "/addFromSvaMediaCallback", produces = "application/json;charset=UTF-8")
    public AjaxResult addFromSvaMediaCallback(@RequestBody JSONObject body) {
        try {
            String alarmId = resolveString(body, "alarm_id", "alarmId");
            String eventId = resolveString(body, "event_id", "eventId");
            String targetId = alarmId;
            if (targetId.isEmpty() && !eventId.isEmpty()) {
                log.warn("SVA素材回写未携带alarmId, 回退使用eventId, eventId={}", eventId);
                targetId = eventId;
            }
            if (targetId.isEmpty()) {
                log.warn("SVA素材回写缺少alarmId/eventId, body={}", body);
                return AjaxResult.error("缺少alarmId或eventId");
            }

            HWaring existing = hWaringService.selectWaringById(targetId);
            if (existing == null) {
                log.warn("SVA素材回写未找到告警记录, alarmId={} eventId={}", alarmId, eventId);
                return AjaxResult.error("未找到告警记录");
            }

            String controlCode = resolveString(body, "control_code", "controlCode");
            String resolvedControlCode = !controlCode.isEmpty() ? controlCode : Optional.ofNullable(existing.getControl_code()).orElse("");
            DeploymentTask deploymentTask = null;
            HDevice device = null;
            if (!resolvedControlCode.isEmpty()) {
                deploymentTask = deploymentTaskService.selectDeploymentTaskById(resolvedControlCode);
                if (deploymentTask != null) {
                    String mappedDeviceId = Optional.ofNullable(deploymentTask.getDeviceId()).orElse("").trim();
                    if (!mappedDeviceId.isEmpty()) {
                        device = hDeviceService.selectDeviceByApeId(mappedDeviceId);
                    }
                }
            }

            String imagePath = resolveString(body, "image_path", "imagePath");
            String videoPath = resolveString(body, "video_path", "videoPath");
            String mediaStatus = resolveString(body, "status");
            String mediaError = resolveString(body, "error_message", "errorMessage", "desc");

            HWaring update = new HWaring();
            update.setId(targetId);
            if (!imagePath.isEmpty()) {
                update.setPicture_url(imagePath);
                update.setPicture_absolute_url(resolveMediaAbsoluteUrl(deploymentTask, device, imagePath));
            }
            if (!mediaStatus.isEmpty()) {
                update.setSva_media_status(mediaStatus);
            }
            if (!mediaError.isEmpty()) {
                update.setSva_media_error(mediaError);
            }

            int updated = hWaringService.updateSvaMediaFields(update);
            log.info("SVA素材回写处理完成: alarmId={} eventId={} targetId={} controlCode={} imagePath={} videoPath={} status={} updated={}",
                alarmId, eventId, targetId, resolvedControlCode, imagePath, videoPath, mediaStatus, updated);
            return updated > 0 ? AjaxResult.success("回写成功") : AjaxResult.error("回写失败");
        } catch (Exception e) {
            log.warn("接收SVA素材回写异常", e);
            return AjaxResult.error("回写失败");
        }
    }

    @Override
    public void consumeSvaDetectEvent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        try {
            JSONObject body = JSON.parseObject(message);
            if (body == null) {
                log.warn("SVA detect.event消费失败: payload解析为空");
                return;
            }

            String type = Optional.ofNullable(body.getString("type")).orElse("").trim();
            if (!"detect.event".equals(type)) {
                return;
            }

            String behaviorType = normalizeBehaviorType(resolveString(body, "behaviorType", "behavior_type"));
            if (behaviorType.isEmpty()) {
                log.debug("SVA detect.event已忽略: behaviorType={} eventId={}",
                    behaviorType,
                    resolveString(body, "eventId", "event_id"));
                return;
            }

            log.info("SVA detect.event已接收: eventId={} behaviorType={} eventState={} controlCode={} trackId={}",
                resolveString(body, "eventId", "event_id"),
                behaviorType,
                resolveString(body, "eventState", "event_state"),
                resolveString(body, "controlCode", "control_code"),
                resolveInteger(body, "trackId", "track_id"));

            upsertRuleDetectEvent(body, behaviorType);
        } catch (Exception e) {
            log.warn("消费SVA detect.event失败: {}", message, e);
        }
    }

    private void upsertRuleDetectEvent(JSONObject body, String behaviorType) {
        String eventId = resolveString(body, "eventId", "event_id");
        if (eventId.isEmpty()) {
            log.warn("SVA规则事件缺少eventId, body={}", body);
            return;
        }

        String eventState = resolveString(body, "eventState", "event_state").toLowerCase(Locale.ROOT);
        if (!"start".equals(eventState) && !"update".equals(eventState) && !"end".equals(eventState)) {
            log.warn("SVA规则事件状态已忽略: eventId={} eventState={} behaviorType={}", eventId, eventState, behaviorType);
            return;
        }

        AlarmTypeMeta alarmTypeMeta = resolveAlarmTypeMeta(behaviorType);
        if (alarmTypeMeta == null) {
            log.debug("SVA规则事件行为类型未支持: eventId={} behaviorType={}", eventId, behaviorType);
            return;
        }

        String controlCode = resolveString(body, "controlCode", "control_code");
        String imagePath = resolveString(body, "image_path", "imagePath");
        String videoPath = resolveString(body, "video_path", "videoPath");
        String mediaStatus = resolveString(body, "sva_media_status", "media_status", "status");
        String eventKey = resolveString(body, "eventKey", "event_key");
        String customAlarmTypeName = resolveCustomAlarmTypeName(body);
        Long businessEventId = resolveLong(body, "businessEventId", "business_event_id");
        String businessTemplateId = resolveString(body, "businessTemplateId", "business_template_id", "templateId", "template_id");
        Integer businessTemplateVersion = resolveInteger(body, "businessTemplateVersion", "business_template_version", "templateVersion", "template_version");
        Integer trackId = resolveInteger(body, "trackId", "track_id");
        String ruleId = resolveString(body, "ruleId", "rule_id");
        String regionId = resolveString(body, "regionId", "region_id");
        String regionName = resolveString(body, "regionName", "region_name");
        String lineId = resolveString(body, "lineId", "line_id");
        String lineName = resolveString(body, "lineName", "line_name");
        String crossingDirection = resolveString(body, "crossingDirection", "crossing_direction");
        String alarmTime = normalizeAlarmTime(resolveEventStartTime(body));
        String eventTime = normalizeAlarmTime(resolveEventCurrentTime(body));
        Long durationMs = calculateDurationMs(body);

        DeploymentTask deploymentTask = null;
        HDevice device = null;
        boolean aiReviewEnabled = false;
        String deviceId = controlCode.isEmpty() ? "" : controlCode;
        String deviceName = "未知设备";
        String orgIndex = "";
        String orgName = "";

        if (!controlCode.isEmpty()) {
            deploymentTask = deploymentTaskService.selectDeploymentTaskById(controlCode);
            if (deploymentTask != null) {
                aiReviewEnabled = deploymentTask.getAiReviewEnabled() == null
                    || Boolean.TRUE.equals(deploymentTask.getAiReviewEnabled());
                String mappedDeviceId = Optional.ofNullable(deploymentTask.getDeviceId()).orElse("").trim();
                if (!mappedDeviceId.isEmpty()) {
                    deviceId = mappedDeviceId;
                    device = hDeviceService.selectDeviceByApeId(mappedDeviceId);
                    if (device != null) {
                        if (device.getName() != null && !device.getName().trim().isEmpty()) {
                            deviceName = device.getName().trim();
                        } else {
                            deviceName = mappedDeviceId;
                        }
                        if (device.getOrg_index() != null && !device.getOrg_index().trim().isEmpty()) {
                            orgIndex = device.getOrg_index().trim();
                        }
                        if (device.getOrg_name() != null && !device.getOrg_name().trim().isEmpty()) {
                            orgName = device.getOrg_name().trim();
                        }
                    } else {
                        deviceName = mappedDeviceId;
                    }
                }
            }
        }
        int alarmIntervalSec = deploymentTask == null || deploymentTask.getAlarmIntervalSec() == null
            ? 0 : Math.max(0, deploymentTask.getAlarmIntervalSec());
        if (isMediaServerRecordEngine(deploymentTask)) {
            mediaStatus = "";
        }

        String absoluteImageUrl = resolveSimpleAlarmImageUrl(deploymentTask, device, imagePath);
        String absoluteVideoUrl = resolveMediaAbsoluteUrl(deploymentTask, device, videoPath);
        HWaring existing = hWaringService.selectWaringById(eventId);
        if (existing == null) {
            if (!"start".equals(eventState)) {
                log.info("SVA规则事件未命中已落库记录, 已忽略非start事件: eventId={} behaviorType={} eventState={} controlCode={} trackId={}",
                    eventId, behaviorType, eventState, controlCode, trackId);
                return;
            }

            HWaring recent = hWaringService.selectLatestSvaRuleWaringForInterval(controlCode,
                behaviorType, ruleId, regionId, lineId, crossingDirection, alarmTime,
                alarmIntervalSec);
            if (recent != null) {
                log.info("SVA规则事件命中告警间隔, 已跳过落库: eventId={} recentId={} behaviorType={} controlCode={} intervalSec={} ruleId={} regionId={} lineId={} dir={}",
                    eventId, recent.getId(), behaviorType, controlCode, alarmIntervalSec, ruleId, regionId,
                    lineId, crossingDirection);
                return;
            }

            HWaring waring = buildRuleWaring(eventId, controlCode, eventKey, eventState, behaviorType,
                ruleId, regionId, regionName, lineId, lineName, crossingDirection, trackId,
                deviceId, deviceName, orgIndex, orgName, alarmTime, imagePath, absoluteImageUrl,
                videoPath, absoluteVideoUrl, mediaStatus,
                durationMs, "end".equals(eventState) ? eventTime : null, alarmTypeMeta, customAlarmTypeName,
                businessEventId, businessTemplateId, businessTemplateVersion,
                aiReviewEnabled, deploymentTask == null ? null : deploymentTask.getAiReviewPrompt());
            int insert = hWaringService.insertWaring(waring);
            log.info("SVA规则事件落库插入: eventId={} behaviorType={} eventState={} inserted={} controlCode={} trackId={}",
                eventId, behaviorType, eventState, insert, controlCode, trackId);
            if (insert == 1 && "start".equals(eventState)) {
                if (isMediaServerRecordEngine(deploymentTask)) {
                    requestZlmRecordTask(waring, deploymentTask, device);
                } else {
                    requestSvaMediaBinding(waring, deploymentTask, device, behaviorType);
                }
                pushNewWarning(waring);
            }
            return;
        }

        HWaring update = new HWaring();
        update.setId(eventId);
        update.setControl_code(controlCode.isEmpty() ? null : controlCode);
        update.setSva_event_key(eventKey.isEmpty() ? null : eventKey);
        update.setSva_event_state(eventState);
        update.setSva_behavior_type(behaviorType);
        update.setSva_rule_id(ruleId.isEmpty() ? null : ruleId);
        update.setAlarm_type_name(customAlarmTypeName.isEmpty() ? null : customAlarmTypeName);
        update.setSva_business_event_id(businessEventId);
        update.setSva_business_event_name(customAlarmTypeName.isEmpty() ? null : customAlarmTypeName);
        update.setSva_business_template_id(businessTemplateId.isEmpty() ? null : businessTemplateId);
        update.setSva_business_template_version(businessTemplateVersion);
        update.setSva_region_id(regionId.isEmpty() ? null : regionId);
        update.setSva_region_name(regionName.isEmpty() ? null : regionName);
        update.setSva_line_id(lineId.isEmpty() ? null : lineId);
        update.setSva_line_name(lineName.isEmpty() ? null : lineName);
        update.setSva_crossing_direction(crossingDirection.isEmpty() ? null : crossingDirection);
        update.setSva_track_id(trackId);
        update.setDuration_ms(durationMs);
        if (!imagePath.isEmpty()) {
            update.setPicture_url(imagePath);
            update.setPicture_absolute_url(absoluteImageUrl);
        }
        if (!mediaStatus.isEmpty()) {
            update.setSva_media_status(mediaStatus);
        }
        if (existing.getAlarm_time() == null || existing.getAlarm_time().trim().isEmpty()) {
            update.setAlarm_time(alarmTime);
        }
        if ("end".equals(eventState)) {
            update.setEnd_time(eventTime);
        }
        int updated = hWaringService.updateSvaLifecycleWaring(update);
        log.info("SVA规则事件落库更新: eventId={} behaviorType={} eventState={} updated={} controlCode={} trackId={} durationMs={}",
            eventId, behaviorType, eventState, updated, controlCode, trackId, durationMs);
    }

    private HWaring buildRuleWaring(String eventId, String controlCode, String eventKey, String eventState,
        String behaviorType, String ruleId, String regionId, String regionName, String lineId, String lineName,
        String crossingDirection, Integer trackId, String deviceId, String deviceName, String orgIndex,
        String orgName, String alarmTime, String imagePath, String absoluteImageUrl,
        String videoPath, String absoluteVideoUrl, String mediaStatus, Long durationMs,
        String endTime, AlarmTypeMeta alarmTypeMeta, String customAlarmTypeName,
        Long businessEventId, String businessTemplateId, Integer businessTemplateVersion,
        boolean aiReviewEnabled, String aiReviewPrompt) {
        HWaring waring = new HWaring();
        waring.setId(eventId);
        waring.setAlarm_type(alarmTypeMeta.alarmType);
        String resolvedAlarmTypeName = customAlarmTypeName == null || customAlarmTypeName.trim().isEmpty()
            ? alarmTypeMeta.alarmTypeName
            : customAlarmTypeName.trim();
        waring.setAlarm_type_name(resolvedAlarmTypeName);
        waring.setAlarm_level("3");
        waring.setAlarm_level_name("一般");
        waring.setDevice_id(deviceId);
        waring.setDevice_name(deviceName);
        waring.setOrg_index(orgIndex);
        waring.setOrg_name(orgName);
        waring.setLongitude("0");
        waring.setLatitude("0");
        waring.setPicture_url(imagePath);
        waring.setPicture_absolute_url(absoluteImageUrl);
        if (videoPath != null && !videoPath.trim().isEmpty()) {
            waring.setVideo_url(videoPath);
            waring.setVideo_absolute_url(absoluteVideoUrl);
        }
        if (mediaStatus != null && !mediaStatus.trim().isEmpty()) {
            waring.setSva_media_status(mediaStatus);
        }
        waring.setIs_handle(0);
        waring.setIs_enable(1);
        waring.setTeam("");
        waring.setIp(ip);
        waring.setAlarm_time(alarmTime);
        waring.setControl_code(controlCode);
        waring.setSva_event_key(eventKey);
        waring.setSva_event_state(eventState);
        waring.setSva_behavior_type(behaviorType);
        waring.setSva_rule_id(ruleId);
        waring.setSva_business_event_id(businessEventId);
        waring.setSva_business_event_name(customAlarmTypeName == null || customAlarmTypeName.trim().isEmpty() ? null : customAlarmTypeName.trim());
        waring.setSva_business_template_id(businessTemplateId);
        waring.setSva_business_template_version(businessTemplateVersion);
        waring.setSva_region_id(regionId);
        waring.setSva_region_name(regionName);
        waring.setSva_line_id(lineId);
        waring.setSva_line_name(lineName);
        waring.setSva_crossing_direction(crossingDirection);
        waring.setSva_track_id(trackId);
        waring.setEnd_time(endTime);
        waring.setDuration_ms(durationMs);
        waring.setAi_review_enabled(aiReviewEnabled);
        waring.setAi_review_prompt(aiReviewPrompt);
        return waring;
    }

    private void pushNewWarning(HWaring waring) {
        JSONObject indexJson = new JSONObject();
        Map<String, Object> map = new HashMap<>();
        map.put("w_id", waring.getW_id());
        map.put("level", waring.getAlarm_level_name());
        map.put("type", waring.getAlarm_type_name());
        map.put("time", waring.getAlarm_time());
        map.put("device", waring.getDevice_id());
        map.put("url", waring.getPicture_absolute_url());
        map.put("team", waring.getTeam());
        indexJson.put("newWarning", map);
        WebSocketUsers.sendMessageToUsersByText(indexJson.toJSONString());
    }

    private String resolveEventStartTime(JSONObject body) {
        Object startTimestamp = resolveObject(body, "startTimestampMs", "start_timestamp_ms");
        if (startTimestamp != null) {
            return String.valueOf(startTimestamp);
        }
        String alarmTime = resolveString(body, "alarm_time", "alarmTime");
        if (!alarmTime.trim().isEmpty()) {
            return alarmTime;
        }
        Object timestamp = resolveObject(body, "timestampMs", "timestamp_ms");
        return timestamp == null ? "" : String.valueOf(timestamp);
    }

    private String resolveEventCurrentTime(JSONObject body) {
        Object timestamp = resolveObject(body, "timestampMs", "timestamp_ms");
        if (timestamp != null) {
            return String.valueOf(timestamp);
        }
        String alarmTime = resolveString(body, "alarm_time", "alarmTime");
        return alarmTime;
    }

    private Long calculateDurationMs(JSONObject body) {
        if (body == null) {
            return null;
        }
        Long durationMs = resolveLong(body, "durationMs", "duration_ms");
        if (durationMs != null) {
            return durationMs < 0L ? 0L : durationMs;
        }
        Long startTimestampMs = resolveLong(body, "startTimestampMs", "start_timestamp_ms");
        Long timestampMs = resolveLong(body, "timestampMs", "timestamp_ms");
        if (startTimestampMs == null || timestampMs == null) {
            return null;
        }
        long duration = timestampMs - startTimestampMs;
        return duration < 0L ? 0L : duration;
    }

    private String normalizeAlarmTime(String alarmTimeRaw) {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        if (alarmTimeRaw == null || alarmTimeRaw.trim().isEmpty()) {
            return now;
        }

        String value = alarmTimeRaw.trim();
        if (value.matches("^\\d{10,13}$")) {
            try {
                long ts = Long.parseLong(value);
                if (value.length() == 10) {
                    ts = ts * 1000;
                }
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ts));
            } catch (Exception e) {
                log.warn("SVA告警alarm_time时间戳解析失败, alarm_time={}", alarmTimeRaw);
                return now;
            }
        }

        String[] patterns = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern);
                parser.setLenient(false);
                Date parsed = parser.parse(value);
                if (parsed != null) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(parsed);
                }
            } catch (Exception ignored) {
            }
        }

        log.warn("SVA告警alarm_time无法解析, 使用当前时间, alarm_time={}", alarmTimeRaw);
        return now;
    }

    private String resolveSimpleAlarmImageUrl(DeploymentTask deploymentTask, HDevice device, String imagePath) {
        return resolveMediaAbsoluteUrl(deploymentTask, device, imagePath);
    }

    private boolean isMediaServerRecordEngine(DeploymentTask deploymentTask) {
        if (deploymentTask == null || deploymentTask.getRecordEngine() == null) {
            return false;
        }
        return ENGINE_M_SERVER.equals(deploymentTask.getRecordEngine().trim());
    }

    private String resolveMediaAbsoluteUrl(DeploymentTask deploymentTask, HDevice device, String mediaPath) {
        if (mediaPath == null) {
            return "";
        }

        String trimmed = mediaPath.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (isAbsoluteMediaUrl(trimmed)) {
            return trimmed;
        }

        String relativePath = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        String host = resolveRecordEngineHost(deploymentTask, device);
        if (host == null || host.trim().isEmpty()) {
            return relativePath;
        }

        String normalizedHost = host.trim();
        if (normalizedHost.endsWith("/")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (!normalizedHost.startsWith("http://") && !normalizedHost.startsWith("https://")) {
            normalizedHost = "http://" + normalizedHost;
        }
        return normalizedHost + "/" + relativePath;
    }

    private void requestZlmRecordTask(HWaring waring, DeploymentTask deploymentTask, HDevice device) {
        if (waring == null || deploymentTask == null || device == null) {
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED, "missing deploymentTask or device");
            return;
        }

        ZlmServer zlmServer = resolveEnabledZlmServer(device);
        if (zlmServer == null) {
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED, "zlm server unavailable");
            log.warn("ZLM录像已跳过: 未找到启用中的ZLM节点, controlCode={} deviceId={}",
                waring.getControl_code(), deploymentTask.getDeviceId());
            return;
        }

        if (zlmServer.getHost() == null || zlmServer.getHost().trim().isEmpty() || zlmServer.getApi_port() == null
            || zlmServer.getSecret() == null || zlmServer.getSecret().trim().isEmpty()) {
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED, "zlm server config incomplete");
            log.warn("ZLM录像已跳过: 节点配置不完整, controlCode={} deviceId={} zlmServerId={}",
                waring.getControl_code(), deploymentTask.getDeviceId(), zlmServer.getId());
            return;
        }

        String stream = Optional.ofNullable(deploymentTask.getDeviceId()).orElse("").trim();
        if (stream.isEmpty()) {
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED, "empty device stream");
            log.warn("ZLM录像已跳过: deviceId为空, controlCode={} alarmId={}", waring.getControl_code(), waring.getId());
            return;
        }

        String app = zlmServer.getApp() == null || zlmServer.getApp().trim().isEmpty()
            ? DEFAULT_ZLM_APP
            : zlmServer.getApp().trim();
        String recordFileName = buildZlmRecordFileName(waring);
        String storagePath = recordFileName;
        String accessPath = buildZlmAccessMediaPath(app, stream, recordFileName);
        String absoluteUrl = resolveMediaAbsoluteUrl(deploymentTask, device, accessPath);
        String requestUrl = buildZlmStartRecordUrl(zlmServer, app, stream, storagePath,
            DEFAULT_ZLM_BACK_MS, DEFAULT_ZLM_FORWARD_MS);

        try {
            String response = restTemplate.getForObject(URI.create(requestUrl), String.class);
            JSONObject result = StringUtils.isBlank(response) ? null : JSON.parseObject(response);
            Integer codeValue = result == null ? null : result.getInteger("code");
            int code = codeValue == null ? Integer.MIN_VALUE : codeValue;
            if (code == 0) {
                updateZlmMediaFields(waring, accessPath, absoluteUrl, ZLM_MEDIA_STATUS_RECORD_STARTED, null);
                log.info("ZLM录像任务启动成功: controlCode={} alarmId={} stream={} storagePath={} accessPath={} zlmServerId={}",
                    waring.getControl_code(), waring.getId(), stream, storagePath, accessPath, zlmServer.getId());
                return;
            }

            String mediaError = result == null ? "empty zlm response" : result.getString("msg");
            if (mediaError == null || mediaError.trim().isEmpty()) {
                mediaError = response;
            }
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED, truncateMediaError(mediaError));
            log.warn("ZLM录像任务启动失败: controlCode={} alarmId={} stream={} storagePath={} accessPath={} zlmServerId={} response={}",
                waring.getControl_code(), waring.getId(), stream, storagePath, accessPath, zlmServer.getId(), response);
        } catch (Exception e) {
            updateZlmMediaFields(waring, null, null, ZLM_MEDIA_STATUS_RECORD_FAILED,
                truncateMediaError(e.getMessage()));
            log.warn("ZLM录像任务请求异常, controlCode={} alarmId={} stream={} storagePath={} accessPath={} zlmServerId={}",
                waring.getControl_code(), waring.getId(), stream, storagePath, accessPath, zlmServer.getId(), e);
        }
    }

    private ZlmServer resolveEnabledZlmServer(HDevice device) {
        if (device == null) {
            return null;
        }
        Long zlmServerId = device.getZlm_server_id() == null ? DEFAULT_SERVER_ID : device.getZlm_server_id();
        return zlmServerMapper.selectEnabledById(zlmServerId);
    }

    private String buildZlmRecordFileName(HWaring waring) {
        String alarmId = safePathSegment(waring == null ? null : waring.getId());
        return alarmId + ".mp4";
    }

    private String buildZlmAccessMediaPath(String app, String stream, String recordFileName) {
        return "zlm/" + safePathSegment(app) + "/" + safePathSegment(stream) + "/" + recordFileName;
    }

    private String safePathSegment(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        return trimmed.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String buildZlmStartRecordUrl(ZlmServer zlmServer, String app, String stream, String path,
                                          long backMs, long forwardMs) {
        String baseHost = normalizeHttpHost(zlmServer.getHost());
        StringBuilder builder = new StringBuilder();
        builder.append(baseHost)
            .append(":")
            .append(zlmServer.getApi_port())
            .append("/index/api/startRecordTask")
            .append("?secret=")
            .append(urlEncode(zlmServer.getSecret()))
            .append("&vhost=")
            .append(urlEncode(DEFAULT_ZLM_VHOST))
            .append("&app=")
            .append(urlEncode(app))
            .append("&stream=")
            .append(urlEncode(stream))
            .append("&path=")
            .append(urlEncode(path))
            .append("&back_ms=")
            .append(backMs)
            .append("&forward_ms=")
            .append(forwardMs);
        return builder.toString();
    }

    private String normalizeHttpHost(String host) {
        String normalized = host == null ? "" : host.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String truncateMediaError(String mediaError) {
        if (mediaError == null) {
            return null;
        }
        String trimmed = mediaError.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500);
    }

    private void updateZlmMediaFields(HWaring waring, String videoPath, String absoluteUrl,
                                      String mediaStatus, String mediaError) {
        if (waring == null || waring.getId() == null || waring.getId().trim().isEmpty()) {
            return;
        }

        HWaring update = new HWaring();
        update.setId(waring.getId());
        update.setVideo_url(videoPath);
        update.setVideo_absolute_url(absoluteUrl);
        update.setSva_media_status(mediaStatus);
        update.setSva_media_error(mediaError);
        hWaringService.updateSvaMediaFields(update);

        if (videoPath != null) {
            waring.setVideo_url(videoPath);
        }
        if (absoluteUrl != null) {
            waring.setVideo_absolute_url(absoluteUrl);
        }
        if (mediaStatus != null) {
            waring.setSva_media_status(mediaStatus);
        }
        waring.setSva_media_error(mediaError);
    }

    private void requestSvaMediaBinding(HWaring waring, DeploymentTask deploymentTask, HDevice device,
                                        String behaviorType) {
        if (waring == null || deploymentTask == null || device == null) {
            log.warn("SVA素材绑定已跳过: 参数不完整, alarmId={} controlCode={} deploymentId={} deviceId={}",
                waring == null ? null : waring.getId(),
                waring == null ? null : waring.getControl_code(),
                deploymentTask == null ? null : deploymentTask.getDeploymentId(),
                device == null ? null : device.getApe_id());
            return;
        }

        String bindUrl = resolveSvaBindMediaUrl(device);
        if (bindUrl == null || bindUrl.isEmpty()) {
            log.warn("SVA素材绑定已跳过: 未找到算法服务地址, controlCode={} deviceId={}",
                waring.getControl_code(), deploymentTask.getDeviceId());
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("alarm_id", waring.getId());
        payload.put("event_id", waring.getId());
        payload.put("control_code", waring.getControl_code());
        payload.put("alarm_time", waring.getAlarm_time());
        payload.put("behavior_type", behaviorType);
        payload.put("rule_id", waring.getSva_rule_id());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            HttpEntity<String> entity = new HttpEntity<>(payload.toJSONString(), headers);
            log.info("SVA素材绑定请求发送: bindUrl={} alarmId={} eventId={} controlCode={} behaviorType={}",
                bindUrl, waring.getId(), waring.getId(), waring.getControl_code(), behaviorType);
            String response = restTemplate.postForObject(bindUrl, entity, String.class);
            log.info("SVA素材绑定请求完成: bindUrl={} alarmId={} controlCode={} response={}",
                bindUrl, waring.getId(), waring.getControl_code(), response);
        } catch (Exception e) {
            log.warn("SVA素材绑定请求失败, bindUrl={} alarmId={} controlCode={}",
                bindUrl, waring.getId(), waring.getControl_code(), e);
        }
    }

    private String resolveSvaBindMediaUrl(HDevice device) {
        if (device == null || device.getSva_server_id() == null) {
            return null;
        }
        SvaServer svaServer = svaServerMapper.selectEnabledById(device.getSva_server_id());
        if (svaServer == null || svaServer.getHost() == null || svaServer.getHost().trim().isEmpty()
            || svaServer.getAnalyzer_port() == null) {
            return null;
        }
        return "http://" + svaServer.getHost().trim() + ":" + svaServer.getAnalyzer_port() + "/api/alarm/bind-media";
    }

    private String resolveRecordEngineHost(DeploymentTask deploymentTask, HDevice device) {
        if (deploymentTask == null || device == null) {
            return null;
        }

        String recordEngine = deploymentTask.getRecordEngine();
        if (recordEngine == null || recordEngine.trim().isEmpty()) {
            recordEngine = ENGINE_A_SERVER;
        } else {
            recordEngine = recordEngine.trim();
        }

        if (ENGINE_A_SERVER.equals(recordEngine)) {
            Long svaServerId = device.getSva_server_id();
            if (svaServerId == null) {
                return null;
            }
            SvaServer svaServer = svaServerMapper.selectEnabledById(svaServerId);
            return svaServer == null ? null : svaServer.getHost();
        }

        if (ENGINE_M_SERVER.equals(recordEngine)) {
            Long zlmServerId = device.getZlm_server_id();
            if (zlmServerId == null) {
                return null;
            }
            ZlmServer zlmServer = zlmServerMapper.selectEnabledById(zlmServerId);
            return zlmServer == null ? null : zlmServer.getHost();
        }

        return null;
    }

    private boolean isAbsoluteMediaUrl(String value) {
        return value.startsWith("http://")
            || value.startsWith("https://")
            || value.startsWith("file://")
            || value.startsWith("data:");
    }

    private String resolveString(JSONObject body, String... keys) {
        if (body == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String value = body.getString(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private Object resolveObject(JSONObject body, String... keys) {
        if (body == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            Object value = body.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer resolveInteger(JSONObject body, String... keys) {
        Object value = resolveObject(body, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long resolveLong(JSONObject body, String... keys) {
        Object value = resolveObject(body, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveCustomAlarmTypeName(JSONObject body) {
        return resolveString(body,
            "customEventName",
            "custom_event_name",
            "alarmTypeName",
            "alarm_type_name",
            "businessEventName",
            "business_event_name");
    }

    private String normalizeBehaviorType(String behaviorType) {
        String normalized = behaviorType == null ? "" : behaviorType.trim().toLowerCase(Locale.ROOT);
        if ("cross_line".equals(normalized) || "enter_region".equals(normalized)
            || "exit_region".equals(normalized) || "dwell".equals(normalized)
            || "low_speed".equals(normalized) || "loitering".equals(normalized) || "absence".equals(normalized)
            || "count_threshold".equals(normalized) || "occupancy".equals(normalized)
            || "direction_move".equals(normalized) || "direction_reverse".equals(normalized)
            || "relation_near".equals(normalized) || "relation_apart".equals(normalized)
            || "relation_not_contains".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private AlarmTypeMeta resolveAlarmTypeMeta(String behaviorType) {
        if ("cross_line".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_CROSS_LINE_ALARM_TYPE, SVA_CROSS_LINE_ALARM_TYPE_NAME);
        }
        if ("enter_region".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_ENTER_REGION_ALARM_TYPE, SVA_ENTER_REGION_ALARM_TYPE_NAME);
        }
        if ("exit_region".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_EXIT_REGION_ALARM_TYPE, SVA_EXIT_REGION_ALARM_TYPE_NAME);
        }
        if ("dwell".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_DWELL_ALARM_TYPE, SVA_DWELL_ALARM_TYPE_NAME);
        }
        if ("low_speed".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_LOW_SPEED_ALARM_TYPE, SVA_LOW_SPEED_ALARM_TYPE_NAME);
        }
        if ("loitering".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_LOITERING_ALARM_TYPE, SVA_LOITERING_ALARM_TYPE_NAME);
        }
        if ("absence".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_ABSENCE_ALARM_TYPE, SVA_ABSENCE_ALARM_TYPE_NAME);
        }
        if ("count_threshold".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_COUNT_THRESHOLD_ALARM_TYPE, SVA_COUNT_THRESHOLD_ALARM_TYPE_NAME);
        }
        if ("occupancy".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_OCCUPANCY_ALARM_TYPE, SVA_OCCUPANCY_ALARM_TYPE_NAME);
        }
        if ("direction_move".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_DIRECTION_MOVE_ALARM_TYPE, SVA_DIRECTION_MOVE_ALARM_TYPE_NAME);
        }
        if ("direction_reverse".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_DIRECTION_REVERSE_ALARM_TYPE, SVA_DIRECTION_REVERSE_ALARM_TYPE_NAME);
        }
        if ("relation_near".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_RELATION_NEAR_ALARM_TYPE, SVA_RELATION_NEAR_ALARM_TYPE_NAME);
        }
        if ("relation_apart".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_RELATION_APART_ALARM_TYPE, SVA_RELATION_APART_ALARM_TYPE_NAME);
        }
        if ("relation_not_contains".equals(behaviorType)) {
            return new AlarmTypeMeta(SVA_RELATION_NOT_CONTAINS_ALARM_TYPE, SVA_RELATION_NOT_CONTAINS_ALARM_TYPE_NAME);
        }
        return null;
    }

    private static class AlarmTypeMeta {
        private final String alarmType;
        private final String alarmTypeName;

        private AlarmTypeMeta(String alarmType, String alarmTypeName) {
            this.alarmType = alarmType;
            this.alarmTypeName = alarmTypeName;
        }
    }

    /**
     * 获取报警信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(HWaring waring) {
        List<HWaring> list = hWaringService.selectWaringList(waring, getUserId(), 0);
        return getDataTable(list);
    }

    /**
     * 报警处理
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/handle")
    public AjaxResult handle(@RequestBody HHandle handle) throws ParseException {
        if (handle == null || handle.getW_id() == null) {
            return error("参数错误：w_id不能为空");
        }
        if (handle.getH_title() == null || handle.getH_title().trim().isEmpty()) {
            return error("参数错误：h_title不能为空");
        }
        if (handle.getH_remark() == null || handle.getH_remark().trim().isEmpty()) {
            return error("参数错误：h_remark不能为空");
        }
        if (hWaringService.handle(handle) != 1) {
            return error("处理失败");
        } else {
            SysUser user = userService.selectUserById(getUserId());
            String id = hWaringService.getId(handle.getW_id());
            HWaring data = hWaringService.selectWaringById(id);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            handle.setH_create_time(sdf.format(new Date()));
            handle.setH_org_index(data.getOrg_index());
            handle.setH_org_name(user.getUserName());
            Date date = sdf.parse(data.getAlarm_time());
            handle.setH_time(sdf.format(TimeUtil.getNextDay(date, 7)));
            int res = hHandleService.insertHandle(handle);
            if (res >= 1) {
                handle.setId(id);
                Object token = redisTemplate.boundValueOps("token").get();
                if (token == null) {
                    return toAjax(1);
                }
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", token.toString());
                headers.set("User", "usercode:" + username);
                headers.set("Cookie", "usercode=" + username);
                headers.set("Content-Type", "application/json");
                String url2 = "http://192.168.1.29:9114/waring/waring/handle";
                HttpEntity<HHandle> entity2 = new HttpEntity<>(handle, headers);
                restTemplate.postForObject(url2, entity2, String.class);
                return toAjax(1);
            } else {
                return error("处理失败");
            }
        }
    }

    /**
     * 查看详情
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getOne(@PathVariable int id) {
        Details map = hWaringService.getOne(id);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警信息导出
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response, HWaring waring) {
        List<HWaring> list = hWaringService.selectWaringList(waring, getUserId(), 1);
        ExcelUtil<HWaring> util = new ExcelUtil<HWaring>(HWaring.class);
        util.exportExcel(response, list, "报警数据");
    }

    /**
     * 获取历史报警
     */
    @PreAuthorize("@ss.hasPermi('waring:device:history')")
    @GetMapping("/getHistoryWaring")
    @ResponseBody
    public TableDataInfo getHistoryWaring(HWaring waring) {
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        List<HWaring> list = hWaringService.getHistoryWaring(waring.getDevice_id());
        return getDataTable(list);
    }

    /**
     * 检修牌识别
     */
    @GetMapping("/getRecondition")
    public TableDataInfo getRecondition(HWaring waring) {
        List<HWaring> list = hWaringService.selectReconditionList(waring, getUserId());
        return getDataTable(list);
    }

    /**
     * 获取误报报警
     *
     * @param waring
     * @return
     */
    @GetMapping("getWubao")
    public TableDataInfo getWubao(HWaring waring) {
        List<HWaring> list = hWaringService.selectWubaoList(waring, getUserId());
        return getDataTable(list);
    }

    @GetMapping("getTeamWaring")
    public AjaxResult getTeamWaring() {
        List<HBinding> types = hBindingService.getTeamWaring();
        return new AjaxResult(200, "操作成功", types);
    }
}
