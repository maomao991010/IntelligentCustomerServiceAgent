(function() {
    var token = localStorage.getItem('token');
    var adminPages = ['admin.html', 'admin-home.html', 'faq-admin.html', 
                        'token-usage.html', 'role-manage.html', 'permission-manage.html',
                        'user-role-assign.html', 'role-permission-assign.html',
                        'user-info.html', 'operation-log.html', 'refund-audit.html'];
    
    var adminRequiredRoles = ['ADMIN', 'OPERATOR', 'CUSTOMER_SERVICE'];
    
    var adminPermissionPatterns = [
        'activity:add', 'activity:edit', 'activity:delete',
        'faq:add', 'faq:edit', 'faq:delete',
        'role:view', 'role:add', 'role:edit', 'role:delete', 'role:assign',
        'permission:view', 'permission:add', 'permission:edit', 'permission:delete', 'permission:assign',
        'token:reset',
        'refund:audit', 'refund:view'
    ];
    
    var currentPage = window.location.pathname.split('/').pop();
    
    function isAdminPage() {
        return adminPages.indexOf(currentPage) !== -1;
    }
    
    function wildcardMatch(pattern, str) {
        var patternParts = pattern.split(':');
        var strParts = str.split(':');
        
        if (patternParts.length !== strParts.length) {
            return false;
        }
        
        for (var i = 0; i < patternParts.length; i++) {
            if (patternParts[i] !== '*' && patternParts[i] !== strParts[i]) {
                return false;
            }
        }
        return true;
    }
    
    function checkAdminAccess(roles, permissions) {
        for (var i = 0; i < roles.length; i++) {
            if (adminRequiredRoles.indexOf(roles[i]) !== -1) {
                return true;
            }
        }
        
        if (permissions.indexOf('*:*:*') !== -1) {
            return true;
        }
        
        for (var i = 0; i < permissions.length; i++) {
            var userPerm = permissions[i];
            if (userPerm === '*:*:*') return true;
            
            for (var j = 0; j < adminPermissionPatterns.length; j++) {
                if (userPerm === adminPermissionPatterns[j]) {
                    return true;
                }
                if (wildcardMatch(userPerm, adminPermissionPatterns[j])) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    window.hasPermission = function(permissionCode) {
        var permissions = JSON.parse(localStorage.getItem('permissions') || '[]');
        if (permissions.indexOf('*:*:*') !== -1) {
            return true;
        }
        if (permissions.indexOf(permissionCode) !== -1) {
            return true;
        }
        
        for (var i = 0; i < permissions.length; i++) {
            if (wildcardMatch(permissions[i], permissionCode)) {
                return true;
            }
        }
        return false;
    };
    
    window.hasRole = function(roleCode) {
        var roles = JSON.parse(localStorage.getItem('roles') || '[]');
        return roles.indexOf(roleCode) !== -1;
    };
    
    window.hasAdminAccess = function() {
        var roles = JSON.parse(localStorage.getItem('roles') || '[]');
        var permissions = JSON.parse(localStorage.getItem('permissions') || '[]');
        return checkAdminAccess(roles, permissions);
    };
    
    window.applyPermissionControls = function() {
        var permissionMap = {
            'perm-activity-add': 'activity:add',
            'perm-activity-edit': 'activity:edit',
            'perm-activity-delete': 'activity:delete',
            'perm-faq-add': 'faq:add',
            'perm-faq-edit': 'faq:edit',
            'perm-faq-delete': 'faq:delete',
            'perm-role-add': 'role:add',
            'perm-role-edit': 'role:edit',
            'perm-role-delete': 'role:delete',
            'perm-role-assign': 'permission:assign',
            'perm-permission-add': 'permission:add',
            'perm-permission-edit': 'permission:edit',
            'perm-permission-delete': 'permission:delete',
            'perm-token-reset': 'token:reset'
        };
        
        for (var className in permissionMap) {
            var permCode = permissionMap[className];
            var elements = document.querySelectorAll('.' + className);
            for (var i = 0; i < elements.length; i++) {
                if (hasPermission(permCode)) {
                    elements[i].style.display = '';
                }
            }
        }
    };
    
    if (isAdminPage()) {
        if (!token) {
            window.location.href = 'login.html';
            return;
        }
        
        fetch('/api/v1/user/info', {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        }).then(function(response) {
            if (!response.ok) {
                if (response.status === 401 || response.status === 403) {
                    localStorage.removeItem('token');
                    localStorage.removeItem('user');
                    localStorage.removeItem('permissions');
                    localStorage.removeItem('roles');
                    window.location.href = 'login.html';
                }
                return null;
            }
            return response.json();
        }).then(function(data) {
            if (data && data.code === 200 && data.data) {
                localStorage.setItem('permissions', JSON.stringify(data.data.permissions));
                localStorage.setItem('roles', JSON.stringify(data.data.roles));
                
                var roles = data.data.roles || [];
                var permissions = data.data.permissions || [];
                
                console.log('[Auth] 用户角色:', roles);
                console.log('[Auth] 用户权限:', permissions);
                
                var hasAccess = checkAdminAccess(roles, permissions);
                console.log('[Auth] 管理端访问权限:', hasAccess);
                
                if (!hasAccess) {
                    localStorage.removeItem('token');
                    localStorage.removeItem('user');
                    localStorage.removeItem('permissions');
                    localStorage.removeItem('roles');
                    window.location.href = 'login.html?error=' + encodeURIComponent('您没有管理后台访问权限，请登录有权限的账号');
                    return;
                }
                
                window.applyPermissionControls();
                
                var event = new CustomEvent('permissionsLoaded');
                document.dispatchEvent(event);
            }
        }).catch(function(error) {
            console.error('Auth check failed:', error);
        });
    }
})();
