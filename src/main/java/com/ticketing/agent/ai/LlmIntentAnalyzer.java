package com.ticketing.agent.ai;

import com.ticketing.agent.intent.Intent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class LlmIntentAnalyzer {

    @Autowired(required = false)
    private AiModelService aiModelService;

    private static final Map<Intent, List<String>> INTENT_EXAMPLES = new HashMap<>();

    static {
        INTENT_EXAMPLES.put(Intent.ARTIST_QUERY, Arrays.asList(
            "周杰伦最近有什么演唱会",
            "有没有林俊杰的演出",
            "五月天演唱会在哪里",
            "邓紫棋有演出吗"
        ));
        INTENT_EXAMPLES.put(Intent.SESSION_QUERY, Arrays.asList(
            "这场演唱会什么时候",
            "在哪里举办",
            "场次信息",
            "演出时间地点"
        ));
        INTENT_EXAMPLES.put(Intent.TICKET_PURCHASE, Arrays.asList(
            "怎么买票",
            "如何购票",
            "选座",
            "支付流程"
        ));
        INTENT_EXAMPLES.put(Intent.TICKET_REFUND, Arrays.asList(
            "可以退票吗",
            "怎么退款",
            "取消订单"
        ));
        INTENT_EXAMPLES.put(Intent.ACCOUNT_ISSUE, Arrays.asList(
            "登录不了",
            "忘记密码",
            "注册账号"
        ));
        INTENT_EXAMPLES.put(Intent.GENERAL_QUESTION, Arrays.asList(
            "你好",
            "谢谢",
            "再见"
        ));
    }

    public Intent analyzeIntentWithLLM(String question) {
        if (aiModelService == null || !aiModelService.isAvailable()) {
            log.warn("AI模型不可用，无法进行LLM意图分析");
            return null;
        }

        try {
            String prompt = buildIntentAnalysisPrompt(question);
            String response = aiModelService.generateAnswer(question, "", prompt);
            
            Intent intent = parseIntentFromResponse(response);
            if (intent != null) {
                log.info("LLM意图分析成功: question={}, intent={}", question, intent);
                return intent;
            }
        } catch (Exception e) {
            log.error("LLM意图分析失败", e);
        }

        return null;
    }

    private String buildIntentAnalysisPrompt(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的意图识别助手。请分析用户的问题，判断属于以下哪个意图类别：\n\n");
        
        for (Intent intent : Intent.values()) {
            if (intent == Intent.UNKNOWN) continue;
            sb.append("- ").append(intent.name()).append(" (").append(intent.getDescription()).append(")\n");
            List<String> examples = INTENT_EXAMPLES.get(intent);
            if (examples != null && !examples.isEmpty()) {
                sb.append("  示例：").append(String.join("、", examples)).append("\n");
            }
        }
        
        sb.append("\n用户问题：").append(question).append("\n\n");
        sb.append("请只返回意图的英文名称（如：ARTIST_QUERY），不要返回其他内容。");
        
        return sb.toString();
    }

    private Intent parseIntentFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        String cleanResponse = response.trim().toUpperCase();
        
        for (Intent intent : Intent.values()) {
            if (cleanResponse.contains(intent.name())) {
                return intent;
            }
        }

        return null;
    }
}
