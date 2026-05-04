package com.ticketing.agent.token;

import com.ticketing.config.AiCustomerServiceProperties;
import com.ticketing.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TokenUsageManager {

    private static final String TOKEN_USAGE_KEY_PREFIX = "token_usage:";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired(required = false)
    private RedisUtil redisUtil;

    @Autowired
    private AiCustomerServiceProperties properties;

    private volatile boolean tokenLimitExceeded = false;

    public boolean isTokenLimitEnabled() {
        return properties.getTokenLimit() != null && properties.getTokenLimit().isEnabled();
    }

    public boolean isTokenLimitExceeded() {
        if (!isTokenLimitEnabled()) {
            return false;
        }
        return tokenLimitExceeded || getCurrentUsage() >= getMaxTokens();
    }

    public long getCurrentUsage() {
        if (!isTokenLimitEnabled() || redisUtil == null) {
            return 0;
        }
        try {
            String key = getCurrentMonthKey();
            Object usage = redisUtil.get(key);
            if (usage instanceof Long) {
                return (Long) usage;
            } else if (usage instanceof Integer) {
                return (Integer) usage;
            } else if (usage instanceof String) {
                try {
                    return Long.parseLong((String) usage);
                } catch (NumberFormatException e) {
                    log.warn("解析 token 使用量失败: {}", usage);
                }
            }
            return 0;
        } catch (Exception e) {
            log.warn("获取 token 使用量失败", e);
            return 0;
        }
    }

    public long getMaxTokens() {
        if (properties.getTokenLimit() == null) {
            return 1000000;
        }
        return properties.getTokenLimit().getMaxTokens();
    }

    public void addUsage(long tokens, boolean isLocal) {
        if (!isTokenLimitEnabled()) {
            return;
        }

        if (isLocal) {
            log.debug("本地服务调用，不计入 token 使用量: tokens={}", tokens);
            return;
        }

        if (redisUtil == null) {
            log.warn("Redis 不可用，无法统计 token 使用量");
            return;
        }

        try {
            String key = getCurrentMonthKey();
            long newUsage = redisUtil.increment(key, tokens);
            
            if (!redisUtil.exists(key)) {
                redisUtil.set(key, tokens, 35 * 24 * 60 * 60);
            }

            log.info("Token 使用量已更新: usage={}, max={}", newUsage, getMaxTokens());

            if (newUsage >= getMaxTokens()) {
                tokenLimitExceeded = true;
                log.warn("Token 使用量已达到上限！usage={}, max={}", newUsage, getMaxTokens());
            }
        } catch (Exception e) {
            log.warn("统计 token 使用量失败", e);
        }
    }

    public long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    public void resetUsage() {
        if (redisUtil == null) {
            log.warn("Redis 不可用，无法重置 token 使用量");
            return;
        }
        try {
            String key = getCurrentMonthKey();
            redisUtil.delete(key);
            tokenLimitExceeded = false;
            log.info("Token 使用量已重置");
        } catch (Exception e) {
            log.warn("重置 token 使用量失败", e);
        }
    }

    private String getCurrentMonthKey() {
        return TOKEN_USAGE_KEY_PREFIX + LocalDateTime.now().format(MONTH_FORMATTER);
    }
}
