package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.domain.HType;
import com.ruoyi.waring.mapper.HTypeMapper;
import com.ruoyi.waring.service.HTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
@Component
public class HTypeServiceImpl implements HTypeService {

    @Autowired
    private HTypeMapper hTypeMapper;

    @Override
    public int insertType(HType type) {
        return hTypeMapper.insertType(type);
    }

    @Override
    public boolean checkTypeUnique(HType type) {
        HType types = hTypeMapper.checkTypeUnique(type);
        return types == null;
    }

    @Override
    public List<HType> selectTypeList(HType type, Long userId) {
        return hTypeMapper.selectTypeList(type);
    }

    @Override
    public int deleteType(Long[] tids) {
        return hTypeMapper.deleteType(tids);
    }

    @Override
    public HType getWaringType(String type, String device) {
        HType types = new HType();
        types.setAlarm_type(type);
        types.setDevice_id(device);
        return hTypeMapper.getWaringType(types);
    }

    @Override
    public List<HType> getTypeWaring() {
        return hTypeMapper.getTypeWaring();
    }

    @Override
    public List<HType> getAlarmTypeFilterOptions() {
        return hTypeMapper.getAlarmTypeFilterOptions();
    }

    @Override
    public List<Map<String, Object>> getDisType() {
        return hTypeMapper.getDisType();
    }
}
