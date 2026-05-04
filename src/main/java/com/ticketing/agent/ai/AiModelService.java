package com.ticketing.agent.ai;

import com.ticketing.agent.token.TokenUsageManager;
import com.ticketing.config.AiCustomerServiceProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
public class AiModelService {

    @Autowired
    private AiCustomerServiceProperties properties;

    @Autowired(required = false)
    private TokenUsageManager tokenUsageManager;

    private ChatLanguageModel chatModel;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("AI客服功能已禁用");
            return;
        }

        String provider = properties.getProvider();
        log.info("初始化AI模型，提供商: {}", provider);

        try {
            switch (provider.toLowerCase()) {
                case "deepseek":
                    initDeepseek();
                    break;
                case "dashscope":
                    initDashscope();
                    break;
                case "qianfan":
                    log.warn("文心一言暂未实现");
                    break;
                default:
                    log.warn("未知的模型提供商: {}", provider);
            }
        } catch (Exception e) {
            log.error("AI模型初始化失败", e);
        }
    }

    private void initDeepseek() {
        String apiKey = properties.getDeepseek().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("DeepSeek API Key未配置，AI功能将不可用");
            return;
        }

        try {
            chatModel = OpenAiChatModel.builder()
                    .baseUrl(properties.getDeepseek().getBaseUrl())
                    .apiKey(apiKey)
                    .modelName(properties.getDeepseek().getModelName())
                    .temperature(properties.getDeepseek().getTemperature())
                    .maxTokens(properties.getDeepseek().getMaxTokens())
                    .build();
            log.info("DeepSeek模型初始化成功: {}", properties.getDeepseek().getModelName());
        } catch (Exception e) {
            log.error("DeepSeek模型初始化失败", e);
        }
    }

    private void initDashscope() {
        String apiKey = properties.getDashscope().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("通义千问API Key未配置，AI功能将不可用");
            return;
        }

        try {
            chatModel = QwenChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(properties.getDashscope().getModelName())
                    .temperature((float) properties.getDashscope().getTemperature())
                    .maxTokens(properties.getDashscope().getMaxTokens())
                    .build();
            log.info("通义千问模型初始化成功: {}", properties.getDashscope().getModelName());
        } catch (Exception e) {
            log.error("通义千问模型初始化失败", e);
        }
    }

    public String generateAnswer(String question, String context, String knowledge) {
        if (chatModel == null) {
            log.warn("AI模型未初始化，返回默认回答");
            return getDefaultAnswer(question);
        }

        if (isTokenLimitExceeded()) {
            log.warn("Token 使用量已达到上限，跳过 API 调用");
            return getDefaultAnswer(question);
        }

        try {
            String prompt = buildPrompt(question, context, knowledge);
            log.info("调用AI模型生成回答，问题长度: {}", question.length());
            String answer = chatModel.generate(prompt);
            
            long inputTokens = estimateTokens(prompt);
            long outputTokens = estimateTokens(answer);
            addTokenUsage(inputTokens + outputTokens, false);
            
            return answer;
        } catch (Exception e) {
            log.error("AI模型调用失败", e);
            return getDefaultAnswer(question);
        }
    }

    private boolean isTokenLimitExceeded() {
        if (tokenUsageManager != null && tokenUsageManager.isTokenLimitEnabled()) {
            return tokenUsageManager.isTokenLimitExceeded();
        }
        return false;
    }

    private void addTokenUsage(long tokens, boolean isLocal) {
        if (tokenUsageManager != null) {
            tokenUsageManager.addUsage(tokens, isLocal);
        }
    }

    private long estimateTokens(String text) {
        if (tokenUsageManager != null) {
            return tokenUsageManager.estimateTokens(text);
        }
        return Math.max(1, text.length() / 2);
    }

    private String buildPrompt(String question, String context, String knowledge) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的票务系统客服助手，名字叫小助手。\n");
        prompt.append("请用友好、专业的语气回答用户的问题。\n\n");
        
        if (knowledge != null && !knowledge.trim().isEmpty()) {
            prompt.append("参考知识库信息：\n");
            prompt.append(knowledge).append("\n\n");
        }
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("对话历史：\n");
            prompt.append(context).append("\n\n");
        }
        
        prompt.append("用户问题：").append(question).append("\n\n");
        prompt.append("请回答：");
        
        return prompt.toString();
    }

    private String getDefaultAnswer(String question) {
        if (question.contains("你好") || question.contains("您好")) {
            return "您好！我是智能客服小助手，很高兴为您服务！您可以问我关于购票、退票、场次查询等问题。";
        }
        return "抱歉，这个问题我暂时无法回答，请尝试转人工客服。";
    }

    public boolean isAvailable() {
        return chatModel != null && properties.isEnabled();
    }

    public boolean healthCheck() {
        if (!isAvailable()) {
            log.warn("AI模型未初始化或已禁用，健康检查失败");
            return false;
        }
        
        try {
            String result = chatModel.generate("你好");
            log.info("AI模型健康检查通过: {}", result != null && !result.isEmpty());
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            log.error("AI模型健康检查失败", e);
            return false;
        }
    }

    public boolean tryReconnect() {
        log.info("尝试重新连接AI模型...");
        try {
            init();
            if (isAvailable()) {
                log.info("AI模型重新连接成功");
                return true;
            } else {
                log.error("AI模型重新连接失败");
                return false;
            }
        } catch (Exception e) {
            log.error("AI模型重新连接异常", e);
            return false;
        }
    }
}
