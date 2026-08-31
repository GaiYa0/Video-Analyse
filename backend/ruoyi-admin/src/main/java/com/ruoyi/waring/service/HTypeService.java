package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HType;

import java.util.List;
import java.util.Map;

public interface HTypeService {
    int insertType(HType type);

    boolean checkTypeUnique(HType type);

    List<HType> selectTypeList(HType type, Long userId);

    int deleteType(Long[] tids);

    HType getWaringType(String type, String device);

    List<HType> getTypeWaring();

    List<HType> getAlarmTypeFilterOptions();

    List<Map<String, Object>> getDisType();
}
