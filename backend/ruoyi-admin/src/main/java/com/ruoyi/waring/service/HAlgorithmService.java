package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HAlgorithm;

import java.util.List;

public interface HAlgorithmService {
    List<HAlgorithm> selectAlgorithmList();

    List<String> selectTargetsByCode(String code);

    String getObjectStrByCode(String code);

    String getApiUrlByCode(String code);

    String getNameByCode(String code);
}
