package com.ruoyi.waring.service;

import java.util.Map;

/**
 * 国标设备同步。拉 ZLM REST/Hook 归同学 A；本接口只冻契约，未接 ZLM 时 ready=false。
 */
public interface IGb28181DeviceSyncService {

    /**
     * 从 ZLM 同步国标设备到 h_device。
     * 返回 inserted / updated / failed / message / ready。
     */
    Map<String, Object> syncFromZlm();
}
