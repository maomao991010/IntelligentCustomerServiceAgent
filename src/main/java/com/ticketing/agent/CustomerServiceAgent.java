package com.ticketing.agent;

import com.ticketing.agent.ai.AiModelService;
import com.ticketing.agent.ai.LlmIntentAnalyzer;
import com.ticketing.agent.context.ChatContextManager;
import com.ticketing.agent.health.ServiceHealthManager;
import com.ticketing.agent.intent.Intent;
import com.ticketing.agent.intent.IntentRecognizer;
import com.ticketing.agent.knowledge.KnowledgeBaseService;
import com.ticketing.agent.tool.SessionQueryTool;
import com.ticketing.config.AiCustomerServiceProperties;
import com.ticketing.dao.ChatHistoryDao;
import com.ticketing.entity.ChatHistory;
import com.ticketing.entity.Faq;
import com.ticketing.entity.Session;
import com.ticketing.vo.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class CustomerServiceAgent {

    @Autowired
    private IntentRecognizer intentRecognizer;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ChatContextManager contextManager;

    @Autowired
    private ChatHistoryDao chatHistoryDao;

    @Autowired(required = false)
    private AiModelService aiModelService;

    @Autowired
    private AiCustomerServiceProperties properties;

    @Autowired
    private SessionQueryTool sessionQueryTool;

    @Autowired(required = false)
    private LlmIntentAnalyzer llmIntentAnalyzer;

    @Autowired
    private ServiceHealthManager serviceHealthManager;

    public ChatResponse chat(String sessionId, Long userId, String question) {
        log.info("收到用户消息: sessionId={}, userId={}, question={}", sessionId, userId, question);
        log.info("服务健康状态: {}", serviceHealthManager.getHealthSummary());

        ChatContextManager.ChatSession session = contextManager.getOrCreateSession(sessionId, userId);

        Intent intent = intentRecognizer.recognize(question);
        log.info("第一轮意图识别: {}", intent);

        boolean llmIntentUsed = false;

        if (intent == Intent.UNKNOWN && llmIntentAnalyzer != null) {
            log.info("关键词未识别，尝试使用LLM分析意图...");
            Intent llmIntent = llmIntentAnalyzer.analyzeIntentWithLLM(question);
            if (llmIntent != null && llmIntent != Intent.UNKNOWN) {
                intent = llmIntent;
                llmIntentUsed = true;
                log.info("LLM意图识别成功: {}", intent);
            } else {
                log.warn("LLM意图分析失败，保持UNKNOWN");
            }
        }

        String answer;
        Long faqId = null;
        boolean needTransfer = false;
        boolean aiGenerated = false;
        boolean isRichText = false;
        double confidence = 0.3;

        boolean useFaqRetrieval = serviceHealthManager.isFaqRetrievalAvailable();
        boolean useAiModel = serviceHealthManager.isAiModelAvailable() && aiModelService != null 
                && properties.getKnowledgeBase().isEnableAiFallback()
                && !serviceHealthManager.isTokenLimitExceeded();

        if (intent == Intent.ARTIST_QUERY) {
            answer = handleArtistQuery(question);
            confidence = llmIntentUsed ? 0.8 : 0.9;
            log.info("使用艺人演唱会查询工具回答, LLM协助={}", llmIntentUsed);
        } else if (intent == Intent.SESSION_QUERY) {
            Faq matchedFaq = null;
            if (useFaqRetrieval) {
                matchedFaq = knowledgeBaseService.retrieve(question, intent);
            }
            if (matchedFaq != null) {
                answer = matchedFaq.getAnswer();
                faqId = matchedFaq.getId();
                isRichText = true;
                confidence = llmIntentUsed ? 0.7 : 0.8;
                log.info("使用知识库回答: faqId={}", faqId);
            } else {
                answer = handleArtistQuery(question);
                confidence = llmIntentUsed ? 0.75 : 0.85;
                log.info("场次查询未匹配知识库，尝试查询场次信息");
            }
        } else if (intent != Intent.UNKNOWN) {
            Faq matchedFaq = null;
            if (useFaqRetrieval) {
                matchedFaq = knowledgeBaseService.retrieve(question, intent);
            }
            if (matchedFaq != null) {
                answer = matchedFaq.getAnswer();
                faqId = matchedFaq.getId();
                isRichText = true;
                confidence = llmIntentUsed ? 0.7 : 0.8;
                log.info("使用知识库回答: faqId={}", faqId);
            } else if (useAiModel) {
                String context = session.getHistoryText();
                String knowledge = useFaqRetrieval ? knowledgeBaseService.getRelatedKnowledge(question) : "";
                answer = aiModelService.generateAnswer(question, context, knowledge);
                aiGenerated = true;
                confidence = llmIntentUsed ? 0.6 : 0.7;
                log.info("使用AI模型生成回答");
                needTransfer = answer.contains("无法回答") || answer.contains("转人工");
            } else {
                answer = knowledgeBaseService.getDefaultAnswer(intent);
                needTransfer = false;
                log.info("使用默认回答");
            }
        } else {
            if (useAiModel) {
                String context = session.getHistoryText();
                String knowledge = useFaqRetrieval ? knowledgeBaseService.getRelatedKnowledge(question) : "";
                answer = aiModelService.generateAnswer(question, context, knowledge);
                aiGenerated = true;
                confidence = 0.5;
                log.info("所有意图识别失败，直接用AI生成回答");
                needTransfer = answer.contains("无法回答") || answer.contains("转人工");
            } else {
                answer = knowledgeBaseService.getDefaultAnswer(Intent.UNKNOWN);
                needTransfer = true;
                log.info("所有方式均失败，建议转人工");
            }
        }

        saveChatHistory(sessionId, userId, question, answer, intent, faqId, needTransfer, confidence, aiGenerated, llmIntentUsed);

        session.addMessage(question, answer);
        contextManager.saveSession(session);

        return ChatResponse.builder()
                .answer(answer)
                .intent(intent.getDescription())
                .needTransfer(needTransfer)
                .faqId(faqId)
                .aiGenerated(aiGenerated)
                .isRichText(isRichText)
                .build();
    }

    private String handleArtistQuery(String question) {
        log.info("处理艺人/演唱会查询，原始问题: {}", question);
        List<Session> sessions = sessionQueryTool.searchSessions(question);
        return sessionQueryTool.formatSessions(sessions);
    }

    private void saveChatHistory(String sessionId, Long userId, String question, 
                                   String answer, Intent intent, Long faqId, boolean needTransfer, 
                                   double confidence, boolean aiGenerated, boolean llmIntentUsed) {
        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setAnswer(answer);
            history.setIntent(intent.getDescription() + (llmIntentUsed ? " (LLM)" : ""));
            history.setConfidence(BigDecimal.valueOf(confidence));
            history.setFaqId(faqId);
            history.setIsTransfer(needTransfer ? 1 : 0);
            history.setAiGenerated(aiGenerated ? 1 : 0);
            history.setCreateTime(LocalDateTime.now());

            chatHistoryDao.insert(history);
            log.debug("对话历史已保存");
        } catch (Exception e) {
            log.error("保存对话历史失败", e);
        }
    }
}
