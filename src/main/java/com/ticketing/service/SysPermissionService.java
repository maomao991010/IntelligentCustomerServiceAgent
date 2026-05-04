package com.ticketing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketing.entity.SysPermission;
import com.ticketing.vo.ResponseVo;
import java.util.List;

public interface SysPermissionService extends IService<SysPermission> {
    ResponseVo getAllPermissions();
    ResponseVo getPermissionsByModule(String module);
    ResponseVo getPermissionById(Long id);
    ResponseVo savePermission(SysPermission permission);
    ResponseVo updatePermission(SysPermission permission);
    ResponseVo deletePermission(Long id);
    ResponseVo getPermissionsByRoleId(Long roleId);
}
