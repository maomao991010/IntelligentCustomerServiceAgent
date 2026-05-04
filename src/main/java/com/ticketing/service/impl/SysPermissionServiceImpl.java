package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticketing.dao.SysPermissionDao;
import com.ticketing.dao.SysRolePermissionDao;
import com.ticketing.entity.SysPermission;
import com.ticketing.entity.SysRolePermission;
import com.ticketing.service.SysPermissionService;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionDao, SysPermission> implements SysPermissionService {

    @Autowired
    private SysRolePermissionDao sysRolePermissionDao;

    @Override
    public ResponseVo getAllPermissions() {
        List<SysPermission> permissions = list();
        return ResponseVo.success(permissions);
    }

    @Override
    public ResponseVo getPermissionsByModule(String module) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPermission::getModule, module);
        List<SysPermission> permissions = list(wrapper);
        return ResponseVo.success(permissions);
    }

    @Override
    public ResponseVo getPermissionById(Long id) {
        SysPermission permission = getById(id);
        return ResponseVo.success(permission);
    }

    @Override
    public ResponseVo savePermission(SysPermission permission) {
        save(permission);
        return ResponseVo.success(permission);
    }

    @Override
    public ResponseVo updatePermission(SysPermission permission) {
        updateById(permission);
        return ResponseVo.success(permission);
    }

    @Override
    public ResponseVo deletePermission(Long id) {
        removeById(id);
        return ResponseVo.success(null);
    }

    @Override
    public ResponseVo getPermissionsByRoleId(Long roleId) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, roleId);
        List<SysRolePermission> rolePermissions = sysRolePermissionDao.selectList(wrapper);
        
        List<Long> permissionIds = rolePermissions.stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        
        if (permissionIds.isEmpty()) {
            return ResponseVo.success(new java.util.ArrayList<>());
        }
        
        List<SysPermission> permissions = listByIds(permissionIds);
        return ResponseVo.success(permissions);
    }
}
