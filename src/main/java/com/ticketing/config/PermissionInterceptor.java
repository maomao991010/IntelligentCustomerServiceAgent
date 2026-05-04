package com.ticketing.config;

import com.ticketing.annotation.RequirePermission;
import com.ticketing.annotation.RequireRole;
import com.ticketing.service.AuthService;
import com.ticketing.utils.JwtUtil;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class PermissionInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Class<?> clazz = method.getDeclaringClass();

        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = clazz.getAnnotation(RequirePermission.class);
        }

        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = clazz.getAnnotation(RequireRole.class);
        }

        if (requirePermission == null && requireRole == null) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Long userId = null;
        try {
            if (token != null) {
                userId = jwtUtil.getUserIdFromToken(token);
            }
        } catch (Exception e) {
            log.warn("Token解析失败", e);
        }

        if (userId == null) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\",\"data\":null}");
            return false;
        }

        if (requirePermission != null) {
            String[] permissions = requirePermission.value();
            boolean hasPermission = authService.hasAnyPermission(userId, Arrays.asList(permissions));
            if (!hasPermission) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(403);
                response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null}");
                return false;
            }
        }

        if (requireRole != null) {
            String[] roles = requireRole.value();
            boolean hasRole = false;
            for (String role : roles) {
                if (authService.hasRole(userId, role)) {
                    hasRole = true;
                    break;
                }
            }
            if (!hasRole) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(403);
                response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null}");
                return false;
            }
        }

        return true;
    }
}
