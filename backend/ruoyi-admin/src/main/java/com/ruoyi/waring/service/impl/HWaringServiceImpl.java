package com.ruoyi.waring.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.waring.Util.TimeUtil;
import com.ruoyi.waring.domain.Details;
import com.ruoyi.waring.domain.HHandle;
import com.ruoyi.waring.domain.HWaring;
import com.ruoyi.waring.mapper.HHandleMapper;
import com.ruoyi.waring.mapper.HWaringMapper;
import com.ruoyi.waring.service.IAiReviewService;
import com.ruoyi.waring.service.HWaringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Component
public class HWaringServiceImpl implements HWaringService {

    @Autowired
    private HWaringMapper hWaringMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private HHandleMapper hHandleMapper;

    @Autowired
    private IAiReviewService aiReviewService;

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public int insertWaring(HWaring waring) {
        int rows = hWaringMapper.insertWaring(waring);
        if (rows > 0) {
            aiReviewService.createImageReviewTask(waring);
        }
        return rows;
    }

    @Override
    public int updateSvaLifecycleWaring(HWaring waring) {
        return hWaringMapper.updateSvaLifecycleWaring(waring);
    }

    @Override
    public int updateSvaMediaFields(HWaring waring) {
        return hWaringMapper.updateSvaMediaFields(waring);
    }

    @Override
    public HWaring selectLatestSvaRuleWaringForInterval(String controlCode, String behaviorType, String ruleId,
                                                        String regionId, String lineId, String crossingDirection,
                                                        String alarmTime, int alarmIntervalSec) {
        if (alarmIntervalSec <= 0) {
            return null;
        }
        return hWaringMapper.selectLatestSvaRuleWaringForInterval(controlCode, behaviorType, ruleId, regionId,
            lineId, crossingDirection, alarmTime, alarmIntervalSec);
    }

