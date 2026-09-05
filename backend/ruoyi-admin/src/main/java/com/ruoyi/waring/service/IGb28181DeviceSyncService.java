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

    /**
     * 若 ZLM 尚无该 rtp 流，触发 on_stream_not_found（WVP 自动 INVITE），必要时再调 WVP 点播。
     * @return 等待结束后流是否已在 ZLM
     */
    boolean ensureRtpReady(String streamId);

    /**
     * 预览用：后台触发 INVITE，不阻塞 HTTP。流已在则立即返回。
     */
    void warmRtp(String streamId);
}
