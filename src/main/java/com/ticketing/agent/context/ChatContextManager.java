package com.ticketing.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.dao.ChatContextDao;
import com.ticketing.entity.ChatContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ChatContextManager {

    private static final String REDIS_KEY_PREFIX = "chat:session:";
    private static final long SESSION_TIMEOUT = 1800;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatContextDao chatContextDao;

    public ChatSession getOrCreateSession(String sessionId, Long userId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                ChatSession session = objectMapper.convertValue(cached, ChatSession.class);
                log.debug("从Redis获取会话: sessionId={}", sessionId);
                return session;
            }
        } catch (Exception e) {
            log.warn("从Redis获取会话失败: {}", e.getMessage());
        }

        ChatSession session = loadFromDatabase(sessionId);
        if (session != null) {
            log.info("从数据库恢复会话: sessionId={}", sessionId);
            saveToRedis(session);
            return session;
        }

        session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setMessages(new ArrayList<>());
        session.setCreateTime(System.currentTimeMillis());
        
        saveSession(session);
        log.info("创建新会话: sessionId={}", sessionId);
        return session;
    }

    public void saveSession(ChatSession session) {
        saveToRedis(session);
        saveToDatabase(session);
    }

    private void saveToRedis(ChatSession session) {
        String key = REDIS_KEY_PREFIX + session.getSessionId();
        try {
            redisTemplate.opsForValue().set(key, session, SESSION_TIMEOUT, TimeUnit.SECONDS);
            log.debug("会话已保存到Redis: sessionId={}", session.getSessionId());
        } catch (Exception e) {
            log.error("保存会话到Redis失败", e);
        }
    }

    private void saveToDatabase(ChatSession session) {
        try {
            String contextData = objectMapper.writeValueAsString(session);
            
            LambdaQueryWrapper<ChatContext> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatContext::getSessionId, session.getSessionId());
            
            ChatContext existing = chatContextDao.selectOne(wrapper);
            LocalDateTime now = LocalDateTime.now();
            
            if (existing != null) {
                existing.setContextData(contextData);
                existing.setUpdateTime(now);
                chatContextDao.updateById(existing);
                log.debug("会话已更新到数据库: sessionId={}", session.getSessionId());
            } else {
                ChatContext chatContext = new ChatContext();
                chatContext.setSessionId(session.getSessionId());
                chatContext.setContextData(contextData);
                chatContext.setCreateTime(now);
                chatContext.setUpdateTime(now);
                chatContextDao.insert(chatContext);
                log.debug("会话已保存到数据库: sessionId={}", session.getSessionId());
            }
        } catch (Exception e) {
            log.error("保存会话到数据库失败", e);
        }
    }

    private ChatSession loadFromDatabase(String sessionId) {
        try {
            LambdaQueryWrapper<ChatContext> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatContext::getSessionId, sessionId);
            
            ChatContext chatContext = chatContextDao.selectOne(wrapper);
            if (chatContext != null && chatContext.getContextData() != null) {
                return objectMapper.readValue(chatContext.getContextData(), ChatSession.class);
            }
        } catch (Exception e) {
            log.warn("从数据库加载会话失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
        return null;
    }

    public ChatSession getSession(String sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.convertValue(cached, ChatSession.class);
            }
        } catch (Exception e) {
            log.warn("从Redis获取会话失败: {}", e.getMessage());
        }
        
        ChatSession session = loadFromDatabase(sessionId);
        if (session != null) {
            saveToRedis(session);
        }
        return session;
    }

    public void removeSession(String sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
            log.info("会话已从Redis删除: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("从Redis删除会话失败", e);
        }
        
        try {
            LambdaQueryWrapper<ChatContext> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatContext::getSessionId, sessionId);
            chatContextDao.delete(wrapper);
            log.info("会话已从数据库删除: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("从数据库删除会话失败", e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatSession {
        private String sessionId;
        private Long userId;
        private List<Message> messages;
        private long createTime;

        public void addMessage(String question, String answer) {
            Message message = new Message();
            message.setQuestion(question);
            message.setAnswer(answer);
            message.setTimestamp(System.currentTimeMillis());
            
            messages.add(message);
            
            if (messages.size() > 5) {
                messages = messages.subList(messages.size() - 5, messages.size());
            }
        }

        public List<Message> getRecentMessages(int n) {
            int size = messages.size();
            int start = Math.max(0, size - n);
            return messages.subList(start, size);
        }

        public String getHistoryText() {
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Message msg : messages) {
                sb.append("用户：").append(msg.getQuestion()).append("\n");
                sb.append("客服：").append(msg.getAnswer()).append("\n\n");
            }
            return sb.toString();
        }
    }

    @Data
    public static class Message {
        private String question;
        private String answer;
        private long timestamp;
    }
}
