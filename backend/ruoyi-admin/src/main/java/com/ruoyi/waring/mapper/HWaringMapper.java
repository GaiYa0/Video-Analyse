package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.Details;
import com.ruoyi.waring.domain.HWaring;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface HWaringMapper {

    int insertWaring(HWaring waring);

    List<HWaring> selectWaringList(HWaring waring);

    List<HWaring> selectWaringListByOrgIndex(HWaring waring);

    HWaring selectWaringById(String id);

    HWaring selectLatestSvaRuleWaringForInterval(@Param("controlCode") String controlCode,
                                                 @Param("behaviorType") String behaviorType,
                                                 @Param("ruleId") String ruleId,
                                                 @Param("regionId") String regionId,
                                                 @Param("lineId") String lineId,
                                                 @Param("crossingDirection") String crossingDirection,
                                                 @Param("alarmTime") String alarmTime,
                                                 @Param("alarmIntervalSec") int alarmIntervalSec);

    HWaring selectWaringByWId(@Param("wId") Integer wId);

    int updateSvaLifecycleWaring(HWaring waring);

    int updateSvaMediaFields(HWaring waring);

    int updateHandle(String id);

    int updateHandleForTitlle(String id);

    // 获取本月报警数
    int getBetweenWaring(HWaring waring);

    int getBetweenWaringOfOrg(HWaring waring);

    // 获取去年本月报警数
    int getBetweenLastYearMonthWaring(HWaring waring);

    int getBetweenLastYearMonthWaringOfOrg(HWaring waring);

    // 获取上个月的报警数
    int getBetweenLastMonthWaring(HWaring waring);

    int getBetweenLastMonthWaringOfOrg(HWaring waring);

    // 获取本年的报警数
    int getBetweenYearWaring(HWaring waring);

    int getBetweenYearWaringOfOrg(HWaring waring);


    int getBetweenWaringByLevel(HWaring waring);

    int getBetweenWaringByLevelOfOrg(HWaring waring);

    int getBetweenWaringByHandle(HWaring waring);

    int getBetweenWaringByHandleOfOrg(HWaring waring);

    int getBetweenWaringByOverdue(HWaring waring);

    int getBetweenWaringByOverdueOfOrg(HWaring waring);

    List<Map<String, Object>> getLevelSpread(HWaring waring);

    List<Map<String, Object>> getLevelSpreadByOrgIndex(HWaring waring);

    List<Map<String, Object>> getTypeSpread(HWaring waring);

    List<Map<String, Object>> getTypeSpreadByOrgIndex(HWaring waring);

    List<Map<String, Object>> getYearTrend(HWaring waring);

    List<Map<String, Object>> getYearTrendByOrgIndex(HWaring waring);

    List<Map<String, Object>> getMonthTrend(HWaring waring);

    List<Map<String, Object>> getMonthTrendByOrgIndex(HWaring waring);

    List<Map<String, Object>> getQuarterTrend(HWaring waring);

    List<Map<String, Object>> getQuarterTrendByOrgIndex(HWaring waring);

    List<Map<String, Object>> getWeekTrend(HWaring waring);

    List<Map<String, Object>> getWeekTrendByOrgIndex(HWaring waring);

    List<Map<String, Object>> getTypeRanking(HWaring waring);

    List<Map<String, Object>> getTypeRankingByOrgIndex(HWaring waring);

    List<Map<String, Object>> getOrgRanking(HWaring waring);

    List<Map<String, Object>> getLevelRanking(HWaring waring);

    List<Map<String, Object>> getLevelRankingByOrgIndex(HWaring waring);

    Details getOne(int w_id);

    List<HWaring> getHistoryWaring(String place);

    int getNoWaring(HWaring waring);

    int getNoCarWaring(HWaring waring);

    List<Map<String, Object>> getWaring();

    List<Map<String, Object>> getWaringByOrg(HWaring waring);

    List<Map<String, Object>> getAlarmPhoto();

    List<Map<String, Object>> getAlarmPhotoByOrg(HWaring waring);


    List<HWaring> selectReconditionList(HWaring waring);

    List<HWaring> selectReconditionListByOrgIndex(HWaring waring);

    List<HWaring> selectWubaoList(HWaring waring);

    List<HWaring> selectWubaoListByOrgIndex(HWaring waring);

    String getId(int id);

    int getChuNumByOrgIndex(HWaring waring);

    int getChuNum(HWaring waring);

    int getWeiByOrgIndex(HWaring waring);

    int getWuByOrgIndex(HWaring waring);

    int getZhengByOrgIndex(HWaring waring);

    int getWei(HWaring waring);

    int getWu(HWaring waring);

    int getZheng(HWaring waring);
}
