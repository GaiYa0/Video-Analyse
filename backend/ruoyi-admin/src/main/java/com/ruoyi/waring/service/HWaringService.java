package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.Details;
import com.ruoyi.waring.domain.HHandle;
import com.ruoyi.waring.domain.HWaring;

import java.util.List;
import java.util.Map;

public interface HWaringService {

    int insertWaring(HWaring waring);

    int updateSvaLifecycleWaring(HWaring waring);

    int updateSvaMediaFields(HWaring waring);

    HWaring selectLatestSvaRuleWaringForInterval(String controlCode, String behaviorType, String ruleId,
                                                 String regionId, String lineId, String crossingDirection,
                                                 String alarmTime, int alarmIntervalSec);

    List<HWaring> selectWaringList(HWaring waring, Long userId, int type);

    int handle(HHandle handle);

    int getMonthWaring(Long userId, String org_index);

    int getLastYearMonthWaring(Long userId, String org_index);

    int getLastMonthWaring(Long userId, String org_index);

    int getYearWaring(Long userId, String org_index);

    int getMonthMajorWaring(Long userId, String org_index);

    int getLastYearMajorMonthWaring(Long userId, String org_index);

    int getLastMonthMajorWaring(Long userId, String org_index);

    int getYearMajorWaring(Long userId, String org_index);

    int getMonthWaringByhandle(Long userId, String org_index);

    int getMonthHandle(Long userId, String org_index);

    int getMonthOverdueWaring(Long userId, String org_index);

    int getLastYearMonthOverdueWaring(Long userId, String org_index);

    int getLastMonthOverdueWaring(Long userId, String org_index);

    int getYearOverdueWaring(Long userId, String org_index);

    List<Map<String, Object>> getTypeRanking(Long userId, String org_index);

    List<Map<String, Object>> getOrgRanking(Long userId);

    List<Map<String, Object>> getLevelRanking(Long userId, String org_index);

    List<Map<String, Object>> getYearTrend(Long userId, String org_index);

    List<Map<String, Object>> getMonthTrend(Long userId, String org_index);

    List<Map<String, Object>> getQuarterTrend(Long userId, String org_index);

    List<Map<String, Object>> getWeekTrend(Long userId, String org_index);

    int getQuarterWaring(Long userId, String org_index);

    int getLastQuarterWaring(Long userId, String org_index);

    int getLastYearWaring(Long userId, String org_index);

    int getQuarterHandle(Long userId, String org_index);

    int getYearHandle(Long userId, String org_index);

    List<Map<String, Object>> getLevelSpread(Long userId, String org_index, String type);

    List<Map<String, Object>> getTypeSpread(Long userId, String org_index, String type);

    Details getOne(int w_id);

    List<HWaring> getHistoryWaring(String place);

    int getNoWaring(String device, String alarm_time);

    int getNoCarWaring(String device, String alarm_time);

    List<Map<String, Object>> getWaring(Long userId);

    List<Map<String, Object>> getAlarmPhoto(Long userId);

    List<HWaring> selectReconditionList(HWaring waring, Long userId);

    List<HWaring> selectWubaoList(HWaring waring, Long userId);

    int getChuNum(Long userId, String org_index);

    HWaring selectWaringById(String id);

    String getId(int id);


}
