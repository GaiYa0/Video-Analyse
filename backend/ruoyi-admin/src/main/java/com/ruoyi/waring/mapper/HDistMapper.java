package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HDist;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface HDistMapper {

    int insertDist(HDist hDist);

    List<HDist> selectDistList(HDist hDist);

    int getId(String id);

    List<Map<Object, Object>> getChao();
}
