package com.ticketing.config;

import com.ticketing.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RedisUtil redisUtil;

    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 60;
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit_";
    private static final String RATE_LIMIT_USER_PREFIX = "rate_limit_user_";

    @Value("${ticketing.rate-limit.ip:60}")
    private int ipRateLimit;

    @Value("${ticketing.rate-limit.user:120}")
    private int userRateLimit;

    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/auth"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        
        if (isExcludedPath(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (redisUtil == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String userId = getUserId(request);

        try {
            if (!checkIpRateLimit(ip)) {
                sendRateLimitResponse(response, "IP请求过于频繁，请稍后再试");
                return;
            }

            if (userId != null && !checkUserRateLimit(userId)) {
                sendRateLimitResponse(response, "用户请求过于频繁，请稍后再试");
                return;
            }

            if (!checkSpecialApiRateLimit(requestUri, ip, userId)) {
                sendRateLimitResponse(response, "该接口请求过于频繁，请稍后再试");
                return;
            }

        } catch (Exception e) {
            log.error("限流检查发生异常: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcludedPath(String requestUri) {
        return EXCLUDED_PATHS.stream().anyMatch(requestUri::startsWith);
    }

    private boolean checkIpRateLimit(String ip) {
        String key = RATE_LIMIT_KEY_PREFIX + ip;
        return checkRateLimit(key, ipRateLimit);
    }

    private boolean checkUserRateLimit(String userId) {
        String key = RATE_LIMIT_USER_PREFIX + userId;
        return checkRateLimit(key, userRateLimit);
    }

    private boolean checkRateLimit(String key, int limit) {
        Long count = redisUtil.increment(key);
        if (count == 1) {
            redisUtil.expire(key, 60);
        }
        return count <= limit;
    }

    private boolean checkSpecialApiRateLimit(String requestUri, String ip, String userId) {
        if (requestUri.contains("/seats/lock") || requestUri.contains("/orders/create")) {
            String lockKey = "api_lock_" + requestUri + "_" + ip;
            Long count = redisUtil.increment(lockKey);
            if (count == 1) {
                redisUtil.expire(lockKey, 10);
            }
            return count <= 10;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String getUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write("{\"code\": 429, \"message\": \"" + message + "\", \"data\": null}");
        writer.flush();
        writer.close();
    }
}
