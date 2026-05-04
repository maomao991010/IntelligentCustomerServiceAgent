package com.ticketing.config;

import com.ticketing.annotation.OperationLog;
import com.ticketing.entity.OperationLogEntity;
import com.ticketing.service.OperationLogService;
import com.ticketing.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(com.ticketing.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();

        OperationLogEntity logEntity = new OperationLogEntity();
        logEntity.setStatus(1);

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                logEntity.setRequestUrl(request.getRequestURI());
                logEntity.setRequestMethod(request.getMethod());
                logEntity.setIp(getClientIp(request));

                String token = request.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    try {
                        Long userId = jwtUtil.getUserIdFromToken(token.substring(7));
                        logEntity.setUserId(userId);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取请求信息失败", e);
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        OperationLog operationLog = method.getAnnotation(OperationLog.class);

        if (operationLog != null) {
            logEntity.setOperation(operationLog.value());
            logEntity.setModule(operationLog.module());
            logEntity.setType(operationLog.type().name());
        }

        logEntity.setMethod(point.getTarget().getClass().getName() + "." + method.getName());

        try {
            Object[] args = point.getArgs();
            if (args != null && args.length > 0) {
                try {
                    String params = objectMapper.writeValueAsString(args);
                    if (params.length() > 2000) {
                        params = params.substring(0, 2000) + "...";
                    }
                    logEntity.setRequestParams(params);
                } catch (Exception e) {
                    logEntity.setRequestParams("参数序列化失败");
                }
            }

            Object result = point.proceed();

            try {
                String resultStr = objectMapper.writeValueAsString(result);
                if (resultStr.length() > 2000) {
                    resultStr = resultStr.substring(0, 2000) + "...";
                }
                logEntity.setResponseResult(resultStr);
            } catch (Exception e) {
                logEntity.setResponseResult("结果序列化失败");
            }

            return result;
        } catch (Throwable e) {
            logEntity.setStatus(0);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500);
            }
            logEntity.setErrorMsg(errorMsg);
            throw e;
        } finally {
            logEntity.setDuration(System.currentTimeMillis() - startTime);
            operationLogService.saveLog(logEntity);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
