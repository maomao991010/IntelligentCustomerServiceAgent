package com.ticketing.agent.transfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketing.dao.ChatTransferDao;
import com.ticketing.entity.ChatTransfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ChatTransferService {

    @Autowired
    private ChatTransferDao chatTransferDao;

    public ChatTransfer createTransfer(String sessionId, Long userId, String userName, String userPhone, String question) {
        log.info("创建人工客服转接: sessionId={}, userId={}", sessionId, userId);

        ChatTransfer transfer = new ChatTransfer();
        transfer.setSessionId(sessionId);
        transfer.setUserId(userId);
        transfer.setUserName(userName);
        transfer.setUserPhone(userPhone);
        transfer.setQuestion(question);
        transfer.setStatus("PENDING");
        transfer.setTransferTime(LocalDateTime.now());
        transfer.setCreateTime(LocalDateTime.now());
        transfer.setUpdateTime(LocalDateTime.now());

        chatTransferDao.insert(transfer);
        log.info("人工客服转接创建成功: id={}", transfer.getId());

        return transfer;
    }

    public ChatTransfer getTransferById(Long id) {
        return chatTransferDao.selectById(id);
    }

    public List<ChatTransfer> getPendingTransfers() {
        LambdaQueryWrapper<ChatTransfer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatTransfer::getStatus, "PENDING")
               .orderByDesc(ChatTransfer::getCreateTime);
        return chatTransferDao.selectList(wrapper);
    }

    public List<ChatTransfer> getTransfersBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatTransfer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatTransfer::getSessionId, sessionId)
               .orderByDesc(ChatTransfer::getCreateTime);
        return chatTransferDao.selectList(wrapper);
    }

    public boolean updateTransferStatus(Long id, String status, Long agentId, String agentName, String remark) {
        ChatTransfer transfer = chatTransferDao.selectById(id);
        if (transfer == null) {
            log.warn("转接记录不存在: id={}", id);
            return false;
        }

        transfer.setStatus(status);
        transfer.setAgentId(agentId);
        transfer.setAgentName(agentName);
        transfer.setRemark(remark);
        transfer.setUpdateTime(LocalDateTime.now());

        if ("HANDLING".equals(status)) {
            transfer.setHandleTime(LocalDateTime.now());
        } else if ("CLOSED".equals(status)) {
            transfer.setCloseTime(LocalDateTime.now());
        }

        chatTransferDao.updateById(transfer);
        log.info("转接状态更新成功: id={}, status={}", id, status);
        return true;
    }
}