    @Override
    public List<HWaring> selectWaringList(HWaring waring, Long userId, int type) {
        List<HWaring> warings;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        waring.setOrg_index(dept.getOrgIndex());
        if (type == 0) {
            PageDomain pageDomain = TableSupport.getPageDomain();
            PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        }
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectWaringList(waring);
        } else if (dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectWaringList(waring);
        } else {
            warings = hWaringMapper.selectWaringListByOrgIndex(waring);
        }
        return warings;
    }

    @Override
    public int handle(HHandle handle) {
        int updateNum;
        // 获取id 华三平台给的
        String id = hWaringMapper.getId(handle.getW_id());
        // 先修改
        if (handle.getH_title().equals("误报")) {
            updateNum = hWaringMapper.updateHandleForTitlle(id);
        } else if (handle.getH_remark().equals("测试")) {
            updateNum = hWaringMapper.updateHandleForTitlle(id);
        } else {
            updateNum = hWaringMapper.updateHandle(id);
        }
        return updateNum;
//        if (updateNum == 1) {
//            HWaring data = hWaringMapper.selectWaringById(id);
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//            handle.setH_create_time(sdf.format(new Date()));
//            handle.setH_org_index(data.getOrg_index());
//            handle.setH_org_name(dept.getDeptName() + "--" + user.getNickName());
//            Date date = sdf.parse(data.getAlarm_time());
//            handle.setH_time(sdf.format(TimeUtil.getNextDay(date, 7)));
//            return hHandleMapper.insertHandle(handle);
//        }
    }

    // 获取本月报警数据
    @Override
    public int getMonthWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
        long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaring(waring);
            }
        }
        return num;
    }

    // 获取去年本月报警数据
    @Override
    public int getLastYearMonthWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(12).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(12).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenLastYearMonthWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenLastYearMonthWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenLastYearMonthWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenLastYearMonthWaring(waring);
            }
        }
        return num;
    }

    // 获取上个月报警数据
    @Override
    public int getLastMonthWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(1).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(1).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenLastMonthWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenLastMonthWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenLastMonthWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenLastMonthWaring(waring);
            }
        }
        return num;
    }

    // 获取本年报警数据
    @Override
    public int getYearWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenYearWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenYearWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenYearWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenYearWaring(waring);
            }
        }
        return num;
    }

    // 获取本月重大报警数据
    @Override
    public int getMonthMajorWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
        long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setAlarm_level("5");
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByLevel(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByLevel(waring);
            }
        }
        return num;
    }

    // 获取去年本月重大报警数据
    @Override
    public int getLastYearMajorMonthWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(12).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(12).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setAlarm_level("5");
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByLevel(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByLevel(waring);
            }
        }
        return num;
    }

    // 获取上个月重大报警数据
    @Override
    public int getLastMonthMajorWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(1).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(1).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setAlarm_level("5");
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByLevel(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByLevel(waring);
            }
        }
        return num;
    }

    // 获取本年重大报警数据
    @Override
    public int getYearMajorWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setAlarm_level("5");
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByLevel(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByLevelOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByLevel(waring);
            }
        }
        return num;
    }

    // 本月逾期数量
    @Override
    public int getMonthOverdueWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
        long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.getParams().put("beOverdue", 7);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByOverdue(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByOverdue(waring);
            }
        }
        return num;
    }

    // 获取去年本月逾期
    @Override
    public int getLastYearMonthOverdueWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(12).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(12).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.getParams().put("beOverdue", 7);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByOverdue(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByOverdue(waring);
            }
        }
        return num;
    }

    // 获取上个月逾期
    @Override
    public int getLastMonthOverdueWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastMonth(1).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastMonth(1).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.getParams().put("beOverdue", 7);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByOverdue(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByOverdue(waring);
            }
        }
        return num;
    }

    // 获取本年逾期
    @Override
    public int getYearOverdueWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.getParams().put("beOverdue", 7);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByOverdue(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByOverdueOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByOverdue(waring);
            }
        }
        return num;
    }

    @Override
    public int getMonthWaringByhandle(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
        long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaring(waring);
            }
        }
        return num;
    }

    // 获取本月处理后的报警数量
    @Override
    public int getMonthHandle(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
        long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setIs_handle(1);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByHandle(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByHandle(waring);
            }
        }
        return num;
    }

    // 报警类型排行
    @Override
    public List<Map<String, Object>> getTypeRanking(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getTypeRankingByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getTypeRanking(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getTypeRankingByOrgIndex(waring);
            } else {
                return hWaringMapper.getTypeRanking(waring);
            }
        }
    }

    // 报警组织排行
    @Override
    public List<Map<String, Object>> getOrgRanking(Long userId) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        return hWaringMapper.getOrgRanking(waring);
    }

    // 报警等级排行
    @Override
    public List<Map<String, Object>> getLevelRanking(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getLevelRankingByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getLevelRanking(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getLevelRankingByOrgIndex(waring);
            } else {
                return hWaringMapper.getLevelRanking(waring);
            }
        }
    }

    @Override
    public int getChuNum(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setIs_handle(1);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getChuNumByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getChuNum(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getChuNumByOrgIndex(waring);
            } else {
                return hWaringMapper.getChuNum(waring);
            }
        }
    }


    // 获取年趋势
    @Override
    public List<Map<String, Object>> getYearTrend(Long userId, String org_index) {
        HWaring waring = new HWaring();
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getYearTrendByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getYearTrend(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getYearTrendByOrgIndex(waring);
            } else {
                return hWaringMapper.getYearTrend(waring);
            }
        }
    }

    // 获取本年内每月数据
    @Override
    public List<Map<String, Object>> getMonthTrend(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getMonthTrendByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getMonthTrend(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getMonthTrendByOrgIndex(waring);
            } else {
                return hWaringMapper.getMonthTrend(waring);
            }
        }
    }

    // 获取本年内每季度数据
    @Override
    public List<Map<String, Object>> getQuarterTrend(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getQuarterTrendByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getQuarterTrend(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getQuarterTrendByOrgIndex(waring);
            } else {
                return hWaringMapper.getQuarterTrend(waring);
            }
        }
    }

    // 获取本年内每周数据
    @Override
    public List<Map<String, Object>> getWeekTrend(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getWeekTrendByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getWeekTrend(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getWeekTrendByOrgIndex(waring);
            } else {
                return hWaringMapper.getWeekTrend(waring);
            }
        }

    }

    // 获取本季度报警数据
    @Override
    public int getQuarterWaring(Long userId, String org_index) {
        long begin = TimeUtil.getCurrentQuarterStartTime().getTime() / 1000;
        long end = TimeUtil.getCurrentQuarterEndTime().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaring(waring);
            }
        }
        return num;
    }

    // 获取上个季度报警数据
    @Override
    public int getLastQuarterWaring(Long userId, String org_index) {
        long begin = TimeUtil.getSCurrentQuarterStartTime().getTime() / 1000;
        long end = TimeUtil.getSCurrentQuarterEndTime().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaring(waring);
            }
        }
        return num;
    }

    // 获取去年全年报警数据
    @Override
    public int getLastYearWaring(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfLastYear(1).getTime() / 1000;
        long end = TimeUtil.getEndDayOfLastYear(1).getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaring(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaring(waring);
            }
        }
        return num;
    }

    // 获取本季度处理后数据
    @Override
    public int getQuarterHandle(Long userId, String org_index) {
        long begin = TimeUtil.getCurrentQuarterStartTime().getTime() / 1000;
        long end = TimeUtil.getCurrentQuarterEndTime().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setIs_handle(1);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByHandle(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByHandle(waring);
            }
        }
        return num;
    }

    // 获取本年处理后的报警数据
    @Override
    public int getYearHandle(Long userId, String org_index) {
        long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
        long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
        HWaring waring = new HWaring();
        waring.setBegin(begin);
        waring.setEnd(end);
        waring.setIs_handle(1);
        int num;
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
        } else if (org_index != null) {
            num = hWaringMapper.getBetweenWaringByHandle(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                num = hWaringMapper.getBetweenWaringByHandleOfOrg(waring);
            } else {
                num = hWaringMapper.getBetweenWaringByHandle(waring);
            }
        }
        return num;
    }

    // 报警等级分布
    @Override
    public List<Map<String, Object>> getLevelSpread(Long userId, String org_index, String type) {
        HWaring waring = new HWaring();
        if (type == null) {
            type = "2";
        }
        // 1为周 2为月 3为季度 4为年
        switch (type) {
            case "1": {
                // 本周开始结束时间
                long begin = TimeUtil.getBeginDayOfWeek().getTime() / 1000;
                long end = TimeUtil.getEndDayOfWeek().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            case "3": {
                // 本季度开始结束时间
                long begin = TimeUtil.getCurrentQuarterStartTime().getTime() / 1000;
                long end = TimeUtil.getCurrentQuarterEndTime().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            case "4": {
                // 本年开始结束时间
                long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
                long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            default: {
                // 本月开始结束时间
                long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
                long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
        }
        Map<String, Object> mapWei = new HashMap<>();
        Map<String, Object> mapWu = new HashMap<>();
        Map<String, Object> mapZheng = new HashMap<>();
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            // 获取未处理的报警
            int wei = hWaringMapper.getWeiByOrgIndex(waring);
            // 获取误报的报警
            int wu = hWaringMapper.getWuByOrgIndex(waring);
            // 获取确定的报警
            int zheng = hWaringMapper.getZhengByOrgIndex(waring);
            mapWei.put("num", wei);
            mapWei.put("is_handle", "未处理");
            mapWu.put("num", wu);
            mapWu.put("is_handle", "误报");
            mapZheng.put("num", zheng);
            mapZheng.put("is_handle", "已处理");
            List<Map<String, Object>> map = new ArrayList<Map<String, Object>>() {{
                add(mapWei);
                add(mapWu);
                add(mapZheng);
            }};
            return map;
        } else if (org_index != null) {
            int wei = hWaringMapper.getWei(waring);
            // 获取误报的报警
            int wu = hWaringMapper.getWu(waring);
            // 获取确定的报警
            int zheng = hWaringMapper.getZheng(waring);
            mapWei.put("num", wei);
            mapWei.put("is_handle", "未处理");
            mapWu.put("num", wu);
            mapWu.put("is_handle", "误报");
            mapZheng.put("num", zheng);
            mapZheng.put("is_handle", "已处理");
            List<Map<String, Object>> map = new ArrayList<Map<String, Object>>() {{
                add(mapWei);
                add(mapWu);
                add(mapZheng);
            }};
            return map;
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                int wei = hWaringMapper.getWeiByOrgIndex(waring);
                // 获取误报的报警
                int wu = hWaringMapper.getWuByOrgIndex(waring);
                // 获取确定的报警
                int zheng = hWaringMapper.getZhengByOrgIndex(waring);
                mapWei.put("num", wei);
                mapWei.put("is_handle", "未处理");
                mapWu.put("num", wu);
                mapWu.put("is_handle", "误报");
                mapZheng.put("num", zheng);
                mapZheng.put("is_handle", "已处理");
                List<Map<String, Object>> map = new ArrayList<Map<String, Object>>() {{
                    add(mapWei);
                    add(mapWu);
                    add(mapZheng);
                }};
                return map;
            } else {
                int wei = hWaringMapper.getWei(waring);
                // 获取误报的报警
                int wu = hWaringMapper.getWu(waring);
                // 获取确定的报警
                int zheng = hWaringMapper.getZheng(waring);
                mapWei.put("num", wei);
                mapWei.put("is_handle", "未处理");
                mapWu.put("num", wu);
                mapWu.put("is_handle", "误报");
                mapZheng.put("num", zheng);
                mapZheng.put("is_handle", "已处理");
                List<Map<String, Object>> map = new ArrayList<Map<String, Object>>() {{
                    add(mapWei);
                    add(mapWu);
                    add(mapZheng);
                }};
                return map;
            }
        }
    }

    // 报警类型分布
    @Override
    public List<Map<String, Object>> getTypeSpread(Long userId, String org_index, String type) {
        HWaring waring = new HWaring();
        if (type == null) {
            type = "2";
        }
        // 1为周 2为月 3为季度 4为年
        switch (type) {
            case "1": {
                // 本周开始结束时间
                long begin = TimeUtil.getBeginDayOfWeek().getTime() / 1000;
                long end = TimeUtil.getEndDayOfWeek().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            case "3": {
                // 本季度开始结束时间
                long begin = TimeUtil.getCurrentQuarterStartTime().getTime() / 1000;
                long end = TimeUtil.getCurrentQuarterEndTime().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            case "4": {
                // 本年开始结束时间
                long begin = TimeUtil.getBeginDayOfYear().getTime() / 1000;
                long end = TimeUtil.getEndDayOfYear().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
            default: {
                // 本月开始结束时间
                long begin = TimeUtil.getBeginDayOfMonth().getTime() / 1000;
                long end = TimeUtil.getEndDayOfMonth().getTime() / 1000;
                waring.setBegin(begin);
                waring.setEnd(end);
                break;
            }
        }
        if (org_index != null && !org_index.equals("10")) {
            waring.setOrg_index(org_index);
            return hWaringMapper.getTypeSpreadByOrgIndex(waring);
        } else if (org_index != null) {
            return hWaringMapper.getTypeSpread(waring);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                waring.setOrg_index(dept.getOrgIndex());
                return hWaringMapper.getTypeSpreadByOrgIndex(waring);
            } else {
                return hWaringMapper.getTypeSpread(waring);
            }
        }
    }

    @Override
    public Details getOne(int w_id) {
        return hWaringMapper.getOne(w_id);
    }

    @Override
    public List<HWaring> getHistoryWaring(String place) {
        return hWaringMapper.getHistoryWaring(place);
    }

    @Override
    public int getNoWaring(String device, String alarm_time) {
        HWaring hWaring = new HWaring();
        hWaring.setDevice_id(device);
        hWaring.setAlarm_time(alarm_time);
        return hWaringMapper.getNoWaring(hWaring);
    }

    @Override
    public int getNoCarWaring(String device, String alarm_time) {
        HWaring hWaring = new HWaring();
        hWaring.setDevice_id(device);
        hWaring.setAlarm_time(alarm_time);
        return hWaringMapper.getNoCarWaring(hWaring);
    }

    @Override
    public List<Map<String, Object>> getWaring(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) || dept.getOrgIndex().equals("10")) {
            return hWaringMapper.getWaring();
        } else {
            HWaring waring = new HWaring();
            waring.setOrg_index(dept.getOrgIndex());
            return hWaringMapper.getWaringByOrg(waring);
        }
    }

    @Override
    public List<Map<String, Object>> getAlarmPhoto(Long userId) {
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) || dept.getOrgIndex().equals("10")) {
            return hWaringMapper.getAlarmPhoto();
        } else {
            HWaring waring = new HWaring();
            waring.setOrg_index(dept.getOrgIndex());
            return hWaringMapper.getAlarmPhotoByOrg(waring);
        }
    }

    @Override
    public List<HWaring> selectReconditionList(HWaring waring, Long userId) {
        List<HWaring> warings;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        waring.setOrg_index(dept.getOrgIndex());
        waring.setAlarm_type("99158eabc762e2f75fcc325d2055343e");
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectReconditionList(waring);
        } else if (dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectReconditionList(waring);
        } else {
            warings = hWaringMapper.selectReconditionListByOrgIndex(waring);
        }
        return warings;
    }

    @Override
    public List<HWaring> selectWubaoList(HWaring waring, Long userId) {
        List<HWaring> warings;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        waring.setOrg_index(dept.getOrgIndex());
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        if (com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectWubaoList(waring);
        } else if (dept.getOrgIndex().equals("10")) {
            warings = hWaringMapper.selectWubaoList(waring);
        } else {
            warings = hWaringMapper.selectWubaoListByOrgIndex(waring);
        }
        return warings;
    }

    public HWaring selectWaringById(String id) {
        return hWaringMapper.selectWaringById(id);
    }

    public String getId(int id) {
        return hWaringMapper.getId(id);
    }


}
