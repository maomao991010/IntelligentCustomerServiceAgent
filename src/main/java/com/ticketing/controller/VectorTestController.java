package com.ticketing.controller;

import com.ticketing.agent.vector.VectorStoreService;
import com.ticketing.entity.Faq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量存储测试控制器
 * 提供 HTTP 接口用于测试向量化过程
 */
@Slf4j
@RestController
@RequestMapping("/test/vector")
public class VectorTestController {

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * 检查向量存储服务状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("available", vectorStoreService.isAvailable());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 检查向量维度
     */
    @GetMapping("/dimensions")
    public Map<String, Object> checkDimensions() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            vectorStoreService.checkVectorDimensions();
            result.put("success", true);
            result.put("message", "向量维度检查完成，请查看日志");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 测试向量化单个 FAQ
     */
    @PostMapping("/embed")
    public Map<String, Object> testEmbedding(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Faq faq = new Faq();
            faq.setId(999L);
            faq.setQuestion(request.getOrDefault("question", "测试问题"));
            faq.setAnswer(request.getOrDefault("answer", "测试回答"));
            faq.setCategory(request.getOrDefault("category", "测试分类"));
            faq.setKeywords(request.getOrDefault("keywords", "测试关键词"));

            vectorStoreService.addFaq(faq);
            
            result.put("success", true);
            result.put("message", "FAQ 向量化测试完成，请查看日志");
            
            // 使用 HashMap 替代 Map.of，因为 Java 8 不支持 Map.of 多个参数
            Map<String, Object> faqInfo = new HashMap<>();
            faqInfo.put("id", faq.getId());
            faqInfo.put("question", faq.getQuestion());
            faqInfo.put("answer", faq.getAnswer());
            result.put("faq", faqInfo);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 测试向量搜索
     */
    @GetMapping("/search")
    public Map<String, Object> testSearch(@RequestParam String query) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<VectorStoreService.FaqMatch> matches = vectorStoreService.search(query, 5);
            
            result.put("success", true);
            result.put("query", query);
            result.put("matchesCount", matches.size());
            result.put("matches", matches);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 重新初始化向量库
     */
    @PostMapping("/reinitialize")
    public Map<String, Object> reinitialize() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            vectorStoreService.reinitializeVectorStore();
            
            result.put("success", true);
            result.put("message", "向量库重新初始化指导已生成，请查看日志");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * 完整测试流程
     */
    @PostMapping("/full-test")
    public Map<String, Object> fullTest() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("=== 开始完整向量测试流程 ===");
            
            // 1. 检查状态
            boolean available = vectorStoreService.isAvailable();
            result.put("available", available);
            
            if (!available) {
                result.put("success", false);
                result.put("error", "向量存储服务不可用");
                return result;
            }
            
            // 2. 检查维度
            vectorStoreService.checkVectorDimensions();
            
            // 3. 测试向量化
            Faq testFaq = new Faq();
            testFaq.setId(888L);
            testFaq.setQuestion("测试问题：如何退票？");
            testFaq.setAnswer("测试回答：您可以通过官网退票");
            testFaq.setCategory("测试分类");
            vectorStoreService.addFaq(testFaq);
            
            // 4. 测试搜索
            List<VectorStoreService.FaqMatch> matches = vectorStoreService.search("退票", 5);
            
            result.put("success", true);
            result.put("message", "完整测试流程完成");
            result.put("embeddingTest", "完成");
            result.put("searchTest", "完成");
            result.put("searchResults", matches.size());
            
            log.info("=== 完整向量测试流程完成 ===");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            log.error("完整测试流程失败: {}", e.getMessage(), e);
        }
        
        return result;
    }
}