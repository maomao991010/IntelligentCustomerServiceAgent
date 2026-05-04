package com.ticketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.dao.SysRolePermissionDao;
import com.ticketing.dao.SysUserRoleDao;
import com.ticketing.entity.SysRolePermission;
import com.ticketing.entity.SysUserRole;
import com.ticketing.service.AuthService;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/role-permission")
@RequirePermission("permission:assign")
public class RolePermissionController {

    @Autowired
    private SysRolePermissionDao sysRolePermissionDao;

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private AuthService authService;

    @PostMapping("/assign")
    @Transactional
    @RequirePermission("permission:assign")
    public ResponseVo assignPermissions(@RequestBody AssignPermissionsRequest request) {
        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getRoleId, request.getRoleId());
        sysRolePermissionDao.delete(wrapper);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            for (Long permissionId : request.getPermissionIds()) {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(request.getRoleId());
                rolePermission.setPermissionId(permissionId);
                sysRolePermissionDao.insert(rolePermission);
            }
        }

        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getRoleId, request.getRoleId());
        List<SysUserRole> userRoles = sysUserRoleDao.selectList(userRoleWrapper);
        for (SysUserRole userRole : userRoles) {
            authService.refreshUserPermissions(userRole.getUserId());
        }

        return ResponseVo.success(null);
    }

    @lombok.Data
    public static class AssignPermissionsRequest {
        private Long roleId;
        private List<Long> permissionIds;
    }
}
