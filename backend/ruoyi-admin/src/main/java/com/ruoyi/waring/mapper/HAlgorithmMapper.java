package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HAlgorithm;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface HAlgorithmMapper {
    List<HAlgorithm> selectWaringList();

    String selectObjectStrByCode(String code);

    String selectApiUrlByCode(String code);

    String selectNameByCode(String code);
}
