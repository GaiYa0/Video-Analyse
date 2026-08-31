package com.ruoyi.waring.controller;


import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.waring.service.HDeviceService;
import com.ruoyi.waring.service.HHandleService;
import com.ruoyi.waring.service.HWaringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/index/index")
public class IndexController extends BaseController {

    @Autowired
    private HWaringService hWaringService;

    @Autowired
    private HHandleService hHandleService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private HDeviceService deviceService;

    /**
     * 本月报警数量排查
     */
    @GetMapping("/getMonthWaring")
    @ResponseBody
    public AjaxResult getMonthWaring(String org_index) {
        // 获取本月报警数据
        int nowNum = hWaringService.getMonthWaring(getUserId(), org_index);
        // 获取去年本月报警数据
        int nowLastNum = hWaringService.getLastYearMonthWaring(getUserId(), org_index);
        // 获取上个月报警数据
        int lastNum = hWaringService.getLastMonthWaring(getUserId(), org_index);
        // 获取本年报警数据
        int yearNum = hWaringService.getYearWaring(getUserId(), org_index);
        // 计算环比
        double hb;
        BigDecimal hbcha = new BigDecimal(nowNum).subtract(new BigDecimal(lastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastNum)) == 0) {
            hb = 0.00;
        } else {
            hb = hbcha.divide(new BigDecimal(lastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 计算同比
        double tb;
        BigDecimal tbcha = new BigDecimal(nowNum).subtract(new BigDecimal(nowLastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(nowLastNum)) == 0) {
            tb = 0.00;
        } else {
            tb = tbcha.divide(new BigDecimal(nowLastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        int num = hWaringService.getChuNum(getUserId(), org_index);
        // 拼接返回信息
        HashMap<String, Object> map = new HashMap<>();
        map.put("instant", nowNum);
        map.put("QOQ", hb);
        map.put("YOY", tb);
        map.put("lastYear", yearNum);
        map.put("num", num);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 本月重大报警数量排查
     */
    @GetMapping("/getMonthMajorWaring")
    @ResponseBody
    public AjaxResult getMonthMajorWaring(String org_index) {
        // 获取本月重大报警数据
        int nowNum = hWaringService.getMonthMajorWaring(getUserId(), org_index);
        // 获取去年本月重大报警数据
        int nowLastNum = hWaringService.getLastYearMajorMonthWaring(getUserId(), org_index);
        // 获取上个月重大报警数据
        int lastNum = hWaringService.getLastMonthMajorWaring(getUserId(), org_index);
        // 获取本年重大报警数据
        int yearNum = hWaringService.getYearMajorWaring(getUserId(), org_index);
        // 计算环比
        double hb;
        BigDecimal hbcha = new BigDecimal(nowNum).subtract(new BigDecimal(lastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastNum)) == 0) {
            hb = 0.00;
        } else {
            hb = hbcha.divide(new BigDecimal(lastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 计算同比
        double tb;
        BigDecimal tbcha = new BigDecimal(nowNum).subtract(new BigDecimal(nowLastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(nowLastNum)) == 0) {
            tb = 0.00;
        } else {
            tb = tbcha.divide(new BigDecimal(nowLastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 拼接返回信息
        HashMap<String, Object> map = new HashMap<>();
        map.put("instant", nowNum);
        map.put("QOQ", hb);
        map.put("YOY", tb);
        map.put("lastYear", yearNum);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 本月报警处理逾期
     */
    @GetMapping("/getMonthOverdueWaring")
    @ResponseBody
    public AjaxResult getMonthOverdueWaring(String org_index) {
        // 本月逾期数量
        int overdueNum = hWaringService.getMonthOverdueWaring(getUserId(), org_index);
        // 获取去年本月逾期
        int nowLastNum = hWaringService.getLastYearMonthOverdueWaring(getUserId(), org_index);
        // 获取上个月逾期
        int lastNum = hWaringService.getLastMonthOverdueWaring(getUserId(), org_index);
        // 获取本年逾期
        int yearNum = hWaringService.getYearOverdueWaring(getUserId(), org_index);
        // 计算环比
        double hb;
        BigDecimal hbcha = new BigDecimal(overdueNum).subtract(new BigDecimal(lastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastNum)) == 0) {
            hb = 0.00;
        } else {
            hb = hbcha.divide(new BigDecimal(lastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 计算同比
        double tb;
        BigDecimal tbcha = new BigDecimal(overdueNum).subtract(new BigDecimal(nowLastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(nowLastNum)) == 0) {
            tb = 0.00;
        } else {
            tb = tbcha.divide(new BigDecimal(nowLastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 拼接返回信息
        HashMap<String, Object> map = new HashMap<>();
        map.put("instant", overdueNum);
        map.put("QOQ", hb);
        map.put("YOY", tb);
        map.put("lastYear", yearNum);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 本月报警整改数量及整改率
     */
    @GetMapping("/getMonthHandle")
    @ResponseBody
    public AjaxResult getMonthHandle(String org_index) {
        // 获取本月报警数量
        int nowNum = hWaringService.getMonthWaringByhandle(getUserId(), org_index);
        // 获取本月处理后的报警数量
        int handleNum = hWaringService.getMonthHandle(getUserId(), org_index);
        double zg;
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(nowNum)) == 0) {
            zg = 0.00;
        } else {
            zg = new BigDecimal(handleNum).divide(new BigDecimal(nowNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("rectificationNum", handleNum);
        map.put("rate", zg);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警综合排行统计
     */
    @GetMapping("/getRanking")
    @ResponseBody
    public AjaxResult getRanking(String org_index) {
        // 报警类型排行
        List<Map<String, Object>> type = hWaringService.getTypeRanking(getUserId(), org_index);
        // 报警组织排行
        List<Map<String, Object>> org = hWaringService.getOrgRanking(getUserId());
        // 报警等级排行
        List<Map<String, Object>> level = hWaringService.getLevelRanking(getUserId(), org_index);
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("org", org);
        map.put("level", level);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警趋势分析
     */
    @GetMapping("/getTrend")
    @ResponseBody
    public AjaxResult getTrend(String org_index) {
        // 获取年趋势
        List<Map<String, Object>> year = hWaringService.getYearTrend(getUserId(), org_index);
        // 获取本年内每月数据
        List<Map<String, Object>> month = hWaringService.getMonthTrend(getUserId(), org_index);
        // 获取本年内每季度数据
        List<Map<String, Object>> quarter = hWaringService.getQuarterTrend(getUserId(), org_index);
        // 获取本年内每周数据
        List<Map<String, Object>> week = hWaringService.getWeekTrend(getUserId(), org_index);
        Map<String, Object> map = new HashMap<>();
        map.put("year", year);
        map.put("month", month);
        map.put("quarter", quarter);
        map.put("week", week);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警治理增长率分析
     */
    @GetMapping("/getGrowth")
    @ResponseBody
    public AjaxResult getGrowth(String org_index) {
        // 获取本月报警数据
        int nowNum = hWaringService.getMonthWaring(getUserId(), org_index);
        // 获取上个月报警数据
        int lastNum = hWaringService.getLastMonthWaring(getUserId(), org_index);
        // 月增长率
        double monthGrowthRate;
        BigDecimal monthcha = new BigDecimal(nowNum).subtract(new BigDecimal(lastNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastNum)) == 0) {
            monthGrowthRate = 0.00;
        } else {
            monthGrowthRate = monthcha.divide(new BigDecimal(lastNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 获取本季度报警数据
        int quarterNum = hWaringService.getQuarterWaring(getUserId(), org_index);
        // 获取上个季度报警数据
        int lastQuarterNum = hWaringService.getLastQuarterWaring(getUserId(), org_index);
        // 季度增长率
        double quarteGrowthRate;
        BigDecimal quartecha = new BigDecimal(quarterNum).subtract(new BigDecimal(lastQuarterNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastQuarterNum)) == 0) {
            quarteGrowthRate = 0.00;
        } else {
            quarteGrowthRate = quartecha.divide(new BigDecimal(lastQuarterNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 获取本年报警数据
        int yearNum = hWaringService.getYearWaring(getUserId(), org_index);
        // 获取去年报警数据
        int lastYearNum = hWaringService.getLastYearWaring(getUserId(), org_index);
        // 年增长率
        double yearGrowthRate;
        BigDecimal yearcha = new BigDecimal(yearNum).subtract(new BigDecimal(lastYearNum));
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(lastYearNum)) == 0) {
            yearGrowthRate = 0.00;
        } else {
            yearGrowthRate = yearcha.divide(new BigDecimal(lastYearNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 获取本月处理后的报警数量
        int handleNum = hWaringService.getMonthHandle(getUserId(), org_index);
        // 获取本月的整改率
        double rectification;
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(nowNum)) == 0) {
            rectification = 0.00;
        } else {
            rectification = new BigDecimal(handleNum).divide(new BigDecimal(nowNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 获取本季度处理后的报警数据
        int quarterHandleNum = hWaringService.getQuarterHandle(getUserId(), org_index);
        // 获取本季度的整改率
        double quarterRectification;
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(quarterNum)) == 0) {
            quarterRectification = 0.00;
        } else {
            quarterRectification = new BigDecimal(quarterHandleNum).divide(new BigDecimal(quarterNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        // 获取本年处理后的报警数据
        int yearHandleNum = hWaringService.getYearHandle(getUserId(), org_index);
        // 获取本年的整改率
        double yearRectification;
        if ((BigDecimal.ZERO).compareTo(new BigDecimal(yearNum)) == 0) {
            yearRectification = 0.00;
        } else {
            yearRectification = new BigDecimal(yearHandleNum).divide(new BigDecimal(yearNum), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("monthGrowthRate", monthGrowthRate);
        map.put("quarteGrowthRate", quarteGrowthRate);
        map.put("yearGrowthRate", yearGrowthRate);
        map.put("monthRectification", rectification);
        map.put("quarterRectification", quarterRectification);
        map.put("yearRectification", yearRectification);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警整体分布图 (柱状图)
     */
    @GetMapping("/getColumn")
    @ResponseBody
    public AjaxResult getColumn(String org_index, String type) {
        List<Map<String, Object>> map = hWaringService.getTypeSpread(getUserId(), org_index, type);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警等级分布
     */
    @GetMapping("/getLevelSpread")
    @ResponseBody
    public AjaxResult getLevelSpread(String org_index, String type) {
        List<Map<String, Object>> map = hWaringService.getLevelSpread(getUserId(), org_index, type);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 报警类型分布
     */
    @GetMapping("/getTypeSpread")
    @ResponseBody
    public AjaxResult getTypeSpread(String org_index, String type) {
        List<Map<String, Object>> map = hWaringService.getTypeSpread(getUserId(), org_index, type);
        return new AjaxResult(200, "操作成功", map);
    }

    /**
     * 挂牌公示报警
     */
    @GetMapping("/getHandleData")
    @ResponseBody
    public AjaxResult getHandleData(String org_index) {
        List<Map<String, Object>> handle = hHandleService.getHandleData(getUserId(), org_index);
        return new AjaxResult(200, "操作成功", handle);
    }

    /**
     * 首页组织遍历
     */
    @GetMapping("/getDeptList")
    @PreAuthorize("@ss.hasPermi('getDeptList')")
    public AjaxResult getDeptList() {
        List<SysDept> sysDepts = deptService.getDeptList();
        return new AjaxResult(200, "操作成功", sysDepts);
    }

    /**
     * 监测点统计
     */
    @GetMapping("/getDeviceNum")
    public AjaxResult getDeviceNum() {
        Map<String, Object> device = deviceService.getDeviceNum(getUserId());
        return new AjaxResult(200, "操作成功", device);
    }

    /**
     * 实时报警
     */
    @GetMapping("/getRealAlarm")
    public AjaxResult getRealAlarm() {
        List<Map<String, Object>> alarm = hWaringService.getWaring(getUserId());
        return new AjaxResult(200, "操作成功", alarm);
    }

    /**
     * 获取报警图片
     */
    @GetMapping("/getAlarmPhoto")
    public AjaxResult getAlarmPhoto() {
        List<Map<String, Object>> alarm = hWaringService.getAlarmPhoto(getUserId());
        return new AjaxResult(200, "操作成功", alarm);
    }
}
