package com.ticketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.dao.ChatStatisticsDao;
import com.ticketing.dao.ChatTransferDao;
import com.ticketing.entity.ChatStatistics;
import com.ticketing.entity.ChatTransfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
public class ChatAdminService {

    @Autowired
    private ChatTransferDao chatTransferDao;

    @Autowired
    private ChatStatisticsDao chatStatisticsDao;

    public ChatTransfer createTransfer(String sessionId, Long userId, String userName, String userPhone, String question) {
        ChatTransfer transfer = new ChatTransfer();
        transfer.setSessionId(sessionId);
        transfer.setUserId(userId);
        transfer.setUserName(userName);
        transfer.setUserPhone(userPhone);
        transfer.setQuestion(question);
        transfer.setStatus("PENDING");
        transfer.setTransferTime(LocalDateTime.now());
        chatTransferDao.insert(transfer);
        log.info("创建人工转接: sessionId={}, userId={}", sessionId, userId);
        return transfer;
    }

    public Page<ChatTransfer> getTransferPage(int pageNum, int pageSize, String status) {
        LambdaQueryWrapper<ChatTransfer> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ChatTransfer::getStatus, status);
        }
        wrapper.orderByDesc(ChatTransfer::getCreateTime);
        return chatTransferDao.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public ChatTransfer handleTransfer(Long id, Long agentId, String agentName) {
        ChatTransfer transfer = chatTransferDao.selectById(id);
        if (transfer != null) {
            transfer.setStatus("PROCESSING");
            transfer.setAgentId(agentId);
            transfer.setAgentName(agentName);
            transfer.setHandleTime(LocalDateTime.now());
            chatTransferDao.updateById(transfer);
            log.info("处理人工转接: id={}, agentId={}", id, agentId);
        }
        return transfer;
    }

    public ChatTransfer closeTransfer(Long id, String remark) {
        ChatTransfer transfer = chatTransferDao.selectById(id);
        if (transfer != null) {
            transfer.setStatus("CLOSED");
            transfer.setRemark(remark);
            transfer.setCloseTime(LocalDateTime.now());
            chatTransferDao.updateById(transfer);
            log.info("关闭人工转接: id={}", id);
        }
        return transfer;
    }

    public ChatStatistics getTodayStatistics() {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<ChatStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatStatistics::getStatDate, today);
        return chatStatisticsDao.selectOne(wrapper);
    }

    public Page<ChatStatistics> getStatisticsPage(int pageNum, int pageSize) {
        LambdaQueryWrapper<ChatStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChatStatistics::getStatDate);
        return chatStatisticsDao.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
