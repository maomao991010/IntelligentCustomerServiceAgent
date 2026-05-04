package com.ticketing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.customer-service")
public class AiCustomerServiceProperties {

    private boolean enabled = true;

    private String provider = "deepseek";

    private DashscopeConfig dashscope = new DashscopeConfig();

    private QianfanConfig qianfan = new QianfanConfig();

    private DeepseekConfig deepseek = new DeepseekConfig();

    private KnowledgeBaseConfig knowledgeBase = new KnowledgeBaseConfig();

    private ChatConfig chat = new ChatConfig();

    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    private TokenLimitConfig tokenLimit = new TokenLimitConfig();

    @Data
    public static class DashscopeConfig {
        private String apiKey = "";
        private String modelName = "qwen-turbo";
        private double temperature = 0.7;
        private int maxTokens = 1000;
    }

    @Data
    public static class QianfanConfig {
        private String apiKey = "";
        private String secretKey = "";
        private String modelName = "ERNIE-Bot-turbo";
    }

    @Data
    public static class DeepseekConfig {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String modelName = "deepseek-chat";
        private double temperature = 0.7;
        private int maxTokens = 1000;
    }

    @Data
    public static class KnowledgeBaseConfig {
        private boolean enableAiFallback = true;
        private double confidenceThreshold = 0.6;
    }

    @Data
    public static class ChatConfig {
        private int maxHistorySize = 10;
        private int sessionTimeout = 1800;
    }

    @Data
    public static class VectorStoreConfig {
        private boolean enabled = true;
        private String provider = "qdrant";
        private String embeddingProvider = "ollama";
        private String embeddingModelName = "nomic-embed-text";
        private OllamaConfig ollama = new OllamaConfig();
        private DashscopeOpenaiConfig dashscopeOpenai = new DashscopeOpenaiConfig();
        private QdrantConfig qdrant = new QdrantConfig();
        private double similarityThreshold = 0.7;
    }

    @Data
    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
    }

    @Data
    public static class DashscopeOpenaiConfig {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String embeddingModelName = "text-embedding-v4";
        private Integer dimensions = 1024;
    }

    @Data
    public static class QdrantConfig {
        private String host = "localhost";
        private int port = 6334;
        private Integer httpPort = 6333;
        private String collectionName = "ticketing-faq";
        private String apiKey = "";
    }

    @Data
    public static class TokenLimitConfig {
        private boolean enabled = true;
        private long maxTokens = 1000000;
        private String resetCron = "0 0 0 1 * ?";
    }
}
