package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HHandle;
import com.ruoyi.waring.domain.HWaring;

import java.util.List;
import java.util.Map;

public interface HHandleService {

    List<Map<String, Object>> getHandleData(Long userId, String org_index);

    List<HWaring> getTestData(HWaring waring, Long userId);

    int insertHandle(HHandle handle);
}
