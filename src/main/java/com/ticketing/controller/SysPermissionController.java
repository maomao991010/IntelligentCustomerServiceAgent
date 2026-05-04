package com.ticketing.controller;

import com.ticketing.annotation.RequirePermission;
import com.ticketing.entity.SysPermission;
import com.ticketing.service.SysPermissionService;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/permission")
public class SysPermissionController {

    @Autowired
    private SysPermissionService sysPermissionService;

    @GetMapping("/list")
    @RequirePermission("permission:view")
    public ResponseVo getAllPermissions() {
        return sysPermissionService.getAllPermissions();
    }

    @GetMapping("/module/{module}")
    @RequirePermission("permission:view")
    public ResponseVo getPermissionsByModule(@PathVariable String module) {
        return sysPermissionService.getPermissionsByModule(module);
    }

    @GetMapping("/{id}")
    @RequirePermission("permission:view")
    public ResponseVo getPermissionById(@PathVariable Long id) {
        return sysPermissionService.getPermissionById(id);
    }

    @PostMapping("/save")
    @RequirePermission("permission:add")
    public ResponseVo savePermission(@RequestBody SysPermission permission) {
        return sysPermissionService.savePermission(permission);
    }

    @PutMapping("/update")
    @RequirePermission("permission:edit")
    public ResponseVo updatePermission(@RequestBody SysPermission permission) {
        return sysPermissionService.updatePermission(permission);
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission("permission:delete")
    public ResponseVo deletePermission(@PathVariable Long id) {
        return sysPermissionService.deletePermission(id);
    }

    @GetMapping("/role/{roleId}")
    @RequirePermission("permission:view")
    public ResponseVo getPermissionsByRoleId(@PathVariable Long roleId) {
        return sysPermissionService.getPermissionsByRoleId(roleId);
    }
}
