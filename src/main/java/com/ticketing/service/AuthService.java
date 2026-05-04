package com.ticketing.service;

import com.ticketing.vo.ResponseVo;
import java.util.List;

public interface AuthService {
    List<String> getUserPermissions(Long userId);
    List<String> getUserRoleCodes(Long userId);
    boolean hasPermission(Long userId, String permissionCode);
    boolean hasAnyPermission(Long userId, List<String> permissionCodes);
    boolean hasRole(Long userId, String roleCode);
    void refreshUserPermissions(Long userId);
}
