package com.ruoyi.waring.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.waring.domain.HDist;
import com.ruoyi.waring.domain.HPerson;
import com.ruoyi.waring.mapper.HDistMapper;
import com.ruoyi.waring.service.HDistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Component
public class HDistServiceImpl implements HDistService {

    @Autowired
    HDistMapper hDistMapper;

    @Override
    public int insertDist(HDist hDist) {
        if ("水泉矿-副井口检身房考勤点".equals(hDist.getSite_name())) {
            hDist.setType(1);
        } else {
            hDist.setType(2);
        }
        return hDistMapper.insertDist(hDist);
    }

    @Override
    public List<HDist> selectDistList(HPerson person) {
        long start = 0;
        long end = 0;
        if (person.getIs_handle() == null) {
            // 从早班到晚班的数据
            start = person.getBegin();
            end = person.getEnd();
        } else if (person.getIs_handle().equals("0")) {
            // 早班 8点到14点 1小时为 3600000 毫秒
            start = person.getBegin() + 3600000 * 8;
            end = person.getBegin() + 3600000 * 16;
        } else if (person.getIs_handle().equals("1")) {
            // 中班 14点到
            start = person.getBegin() + 3600000 * 16;
            end = person.getBegin() + 3600000 * 24;
        } else if (person.getIs_handle().equals("2")) {
            // 晚班
            start = person.getBegin() + 3600000 * 24;
            end = person.getBegin() + 3600000 * 32;
        }
        HDist hDist = new HDist();
        if (person.getType() == 1) {
            // 入井识别
            hDist.setType(1);
        } else if (person.getType() == 2) {
            // 巡检记录
            hDist.setType(2);
        }

        hDist.setEnd(end / 1000);
        hDist.setBegin(start / 1000);
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        List<HDist> list = hDistMapper.selectDistList(hDist);
        return list;
    }

    @Override
    public int getId(String id) {
        return hDistMapper.getId(id);
    }

    @Override
    public List<Map<Object, Object>> getChao() {
        return hDistMapper.getChao();
    }
}
