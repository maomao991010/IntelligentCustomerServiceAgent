package com.ticketing.controller;

import com.ticketing.agent.token.TokenUsageManager;
import com.ticketing.config.AiCustomerServiceProperties;
import com.ticketing.vo.ResponseVo;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/token")
public class TokenUsageController {

    @Autowired(required = false)
    private TokenUsageManager tokenUsageManager;

    @Autowired
    private AiCustomerServiceProperties properties;

    @GetMapping("/usage")
    public ResponseVo getTokenUsage() {
        if (tokenUsageManager == null) {
            return ResponseVo.error("Token 统计服务不可用");
        }

        TokenUsageInfo info = new TokenUsageInfo();
        info.setEnabled(tokenUsageManager.isTokenLimitEnabled());
        info.setCurrentUsage(tokenUsageManager.getCurrentUsage());
        info.setMaxTokens(tokenUsageManager.getMaxTokens());
        info.setExceeded(tokenUsageManager.isTokenLimitExceeded());
        
        if (info.getMaxTokens() > 0) {
            info.setUsagePercent((double) info.getCurrentUsage() / info.getMaxTokens() * 100);
        } else {
            info.setUsagePercent(0.0);
        }

        return ResponseVo.success(info);
    }

    @PostMapping("/reset")
    public ResponseVo resetTokenUsage() {
        if (tokenUsageManager == null) {
            return ResponseVo.error("Token 统计服务不可用");
        }

        tokenUsageManager.resetUsage();
        return ResponseVo.success("Token 使用量已重置");
    }

    @Data
    public static class TokenUsageInfo {
        private boolean enabled;
        private long currentUsage;
        private long maxTokens;
        private boolean exceeded;
        private double usagePercent;
    }
}
