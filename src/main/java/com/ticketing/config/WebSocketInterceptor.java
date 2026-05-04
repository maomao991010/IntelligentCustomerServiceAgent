package com.ticketing.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 从请求参数中获取token
        String token = request.getURI().getQuery().split("token=")[1].split("&")[0];
        // 从请求参数中获取sessionId
        String sessionId = request.getURI().getQuery().split("sessionId=")[1].split("&")[0];
        
        // 验证token
        // 这里简化处理，实际项目中应该使用JwtUtil验证token
        if (token != null && !token.isEmpty()) {
            attributes.put("token", token);
            attributes.put("sessionId", sessionId);
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // 握手后的处理
    }
}
