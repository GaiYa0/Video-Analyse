package com.ruoyi.waring.controller;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.waring.domain.HWaring;
import com.ruoyi.waring.service.HHandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/waring/handle")
public class HHandleContrller extends BaseController {

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private HHandleService hHandleService;

    @GetMapping("/getTestData")
    public TableDataInfo list(HWaring waring) {
        List<HWaring> list = hHandleService.getTestData(waring, getUserId());
        Object token = redisTemplate.boundValueOps("token").get();
        return getDataTable(list);
    }
}
