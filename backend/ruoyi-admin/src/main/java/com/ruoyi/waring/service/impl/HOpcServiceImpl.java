package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.domain.HOpc;
import com.ruoyi.waring.mapper.HOpcMapper;
import com.ruoyi.waring.service.HOpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
public class HOpcServiceImpl implements HOpcService {

    @Autowired
    HOpcMapper hOpcMapper;

    @Override
    public String getStatus(HOpc hopc) {
        return hOpcMapper.getStatus(hopc);
    }
}
