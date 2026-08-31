package com.ruoyi.waring.service;

import com.ruoyi.waring.domain.HBinding;

import java.util.List;

public interface HBindingService {

    String getTeam(String device);

    List<HBinding> getTeamWaring();
}
