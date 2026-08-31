package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.domain.HAlgorithm;
import com.ruoyi.waring.mapper.HAlgorithmMapper;
import com.ruoyi.waring.service.HAlgorithmService;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HAlgorithmServiceImpl implements HAlgorithmService {
    @Autowired
    HAlgorithmMapper hAlgorithmMapper;

    @Override
    public List<HAlgorithm> selectAlgorithmList() {
        List<HAlgorithm> algorithms = hAlgorithmMapper.selectWaringList();
        return algorithms == null ? Collections.emptyList() : algorithms;
    }

    @Override
    public List<String> selectTargetsByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return Collections.emptyList();
        }

        String objectStr = getObjectStrByCode(code);
        if (objectStr == null || objectStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> orderedUniqueTargets = new LinkedHashSet<>();
        for (String item : objectStr.split(",")) {
            if (item == null) {
                continue;
            }
            String target = item.trim();
            if (!target.isEmpty()) {
                orderedUniqueTargets.add(target);
            }
        }

        return orderedUniqueTargets.stream().collect(Collectors.toList());
    }

    @Override
    public String getObjectStrByCode(String code) {
        return hAlgorithmMapper.selectObjectStrByCode(code);
    }

    @Override
    public String getApiUrlByCode(String code) {
        return hAlgorithmMapper.selectApiUrlByCode(code);
    }

    @Override
    public String getNameByCode(String code) {
        return hAlgorithmMapper.selectNameByCode(code);
    }
}
