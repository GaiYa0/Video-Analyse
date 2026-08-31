package com.ruoyi.waring.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.waring.domain.HDevice;
import com.ruoyi.waring.service.HDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/waring/device")
public class HDeviceController extends BaseController {

    @Autowired
    private HDeviceService hDeviceService;

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 获取设备信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(HDevice device) {
        List<HDevice> list = hDeviceService.selectDeviceList(device, getUserId());
        Object token = redisTemplate.boundValueOps("token").get();
        return getDataTable(list);
    }

    /**
     * 获取离线设备信息
     */
    @GetMapping("lixian")
    public TableDataInfo lixian(HDevice device) {
        List<HDevice> list = hDeviceService.selectLDeviceList(device, getUserId());
        Object token = redisTemplate.boundValueOps("token").get();
        return getDataTable(list);
    }

    /**
     * 离线设备信息导出
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response, HDevice device) {
        List<HDevice> list = hDeviceService.selectLDeviceList(device, getUserId());
        ExcelUtil<HDevice> util = new ExcelUtil<HDevice>(HDevice.class);
        util.exportExcel(response, list, "离线设备数据");
    }

    /**
     * 查询设备详细
     */
    @PreAuthorize("@ss.hasPermi('waring:device:query')")
    @GetMapping(value = "/{apeId}")
    public AjaxResult getInfo(@PathVariable String apeId) {
        return success(hDeviceService.selectDeviceByApeId(apeId));
    }

    /**
     * 直连设备实时播放地址
     */
    @PreAuthorize("@ss.hasPermi('waring:device:query')")
    @GetMapping("/live/direct/{apeId}")
    public AjaxResult getDirectLiveUrl(@PathVariable String apeId) {
        Map<String, Object> data = hDeviceService.getDirectLiveUrl(apeId);
        return success(data);
    }

    /**
     * 新增设备
     */
    @PreAuthorize("@ss.hasPermi('waring:device:add')")
    @PostMapping
    public AjaxResult add(@RequestBody HDevice device) {
        return toAjax(hDeviceService.insertDeviceCrud(device));
    }

    /**
     * 修改设备
     */
    @PreAuthorize("@ss.hasPermi('waring:device:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody HDevice device) {
        return toAjax(hDeviceService.updateDevice(device));
    }

    /**
     * 删除设备
     */
    @PreAuthorize("@ss.hasPermi('waring:device:remove')")
    @DeleteMapping("/{apeIds}")
    public AjaxResult remove(@PathVariable String[] apeIds) {
        return toAjax(hDeviceService.deleteDeviceByApeIds(apeIds));
    }

    /**
     * 启动设备实时监控
     */
    @PreAuthorize("@ss.hasPermi('waring:device:start')")
    @PostMapping("/monitor/{apeId}/start")
    public AjaxResult startMonitor(@PathVariable String apeId) {
        HDevice existedDevice = hDeviceService.selectDeviceByApeId(apeId);
        if (existedDevice == null) {
            return buildMonitorActionResult(false, "启动", "设备不存在", null);
        }

        try {
            int rows = hDeviceService.startMonitor(apeId);
            if (rows <= 0) {
                HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
                return buildMonitorActionResult(false, "启动", "启动监控失败", latest);
            }
            HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
            return buildMonitorActionResult(true, "启动", "启动监控成功", latest);
        } catch (Exception ex) {
            HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
            return buildMonitorActionResult(false, "启动", resolveMonitorFailMessage("启动", ex), latest);
        }
    }

    /**
     * 停止设备实时监控
     */
    @PreAuthorize("@ss.hasPermi('waring:device:stop')")
    @PostMapping("/monitor/{apeId}/stop")
    public AjaxResult stopMonitor(@PathVariable String apeId) {
        HDevice existedDevice = hDeviceService.selectDeviceByApeId(apeId);
        if (existedDevice == null) {
            return buildMonitorActionResult(false, "停止", "设备不存在", null);
        }

        try {
            int rows = hDeviceService.stopMonitor(apeId);
            if (rows <= 0) {
                HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
                return buildMonitorActionResult(false, "停止", "停止监控失败", latest);
            }
            HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
            return buildMonitorActionResult(true, "停止", "停止监控成功", latest);
        } catch (Exception ex) {
            HDevice latest = hDeviceService.selectDeviceByApeId(apeId);
            return buildMonitorActionResult(false, "停止", resolveMonitorFailMessage("停止", ex), latest);
        }
    }

    /**
     * 设备实时监控预览信息
     */
    @PreAuthorize("@ss.hasPermi('waring:device:query')")
    @GetMapping("/monitor/{apeId}/preview")
    public AjaxResult previewMonitor(@PathVariable String apeId) {
        return success(hDeviceService.previewMonitor(apeId));
    }

    private AjaxResult buildMonitorActionResult(boolean success, String action, String shortMessage, HDevice device) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        String defaultMessage = success ? action + "监控成功" : action + "监控失败";
        payload.put("shortMessage", StringUtils.isEmpty(shortMessage) ? defaultMessage : shortMessage);
        payload.put("data", device);
        return AjaxResult.success(payload);
    }

    private String resolveMonitorFailMessage(String action, Exception ex) {
        String fallback = action + "监控失败";
        if (ex == null) {
            return fallback;
        }

        String message = ex.getMessage();
        if (StringUtils.isEmpty(message)) {
            return fallback;
        }

        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("pull stream connect error")) {
            return "读取视频流失败，请确认设备启动了视频流";
        }
        if (lowerMessage.contains("push stream connect error")) {
            return "推送失败，请稍后再试！";
        }
        if (lowerMessage.contains("already exists")) {
            return "设备监控已经启动过";
        }
        if (lowerMessage.contains("timeout")) {
            return "连接超时";
        }
        return message;
    }
}
