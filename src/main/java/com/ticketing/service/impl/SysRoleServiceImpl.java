package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketing.dao.SysRoleDao;
import com.ticketing.dao.SysUserRoleDao;
import com.ticketing.entity.SysRole;
import com.ticketing.entity.SysUserRole;
import com.ticketing.service.SysRoleService;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SysRoleServiceImpl extends ServiceImpl<SysRoleDao, SysRole> implements SysRoleService {

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Override
    public ResponseVo getAllRoles() {
        List<SysRole> roles = list();
        return ResponseVo.success(roles);
    }

    @Override
    public ResponseVo getRoleById(Long id) {
        SysRole role = getById(id);
        return ResponseVo.success(role);
    }

    @Override
    public ResponseVo saveRole(SysRole role) {
        save(role);
        return ResponseVo.success(role);
    }

    @Override
    public ResponseVo updateRole(SysRole role) {
        updateById(role);
        return ResponseVo.success(role);
    }

    @Override
    public ResponseVo deleteRole(Long id) {
        removeById(id);
        return ResponseVo.success(null);
    }

    @Override
    public ResponseVo getRolesByUserId(Long userId) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        List<SysUserRole> userRoles = sysUserRoleDao.selectList(wrapper);
        
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        
        if (roleIds.isEmpty()) {
            return ResponseVo.success(new java.util.ArrayList<>());
        }
        
        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(SysRole::getId, roleIds).eq(SysRole::getStatus, 1);
        List<SysRole> roles = list(roleWrapper);
        return ResponseVo.success(roles);
    }
}
