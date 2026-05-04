package com.ticketing.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SeatStatusWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, List<WebSocketSession>> sessionMap = new ConcurrentHashMap<>();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            if (!sessionMap.containsKey(sessionId)) {
                sessionMap.put(sessionId, new ArrayList<>());
            }
            sessionMap.get(sessionId).add(session);
            log.info("WebSocket连接建立: {}", sessionId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null && sessionMap.containsKey(sessionId)) {
            sessionMap.get(sessionId).remove(session);
            if (sessionMap.get(sessionId).isEmpty()) {
                sessionMap.remove(sessionId);
            }
            log.info("WebSocket连接关闭: {}", sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("收到WebSocket消息: {}", message.getPayload());
    }

    public void broadcastSeatStatus(String sessionId, List<Map<String, Object>> seatStatuses) {
        try {
            if (sessionMap.containsKey(sessionId)) {
                String message = objectMapper.writeValueAsString(seatStatuses);
                for (WebSocketSession session : sessionMap.get(sessionId)) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(message));
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播座位状态失败, sessionId={}", sessionId, e);
        }
    }
}
