package com.ticketing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticketing.entity.SysRole;
import com.ticketing.vo.ResponseVo;
import java.util.List;

public interface SysRoleService extends IService<SysRole> {
    ResponseVo getAllRoles();
    ResponseVo getRoleById(Long id);
    ResponseVo saveRole(SysRole role);
    ResponseVo updateRole(SysRole role);
    ResponseVo deleteRole(Long id);
    ResponseVo getRolesByUserId(Long userId);
}
