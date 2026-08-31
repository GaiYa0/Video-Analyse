package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HPerson;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface HPersonMapper {

    int insertPerson(HPerson person);
}
