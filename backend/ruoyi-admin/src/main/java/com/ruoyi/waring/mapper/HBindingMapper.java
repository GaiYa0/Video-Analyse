package com.ruoyi.waring.mapper;

import com.ruoyi.waring.domain.HBinding;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface HBindingMapper {

    String getTeam(String device);

    List<HBinding> getTeamWaring();
}
