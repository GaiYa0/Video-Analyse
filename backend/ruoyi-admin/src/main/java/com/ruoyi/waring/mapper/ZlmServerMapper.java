package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.ZlmServer;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ZlmServerMapper {

    ZlmServer selectEnabledById(Long id);

    List<ZlmServer> selectEnabledList();
}
