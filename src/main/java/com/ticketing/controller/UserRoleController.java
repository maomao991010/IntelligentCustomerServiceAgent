package com.ticketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.dao.SysUserRoleDao;
import com.ticketing.entity.SysUserRole;
import com.ticketing.service.AuthService;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/admin/user-role")
@RequirePermission("role:assign")
public class UserRoleController {

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private AuthService authService;

    @PostMapping("/assign")
    @Transactional
    @RequirePermission("role:assign")
    public ResponseVo assignRoles(@RequestBody AssignRolesRequest request) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, request.getUserId());
        sysUserRoleDao.delete(wrapper);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(request.getUserId());
                userRole.setRoleId(roleId);
                sysUserRoleDao.insert(userRole);
            }
        }

        authService.refreshUserPermissions(request.getUserId());

        return ResponseVo.success(null);
    }

    @lombok.Data
    public static class AssignRolesRequest {
        private Long userId;
        private List<Long> roleIds;
    }
}
