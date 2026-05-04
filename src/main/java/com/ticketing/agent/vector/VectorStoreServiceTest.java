package com.ticketing.agent.vector;

import com.ticketing.config.AiCustomerServiceProperties;
import com.ticketing.entity.Faq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * VectorStoreService 测试类
 * 用于测试 FAQ 嵌入和搜索向量数据库的全过程
 */
@Slf4j
@Component
public class VectorStoreServiceTest implements CommandLineRunner {

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private AiCustomerServiceProperties properties;

    /**
     * 测试向量化过程
     */
    public void testEmbeddingProcess() {
        log.info("=== 开始测试向量化过程 ===");
        
        if (!vectorStoreService.isAvailable()) {
            log.error("向量存储不可用，跳过测试");
            return;
        }

        try {
            // 创建测试 FAQ
            Faq testFaq = new Faq();
            testFaq.setId(999L);
            testFaq.setQuestion("如何退票？");
            testFaq.setAnswer("您可以通过以下方式退票：1. 登录官网 2. 联系客服 3. 到售票点办理");
            testFaq.setCategory("退票政策");
            testFaq.setKeywords("退票,退款,取消");

            log.info("测试 FAQ 信息:");
            log.info("- ID: {}", testFaq.getId());
            log.info("- 问题: {}", testFaq.getQuestion());
            log.info("- 回答: {}", testFaq.getAnswer());
            log.info("- 分类: {}", testFaq.getCategory());

            // 测试向量化
            log.info("开始向量化测试...");
            vectorStoreService.addFaq(testFaq);
            log.info("向量化测试完成");

        } catch (Exception e) {
            log.error("向量化测试失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 测试搜索过程
     */
    public void testSearchProcess() {
        log.info("=== 开始测试搜索过程 ===");
        
        if (!vectorStoreService.isAvailable()) {
            log.error("向量存储不可用，跳过测试");
            return;
        }

        try {
            // 测试不同查询
            List<String> testQueries = Arrays.asList(
                    "票能退吗",
                    "如何取消订单",
                    "退票流程",
                    "退款政策"
            );

            for (String query : testQueries) {
                log.info("测试查询: {}", query);
                List<VectorStoreService.FaqMatch> results = vectorStoreService.search(query, 5);
                log.info("查询结果数量: {}", results.size());
                
                for (int i = 0; i < results.size(); i++) {
                    VectorStoreService.FaqMatch match = results.get(i);
                    log.info("  结果 {}: FAQ ID={}, 问题={}, 相似度={}", 
                            i + 1, match.getFaqId(), match.getQuestion(), match.getScore());
                }
                log.info("---");
            }

        } catch (Exception e) {
            log.error("搜索测试失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 测试向量维度检查
     */
    public void testVectorDimensions() {
        log.info("=== 开始测试向量维度检查 ===");
        
        if (!vectorStoreService.isAvailable()) {
            log.error("向量存储不可用，跳过测试");
            return;
        }

        try {
            vectorStoreService.checkVectorDimensions();
        } catch (Exception e) {
            log.error("向量维度检查失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 完整测试流程
     */
    public void testCompleteWorkflow() {
        log.info("=== 开始完整测试流程 ===");
        
        // 1. 检查服务可用性
        if (!vectorStoreService.isAvailable()) {
            log.error("向量存储不可用，跳过完整测试");
            return;
        }

        log.info("向量存储服务可用");
        log.info("配置信息:");
        log.info("- 向量存储提供商: {}", properties.getVectorStore().getProvider());
        log.info("- 嵌入模型提供商: {}", properties.getVectorStore().getEmbeddingProvider());
        log.info("- 嵌入模型名称: {}", properties.getVectorStore().getEmbeddingModelName());

        // 2. 检查向量维度
        log.info("\n1. 检查向量维度...");
        testVectorDimensions();

        // 3. 测试向量化
        log.info("\n2. 测试向量化过程...");
        testEmbeddingProcess();

        // 4. 测试搜索
        log.info("\n3. 测试搜索过程...");
        testSearchProcess();

        log.info("=== 完整测试流程完成 ===");
    }

    /**
     * 测试批量添加 FAQ
     */
    public void testBatchAddFaqs() {
        log.info("=== 开始测试批量添加 FAQ ===");
        
        if (!vectorStoreService.isAvailable()) {
            log.error("向量存储不可用，跳过测试");
            return;
        }

        try {
            // 创建多个测试 FAQ
            List<Faq> testFaqs = Arrays.asList(
                    createFaq(1001L, "演唱会门票可以退吗？", "根据退票政策，演唱会门票在演出前48小时可以退", "退票政策", "退票,演唱会"),
                    createFaq(1002L, "如何购买门票？", "您可以通过官网、APP或售票点购买门票", "购票流程", "购买,购票"),
                    createFaq(1003L, "门票可以转让吗？", "门票支持转让，但需在演出前24小时完成", "转让政策", "转让,转赠"),
                    createFaq(1004L, "儿童需要购票吗？", "身高1.2米以下儿童免票，1.2米以上需购票", "儿童政策", "儿童,免票")
            );

            log.info("批量添加 {} 个 FAQ", testFaqs.size());
            vectorStoreService.addFaqs(testFaqs);
            log.info("批量添加完成");

        } catch (Exception e) {
            log.error("批量添加测试失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建测试 FAQ 对象
     */
    private Faq createFaq(Long id, String question, String answer, String category, String keywords) {
        Faq faq = new Faq();
        faq.setId(id);
        faq.setQuestion(question);
        faq.setAnswer(answer);
        faq.setCategory(category);
        faq.setKeywords(keywords);
        return faq;
    }

    /**
     * Spring Boot 启动时自动运行测试
     */
    @Override
    public void run(String... args) throws Exception {
        // 检查是否有测试参数
        boolean enableTest = false;
        for (String arg : args) {
            if ("--test-vector".equals(arg)) {
                enableTest = true;
                break;
            }
        }
        
        if (enableTest) {
            log.info("=== 启动 VectorStoreService 自动测试 ===");
            testCompleteWorkflow();
            log.info("=== VectorStoreService 自动测试完成 ===");
        }
    }

    /**
     * 主方法 - 可以直接运行测试
     */
    public static void main(String[] args) {
        log.info("启动 VectorStoreService 测试...");
        
        // 这里可以添加 Spring Boot 应用启动逻辑
        // 或者通过 Spring Boot Test 运行
        
        log.info("请通过以下方式运行测试:");
        log.info("1. 使用 HTTP 接口: curl -X POST http://localhost:8080/api/v1/test/vector/full-test");
        log.info("2. 启动应用时添加参数: --test-vector");
    }
}