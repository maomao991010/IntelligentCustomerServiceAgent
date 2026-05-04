package com.ticketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.agent.CustomerServiceAgent;
import com.ticketing.agent.vector.VectorStoreService;
import com.ticketing.dao.ChatHistoryDao;
import com.ticketing.dao.FaqDao;
import com.ticketing.entity.ChatHistory;
import com.ticketing.entity.Faq;
import com.ticketing.vo.ChatRequest;
import com.ticketing.vo.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
@Slf4j
public class ChatService {

    @Autowired
    private CustomerServiceAgent customerServiceAgent;

    @Autowired
    private FaqDao faqDao;

    @Autowired
    private ChatHistoryDao chatHistoryDao;

    @Autowired(required = false)
    private VectorStoreService vectorStoreService;

    @PostConstruct
    public void init() {
        if (vectorStoreService != null && vectorStoreService.isAvailable()) {
            try {
                List<Faq> allFaqs = faqDao.selectAllActive();
                if (allFaqs != null && !allFaqs.isEmpty()) {
                    vectorStoreService.addFaqs(allFaqs);
                    log.info("初始化向量存储完成，加载 FAQ 数量: {}", allFaqs.size());
                }
            } catch (Exception e) {
                log.error("初始化向量存储失败", e);
            }
        }
    }

    public ChatResponse chat(ChatRequest request) {
        return customerServiceAgent.chat(
            request.getSessionId(),
            request.getUserId(),
            request.getQuestion()
        );
    }

    public List<ChatHistory> getHistory(String sessionId) {
        return chatHistoryDao.selectBySessionId(sessionId);
    }

    public List<ChatHistory> getUserHistory(Long userId) {
        return chatHistoryDao.selectByUserId(userId);
    }

    public Page<Faq> getFaqPage(int pageNum, int pageSize, String category) {
        Page<Faq> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Faq> wrapper = new LambdaQueryWrapper<>();
        
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Faq::getCategory, category);
        }
        
        wrapper.orderByAsc(Faq::getSortOrder).orderByDesc(Faq::getCreateTime);
        return faqDao.selectPage(page, wrapper);
    }

    public Faq getFaqById(Long id) {
        return faqDao.selectById(id);
    }

    public Faq createFaq(Faq faq) {
        if (faq.getCreateTime() == null) {
            faq.setCreateTime(java.time.LocalDateTime.now());
        }
        if (faq.getUpdateTime() == null) {
            faq.setUpdateTime(java.time.LocalDateTime.now());
        }
        if (faq.getSortOrder() == null) {
            faq.setSortOrder(0);
        }
        if (faq.getStatus() == null) {
            faq.setStatus("ACTIVE");
        }
        faqDao.insert(faq);
        log.info("创建FAQ: id={}, question={}", faq.getId(), faq.getQuestion());
        
        if (vectorStoreService != null && vectorStoreService.isAvailable()) {
            try {
                vectorStoreService.addFaq(faq);
                log.info("FAQ向量添加成功: id={}", faq.getId());
            } catch (Exception e) {
                log.error("FAQ向量添加失败: id={}", faq.getId(), e);
            }
        }
        return faq;
    }

    public Faq updateFaq(Faq faq) {
        faq.setUpdateTime(java.time.LocalDateTime.now());
        faqDao.updateById(faq);
        log.info("更新FAQ: id={}", faq.getId());
        
        if (vectorStoreService != null && vectorStoreService.isAvailable()) {
            try {
                Faq updatedFaq = faqDao.selectById(faq.getId());
                if (updatedFaq != null) {
                    vectorStoreService.updateFaq(updatedFaq);
                    log.info("FAQ向量更新成功: id={}", faq.getId());
                }
            } catch (Exception e) {
                log.error("FAQ向量更新失败: id={}", faq.getId(), e);
            }
        }
        return faqDao.selectById(faq.getId());
    }

    public boolean deleteFaq(Long id) {
        Faq faq = faqDao.selectById(id);
        if (faq == null) {
            return false;
        }
        
        faqDao.deleteById(id);
        log.info("删除FAQ: id={}", id);
        
        if (vectorStoreService != null && vectorStoreService.isAvailable()) {
            try {
                vectorStoreService.deleteFaq(id);
                log.info("FAQ向量删除成功: id={}", id);
            } catch (Exception e) {
                log.error("FAQ向量删除失败: id={}", id, e);
            }
        }
        return true;
    }

    public List<Faq> getAllActiveFaqs() {
        return faqDao.selectAllActive();
    }
}
