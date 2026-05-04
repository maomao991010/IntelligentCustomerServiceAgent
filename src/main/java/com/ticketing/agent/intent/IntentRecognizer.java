package com.ticketing.agent.intent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class IntentRecognizer {

    private static final Map<Intent, List<String>> KEYWORD_MAPPING = new HashMap<>();

    static {
        KEYWORD_MAPPING.put(Intent.TICKET_PURCHASE, Arrays.asList(
            "购票", "买票", "选座", "座位", "支付", "怎么买", "如何买", "付款", "怎么买票", "购买"
        ));
        KEYWORD_MAPPING.put(Intent.TICKET_REFUND, Arrays.asList(
            "退票", "退款", "取消", "退", "能退吗", "可以退吗"
        ));
        KEYWORD_MAPPING.put(Intent.ACCOUNT_ISSUE, Arrays.asList(
            "登录", "注册", "密码", "账号", "账户", "忘记", "找回", "修改"
        ));
        KEYWORD_MAPPING.put(Intent.GENERAL_QUESTION, Arrays.asList(
            "你好", "您好", "hello", "hi", "谢谢", "感谢", "thanks", "再见", "拜拜", "bye"
        ));
        KEYWORD_MAPPING.put(Intent.SESSION_QUERY, Arrays.asList(
            "场次", "时间", "地点", "什么时候", "哪里", "地址"
        ));
    }

    public Intent recognize(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Intent.UNKNOWN;
        }

        String q = question.toLowerCase().trim();
        log.debug("开始意图识别: question={}", q);

        for (Map.Entry<Intent, List<String>> entry : KEYWORD_MAPPING.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (q.contains(keyword)) {
                    log.debug("识别到意图: intent={}, keyword={}", entry.getKey(), keyword);
                    return entry.getKey();
                }
            }
        }

        if (isArtistQuery(q)) {
            log.debug("识别到艺人演唱会查询意图");
            return Intent.ARTIST_QUERY;
        }

        log.debug("未识别到明确意图，返回UNKNOWN");
        return Intent.UNKNOWN;
    }

    private boolean isArtistQuery(String q) {
        boolean hasCoreKeyword = false;
        for (String kw : Arrays.asList("演唱会", "演出", "艺人", "歌手")) {
            if (q.contains(kw)) {
                hasCoreKeyword = true;
                break;
            }
        }

        if (!hasCoreKeyword) {
            return false;
        }

        for (String kw : Arrays.asList("的演唱会", "的演出", "有什么", "有哪些", "最近", "有没有", "哪里有", "谁的", "哪些艺人", "哪些歌手")) {
            if (q.contains(kw)) {
                return true;
            }
        }

        return false;
    }
}
