package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HType;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface HTypeMapper {
    int insertType(HType type);

    HType checkTypeUnique(HType type);

    List<HType> selectTypeList(HType type);

    int deleteType(Long[] ids);

    HType getWaringType(HType type);

    List<HType> getTypeWaring();

    List<HType> getAlarmTypeFilterOptions();

    List<Map<String, Object>> getDisType();
}
