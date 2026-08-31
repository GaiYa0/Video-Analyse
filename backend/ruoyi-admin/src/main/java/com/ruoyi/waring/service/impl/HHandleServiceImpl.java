package com.ruoyi.waring.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.waring.domain.HHandle;
import com.ruoyi.waring.domain.HWaring;
import com.ruoyi.waring.mapper.HHandleMapper;
import com.ruoyi.waring.service.HHandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Component
public class HHandleServiceImpl implements HHandleService {

    @Autowired
    private HHandleMapper hHandleMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Override
    public List<Map<String, Object>> getHandleData(Long userId, String org_index) {
        List<Map<String, Object>> hHandles;
        HHandle handle = new HHandle();
        if (org_index != null && !org_index.equals("10")) {
            handle.getParams().put("org_index", org_index);
            hHandles = hHandleMapper.getHandleDataByOrgIndex(handle);
        } else if (org_index != null) {
            hHandles = hHandleMapper.getHandleData(handle);
        } else {
            SysUser user = userMapper.selectUserById(userId);
            SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
            if (!com.ruoyi.common.utils.SecurityUtils.isAdmin(userId) && !dept.getOrgIndex().equals("10")) {
                handle.getParams().put("org_index", dept.getOrgIndex());
                hHandles = hHandleMapper.getHandleDataByOrgIndex(handle);
            } else {
                hHandles = hHandleMapper.getHandleData(handle);
            }
        }
        return hHandles;
    }

    @Override
    public List<HWaring> getTestData(HWaring waring, Long userId) {
        List<HWaring> warings;
        SysUser user = userMapper.selectUserById(userId);
        SysDept dept = sysDeptMapper.selectDeptById(user.getDeptId());
        waring.setOrg_index(dept.getOrgIndex());
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());

        warings = hHandleMapper.getTestData(waring);
        return warings;
    }

    public int insertHandle(HHandle handle) {
        return hHandleMapper.insertHandle(handle);
    }
}
