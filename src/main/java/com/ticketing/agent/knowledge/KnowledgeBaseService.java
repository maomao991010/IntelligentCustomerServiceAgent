package com.ticketing.agent.knowledge;

import com.ticketing.agent.intent.Intent;
import com.ticketing.agent.vector.VectorStoreService;
import com.ticketing.dao.FaqDao;
import com.ticketing.entity.Faq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class KnowledgeBaseService {

    @Autowired
    private FaqDao faqDao;

    @Autowired(required = false)
    private VectorStoreService vectorStoreService;

    private static final double MATCH_THRESHOLD = 0.3;

    public Faq retrieve(String question, Intent intent) {
        log.debug("开始检索知识库: question={}, intent={}", question, intent);

        if (vectorStoreService != null && vectorStoreService.isAvailable()) {
            try {
                List<VectorStoreService.FaqMatch> matches = vectorStoreService.search(question, 3);
                if (matches != null && !matches.isEmpty()) {
                    VectorStoreService.FaqMatch bestMatch = matches.get(0);
                    log.info("向量搜索找到匹配FAQ: id={}, score={}", bestMatch.getFaqId(), bestMatch.getScore());
                    Faq faq = new Faq();
                    faq.setId(bestMatch.getFaqId());
                    faq.setQuestion(bestMatch.getQuestion());
                    faq.setAnswer(bestMatch.getAnswer());
                    faq.setCategory(bestMatch.getCategory());
                    return faq;
                }
            } catch (Exception e) {
                log.error("向量搜索失败，回退到关键词匹配", e);
            }
        }

        List<Faq> faqs;
        if (intent != null && intent != Intent.UNKNOWN) {
            String category = mapIntentToCategory(intent);
            faqs = faqDao.selectByCategory(category);
            if (faqs == null || faqs.isEmpty()) {
                faqs = faqDao.selectAllActive();
            }
        } else {
            faqs = faqDao.selectAllActive();
        }

        if (faqs == null || faqs.isEmpty()) {
            log.debug("知识库为空");
            return null;
        }

        Faq bestMatch = null;
        double bestScore = 0;

        for (Faq faq : faqs) {
            double score = calculateMatchScore(question, faq);
            log.debug("FAQ匹配: id={}, score={}, question={}", faq.getId(), score, faq.getQuestion());

            if (score > bestScore && score >= MATCH_THRESHOLD) {
                bestScore = score;
                bestMatch = faq;
            }
        }

        if (bestMatch != null) {
            log.info("找到匹配FAQ: id={}, score={}", bestMatch.getId(), bestScore);
        } else {
            log.debug("未找到匹配的FAQ");
        }

        return bestMatch;
    }

    private String mapIntentToCategory(Intent intent) {
        switch (intent) {
            case ARTIST_QUERY:
            case SESSION_QUERY:
                return "场次查询";
            case TICKET_PURCHASE:
                return "购票咨询";
            case TICKET_REFUND:
                return "退票咨询";
            case ACCOUNT_ISSUE:
                return "账户问题";
            case GENERAL_QUESTION:
            case UNKNOWN:
            default:
                return "其他";
        }
    }

    private double calculateMatchScore(String question, Faq faq) {
        String q = question.toLowerCase().trim();
        double score = 0;

        if (faq.getQuestion() != null) {
            String faqQuestion = faq.getQuestion().toLowerCase();
            if (q.equals(faqQuestion)) {
                return 1.0;
            }
            if (q.contains(faqQuestion) || faqQuestion.contains(q)) {
                score += 0.6;
            }
        }

        if (faq.getKeywords() != null && !faq.getKeywords().isEmpty()) {
            List<String> keywords = Arrays.asList(faq.getKeywords().toLowerCase().split(","));
            for (String keyword : keywords) {
                String kw = keyword.trim();
                if (!kw.isEmpty() && q.contains(kw)) {
                    score += 0.2;
                }
            }
        }

        return Math.min(score, 1.0);
    }

    public String getDefaultAnswer(Intent intent) {
        switch (intent) {
            case SESSION_QUERY:
                return "您好！关于场次查询，您可以在首页查看所有正在售票的演唱会。如需帮助，请告诉我具体想了解哪个演出。";
            case TICKET_PURCHASE:
                return "您好！购票相关问题，您可以查看首页的购票指南。如需进一步帮助，请详细描述您的问题。";
            case TICKET_REFUND:
                return "您好！退票相关问题，请查看购票须知中的退票政策。如有特殊情况，请联系人工客服。";
            case ACCOUNT_ISSUE:
                return "您好！账户相关问题，您可以在个人中心进行操作。如需帮助，请联系客服。";
            case GENERAL_QUESTION:
            case UNKNOWN:
            default:
                return "您好！我是智能客服小助手，很高兴为您服务！\n\n您可以问我：\n- 演唱会场次信息\n- 购票流程\n- 退票政策\n- 账户问题\n\n如需更多帮助，也可以联系人工客服。";
        }
    }

    public String getRelatedKnowledge(String question) {
        try {
            List<Faq> faqs = faqDao.selectAllActive();
            if (faqs == null || faqs.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Faq faq : faqs) {
                if (count >= 5) break;
                String faqQuestion = faq.getQuestion() != null ? faq.getQuestion().toLowerCase() : "";
                String q = question.toLowerCase();
                
                if (q.contains(faqQuestion) || faqQuestion.contains(q)) {
                    sb.append("问题：").append(faq.getQuestion()).append("\n");
                    sb.append("回答：").append(faq.getAnswer()).append("\n\n");
                    count++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取相关知识失败", e);
            return "";
        }
    }
}
