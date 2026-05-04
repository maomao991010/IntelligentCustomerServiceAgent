package com.ticketing.controller;

import com.ticketing.service.AuthService;
import com.ticketing.vo.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@Tag(name = "权限管理", description = "用户权限与角色查询接口")
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping("/permissions/{userId}")
    @Operation(summary = "获取用户权限列表", description = "根据用户ID获取其所有权限码")
    public ResponseVo getUserPermissions(@PathVariable Long userId) {
        return ResponseVo.success(authService.getUserPermissions(userId));
    }

    @GetMapping("/roles/{userId}")
    @Operation(summary = "获取用户角色列表", description = "根据用户ID获取其所有角色码")
    public ResponseVo getUserRoleCodes(@PathVariable Long userId) {
        return ResponseVo.success(authService.getUserRoleCodes(userId));
    }

    @GetMapping("/check/{userId}/{permissionCode}")
    @Operation(summary = "检查用户权限", description = "检查用户是否拥有指定权限")
    public ResponseVo hasPermission(@PathVariable Long userId, @PathVariable String permissionCode) {
        return ResponseVo.success(authService.hasPermission(userId, permissionCode));
    }

    @GetMapping("/check-role/{userId}/{roleCode}")
    @Operation(summary = "检查用户角色", description = "检查用户是否拥有指定角色")
    public ResponseVo hasRole(@PathVariable Long userId, @PathVariable String roleCode) {
        return ResponseVo.success(authService.hasRole(userId, roleCode));
    }
}
