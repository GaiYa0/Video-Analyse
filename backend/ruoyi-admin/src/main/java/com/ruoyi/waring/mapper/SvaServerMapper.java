package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.SvaServer;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface SvaServerMapper {

    SvaServer selectEnabledById(Long id);

    List<SvaServer> selectEnabledList();
}
