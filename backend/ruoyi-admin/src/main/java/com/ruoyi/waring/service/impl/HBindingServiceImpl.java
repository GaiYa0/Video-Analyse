package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.domain.HBinding;
import com.ruoyi.waring.mapper.HBindingMapper;
import com.ruoyi.waring.service.HBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Component
public class HBindingServiceImpl implements HBindingService {

    @Autowired
    HBindingMapper hBindingMapper;

    @Override
    public String getTeam(String device) {
        return hBindingMapper.getTeam(device);
    }

    @Override
    public List<HBinding> getTeamWaring() {
        return hBindingMapper.getTeamWaring();
    }
}
