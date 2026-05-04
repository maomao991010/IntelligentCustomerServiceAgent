package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketing.dao.SysPermissionDao;
import com.ticketing.dao.SysRoleDao;
import com.ticketing.dao.SysRolePermissionDao;
import com.ticketing.dao.SysUserRoleDao;
import com.ticketing.entity.SysPermission;
import com.ticketing.entity.SysRole;
import com.ticketing.entity.SysRolePermission;
import com.ticketing.entity.SysUserRole;
import com.ticketing.service.AuthService;
import com.ticketing.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserRoleDao sysUserRoleDao;

    @Autowired
    private SysRolePermissionDao sysRolePermissionDao;

    @Autowired
    private SysRoleDao sysRoleDao;

    @Autowired
    private SysPermissionDao sysPermissionDao;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public List<String> getUserPermissions(Long userId) {
        String permissionKey = "user:permissions:" + userId;
        Object cachedPermissions = redisUtil.get(permissionKey);
        if (cachedPermissions != null) {
            return (List<String>) cachedPermissions;
        }
        
        List<String> permissions = loadUserPermissionsFromDB(userId);
        redisUtil.set(permissionKey, permissions, 24 * 60 * 60);
        return permissions;
    }

    private List<String> loadUserPermissionsFromDB(Long userId) {
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getUserId, userId);
        List<SysUserRole> userRoles = sysUserRoleDao.selectList(userRoleWrapper);
        
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        
        LambdaQueryWrapper<SysRole> activeRoleWrapper = new LambdaQueryWrapper<>();
        activeRoleWrapper.in(SysRole::getId, roleIds).eq(SysRole::getStatus, 1);
        List<SysRole> activeRoles = sysRoleDao.selectList(activeRoleWrapper);
        List<Long> activeRoleIds = activeRoles.stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        
        if (activeRoleIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        LambdaQueryWrapper<SysRolePermission> rolePermWrapper = new LambdaQueryWrapper<>();
        rolePermWrapper.in(SysRolePermission::getRoleId, activeRoleIds);
        List<SysRolePermission> rolePermissions = sysRolePermissionDao.selectList(rolePermWrapper);
        
        List<Long> permissionIds = rolePermissions.stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        
        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        LambdaQueryWrapper<SysPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.in(SysPermission::getId, permissionIds).eq(SysPermission::getStatus, 1);
        List<SysPermission> permissions = sysPermissionDao.selectList(permWrapper);
        
        return permissions.stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserRoleCodes(Long userId) {
        String roleKey = "user:roles:" + userId;
        Object cachedRoles = redisUtil.get(roleKey);
        if (cachedRoles != null) {
            return (List<String>) cachedRoles;
        }
        
        List<String> roles = loadUserRolesFromDB(userId);
        redisUtil.set(roleKey, roles, 24 * 60 * 60);
        return roles;
    }

    private List<String> loadUserRolesFromDB(Long userId) {
        LambdaQueryWrapper<SysUserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRole::getUserId, userId);
        List<SysUserRole> userRoles = sysUserRoleDao.selectList(userRoleWrapper);
        
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        
        LambdaQueryWrapper<SysRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(SysRole::getId, roleIds).eq(SysRole::getStatus, 1);
        List<SysRole> roles = sysRoleDao.selectList(roleWrapper);
        
        return roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    public void refreshUserPermissions(Long userId) {
        String permissionKey = "user:permissions:" + userId;
        String roleKey = "user:roles:" + userId;
        redisUtil.delete(permissionKey);
        redisUtil.delete(roleKey);
        
        List<String> permissions = loadUserPermissionsFromDB(userId);
        List<String> roles = loadUserRolesFromDB(userId);
        
        log.info("刷新用户权限缓存: userId={}, roles={}, permissions={}", userId, roles, permissions);
        
        redisUtil.set(permissionKey, permissions, 24 * 60 * 60);
        redisUtil.set(roleKey, roles, 24 * 60 * 60);
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        List<String> permissions = getUserPermissions(userId);
        return matchPermission(permissions, permissionCode);
    }

    @Override
    public boolean hasAnyPermission(Long userId, List<String> permissionCodes) {
        List<String> permissions = getUserPermissions(userId);
        for (String code : permissionCodes) {
            if (matchPermission(permissions, code)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchPermission(List<String> userPermissions, String requiredPermission) {
        for (String userPerm : userPermissions) {
            if (userPerm.equals("*:*:*")) {
                return true;
            }
            if (userPerm.equals(requiredPermission)) {
                return true;
            }
            if (wildcardMatch(userPerm, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    private boolean wildcardMatch(String pattern, String str) {
        String[] patternParts = pattern.split(":");
        String[] strParts = str.split(":");
        
        if (patternParts.length != strParts.length) {
            return false;
        }
        
        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].equals("*") && !patternParts[i].equals(strParts[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        List<String> roles = getUserRoleCodes(userId);
        return roles.contains(roleCode);
    }
}
