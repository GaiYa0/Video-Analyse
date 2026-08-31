package com.ruoyi.waring.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.waring.domain.HType;
import com.ruoyi.waring.service.HTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/waring/type")
public class HTypeController extends BaseController {

    @Autowired
    private HTypeService hTypeService;

    @PostMapping("/insertType")
    public AjaxResult insertType(@RequestBody HType type) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (!hTypeService.checkTypeUnique(type)) {
            return error("新增报警类型'" + type.getAlarm_type_name() + "'失败，该通道已经配置该报警类型");
        }
        type.setCreate_user(getUsername());
        type.setCreate_time(formatter.format(new Date()));
        return toAjax(hTypeService.insertType(type));
    }

    @GetMapping("/list")
    public TableDataInfo list(HType type) {
        startPage();
        List<HType> list = hTypeService.selectTypeList(type, getUserId());
        return getDataTable(list);
    }

    @DeleteMapping("/{tIds}")
    public AjaxResult remove(@PathVariable Long[] tIds) {
        return toAjax(hTypeService.deleteType(tIds));
    }

    /**
     * 获取报警类型下拉
     */
    @GetMapping("/getTypeWaring")
    public AjaxResult getTypeWaring() {
        List<HType> types = hTypeService.getTypeWaring();
        return new AjaxResult(200, "操作成功", types);
    }

    /**
     * 获取报警列表筛选用的报警类型候选，优先按业务名称聚合
     */
    @GetMapping("/getAlarmTypeFilterOptions")
    public AjaxResult getAlarmTypeFilterOptions() {
        List<HType> types = hTypeService.getAlarmTypeFilterOptions();
        return new AjaxResult(200, "操作成功", types);
    }
}
