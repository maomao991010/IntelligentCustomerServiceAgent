package com.ticketing.controller;

import com.ticketing.annotation.RequirePermission;
import com.ticketing.entity.SysRole;
import com.ticketing.service.SysRoleService;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/role")
public class SysRoleController {

    @Autowired
    private SysRoleService sysRoleService;

    @GetMapping("/list")
    @RequirePermission("role:view")
    public ResponseVo getAllRoles() {
        return sysRoleService.getAllRoles();
    }

    @GetMapping("/{id}")
    @RequirePermission("role:view")
    public ResponseVo getRoleById(@PathVariable Long id) {
        return sysRoleService.getRoleById(id);
    }

    @PostMapping("/save")
    @RequirePermission("role:add")
    public ResponseVo saveRole(@RequestBody SysRole role) {
        return sysRoleService.saveRole(role);
    }

    @PutMapping("/update")
    @RequirePermission("role:edit")
    public ResponseVo updateRole(@RequestBody SysRole role) {
        return sysRoleService.updateRole(role);
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission("role:delete")
    public ResponseVo deleteRole(@PathVariable Long id) {
        return sysRoleService.deleteRole(id);
    }

    @GetMapping("/user/{userId}")
    @RequirePermission("role:view")
    public ResponseVo getRolesByUserId(@PathVariable Long userId) {
        return sysRoleService.getRolesByUserId(userId);
    }
}
