package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HDist;
import com.ruoyi.waring.domain.HPerson;

import java.util.List;
import java.util.Map;

public interface HDistService {
    int insertDist(HDist hDist);

    List<HDist> selectDistList(HPerson hPerson);

    int getId(String id);

    List<Map<Object, Object>> getChao();
}
