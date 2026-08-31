package com.ruoyi.waring.mapper;


import com.ruoyi.waring.domain.HOpc;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface HOpcMapper {

    String getStatus(HOpc hopc);
}
