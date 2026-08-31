package com.ruoyi.web.controller.algorithm;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.waring.domain.HAlgorithm;
import com.ruoyi.waring.service.HAlgorithmService;

@RestController
@RequestMapping("/algorithm/config")
public class AlgorithmController extends BaseController
{
    @Autowired
    private HAlgorithmService hAlgorithmService;

    @GetMapping("/list")
    public TableDataInfo list(HAlgorithm algorithm)
    {
        List<HAlgorithm> list = hAlgorithmService.selectAlgorithmList();
        return getDataTable(list);
    }

    @GetMapping("/targets/{code}")
    public AjaxResult targets(@PathVariable("code") String code)
    {
        if (StringUtils.isBlank(code))
        {
            return AjaxResult.success(Collections.emptyList());
        }

        List<String> targets = hAlgorithmService.selectTargetsByCode(code);
        return AjaxResult.success(targets);
    }
}
