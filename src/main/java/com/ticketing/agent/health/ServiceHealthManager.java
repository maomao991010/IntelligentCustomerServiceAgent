package com.ticketing.agent.health;

import com.ticketing.agent.token.TokenUsageManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class ServiceHealthManager {

    private final AtomicBoolean vectorStoreAvailable = new AtomicBoolean(true);
    private final AtomicBoolean aiModelAvailable = new AtomicBoolean(true);
    private final AtomicBoolean faqRetrievalAvailable = new AtomicBoolean(true);
    private final AtomicBoolean tokenLimitExceeded = new AtomicBoolean(false);
    
    private final AtomicReference<HealthStatus> vectorStoreStatus = new AtomicReference<>(HealthStatus.HEALTHY);
    private final AtomicReference<HealthStatus> aiModelStatus = new AtomicReference<>(HealthStatus.HEALTHY);
    private final AtomicReference<HealthStatus> faqRetrievalStatus = new AtomicReference<>(HealthStatus.HEALTHY);

    @Autowired(required = false)
    private com.ticketing.agent.vector.VectorStoreService vectorStoreService;

    @Autowired(required = false)
    private com.ticketing.agent.ai.AiModelService aiModelService;

    @Autowired(required = false)
    private TokenUsageManager tokenUsageManager;

    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void healthCheck() {
        checkVectorStore();
        checkAiModel();
        checkFaqRetrieval();
        checkTokenLimit();
    }

    private void checkTokenLimit() {
        try {
            if (tokenUsageManager != null) {
                boolean exceeded = tokenUsageManager.isTokenLimitExceeded();
                if (exceeded) {
                    if (!tokenLimitExceeded.get()) {
                        log.warn("Token 使用量已达到上限！将停止 API 调用，使用本地服务");
                    }
                    tokenLimitExceeded.set(true);
                } else {
                    if (tokenLimitExceeded.get()) {
                        log.info("Token 使用量已恢复正常");
                    }
                    tokenLimitExceeded.set(false);
                }
            }
        } catch (Exception e) {
            log.error("Token 限制检查失败", e);
        }
    }

    private void checkVectorStore() {
        try {
            if (vectorStoreService != null) {
                boolean available = vectorStoreService.healthCheck();
                if (available) {
                    if (!vectorStoreAvailable.get()) {
                        log.info("向量存储恢复可用");
                    }
                    vectorStoreAvailable.set(true);
                    vectorStoreStatus.set(HealthStatus.HEALTHY);
                } else {
                    if (vectorStoreAvailable.get()) {
                        log.warn("向量存储不可用，尝试重连...");
                        boolean reconnected = vectorStoreService.tryReconnect();
                        if (reconnected) {
                            vectorStoreAvailable.set(true);
                            vectorStoreStatus.set(HealthStatus.HEALTHY);
                            log.info("向量存储重连成功");
                        } else {
                            vectorStoreAvailable.set(false);
                            vectorStoreStatus.set(HealthStatus.UNHEALTHY);
                        }
                    } else {
                        vectorStoreAvailable.set(false);
                        vectorStoreStatus.set(HealthStatus.UNHEALTHY);
                    }
                }
            } else {
                vectorStoreAvailable.set(false);
                vectorStoreStatus.set(HealthStatus.UNHEALTHY);
            }
        } catch (Exception e) {
            log.error("向量存储健康检查失败", e);
            vectorStoreAvailable.set(false);
            vectorStoreStatus.set(HealthStatus.UNHEALTHY);
        }
    }

    private void checkAiModel() {
        try {
            if (aiModelService != null) {
                boolean available = aiModelService.healthCheck();
                if (available) {
                    if (!aiModelAvailable.get()) {
                        log.info("AI模型恢复可用");
                    }
                    aiModelAvailable.set(true);
                    aiModelStatus.set(HealthStatus.HEALTHY);
                } else {
                    if (aiModelAvailable.get()) {
                        log.warn("AI模型不可用，尝试重连...");
                        boolean reconnected = aiModelService.tryReconnect();
                        if (reconnected) {
                            aiModelAvailable.set(true);
                            aiModelStatus.set(HealthStatus.HEALTHY);
                            log.info("AI模型重连成功");
                        } else {
                            aiModelAvailable.set(false);
                            aiModelStatus.set(HealthStatus.UNHEALTHY);
                        }
                    } else {
                        aiModelAvailable.set(false);
                        aiModelStatus.set(HealthStatus.UNHEALTHY);
                    }
                }
            } else {
                aiModelAvailable.set(false);
                aiModelStatus.set(HealthStatus.UNHEALTHY);
            }
        } catch (Exception e) {
            log.error("AI模型健康检查失败", e);
            aiModelAvailable.set(false);
            aiModelStatus.set(HealthStatus.UNHEALTHY);
        }
    }

    private void checkFaqRetrieval() {
        try {
            boolean hasDao = false;
            try {
                Class.forName("com.ticketing.dao.FaqDao");
                hasDao = true;
            } catch (ClassNotFoundException e) {
            }
            
            if (!hasDao) {
                faqRetrievalAvailable.set(false);
                faqRetrievalStatus.set(HealthStatus.UNHEALTHY);
                return;
            }
            
            if (vectorStoreService != null) {
                boolean embeddingModelOk = vectorStoreService.isEmbeddingModelAvailable();
                boolean vectorStoreOk = vectorStoreService.isVectorStoreAvailable();
                
                if (embeddingModelOk && vectorStoreOk) {
                    faqRetrievalAvailable.set(true);
                    faqRetrievalStatus.set(HealthStatus.HEALTHY);
                    log.debug("FAQ检索服务可用：嵌入模型和向量数据库都正常");
                } else {
                    faqRetrievalAvailable.set(false);
                    faqRetrievalStatus.set(HealthStatus.UNHEALTHY);
                    log.warn("FAQ检索服务不可用：embeddingModelOk={}, vectorStoreOk={}", 
                            embeddingModelOk, vectorStoreOk);
                }
            } else {
                faqRetrievalAvailable.set(false);
                faqRetrievalStatus.set(HealthStatus.UNHEALTHY);
                log.warn("FAQ检索服务不可用：VectorStoreService未初始化");
            }
        } catch (Exception e) {
            log.error("FAQ检索健康检查失败", e);
            faqRetrievalAvailable.set(false);
            faqRetrievalStatus.set(HealthStatus.UNHEALTHY);
        }
    }

    public boolean isVectorStoreAvailable() {
        return vectorStoreAvailable.get();
    }

    public boolean isAiModelAvailable() {
        return aiModelAvailable.get();
    }

    public boolean isFaqRetrievalAvailable() {
        return faqRetrievalAvailable.get();
    }

    public boolean isTokenLimitExceeded() {
        return tokenLimitExceeded.get();
    }

    public HealthStatus getVectorStoreStatus() {
        return vectorStoreStatus.get();
    }

    public HealthStatus getAiModelStatus() {
        return aiModelStatus.get();
    }

    public HealthStatus getFaqRetrievalStatus() {
        return faqRetrievalStatus.get();
    }

    public void markVectorStoreUnhealthy() {
        vectorStoreAvailable.set(false);
        vectorStoreStatus.set(HealthStatus.UNHEALTHY);
        log.warn("向量存储标记为不可用");
    }

    public void markVectorStoreHealthy() {
        vectorStoreAvailable.set(true);
        vectorStoreStatus.set(HealthStatus.HEALTHY);
        log.info("向量存储恢复可用");
    }

    public void markAiModelUnhealthy() {
        aiModelAvailable.set(false);
        aiModelStatus.set(HealthStatus.UNHEALTHY);
        log.warn("AI模型标记为不可用");
    }

    public void markAiModelHealthy() {
        aiModelAvailable.set(true);
        aiModelStatus.set(HealthStatus.HEALTHY);
        log.info("AI模型恢复可用");
    }

    public String getHealthSummary() {
        return String.format("VectorStore: %s, AiModel: %s, FaqRetrieval: %s",
                vectorStoreStatus.get(), aiModelStatus.get(), faqRetrievalStatus.get());
    }
}
