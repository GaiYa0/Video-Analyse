package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.domain.HPerson;
import com.ruoyi.waring.mapper.HPersonMapper;
import com.ruoyi.waring.service.HPersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
public class HPersonServiceImpl implements HPersonService {

    @Autowired
    HPersonMapper hPersonMapper;

    @Override
    public int insertPerson(HPerson person) {
        return hPersonMapper.insertPerson(person);
    }
}
