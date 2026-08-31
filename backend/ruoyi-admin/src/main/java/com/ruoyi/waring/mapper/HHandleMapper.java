package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HHandle;
import com.ruoyi.waring.domain.HWaring;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface HHandleMapper {
    int insertHandle(HHandle handle);

    List<Map<String, Object>> getHandleData(HHandle handle);

    List<Map<String, Object>> getHandleDataByOrgIndex(HHandle handle);

    List<HWaring> getTestData(HWaring waring);

}
