package com.ticketing.controller;

import com.ticketing.agent.statistics.ChatStatisticsService;
import com.ticketing.agent.statistics.ChatStatisticsService.DailyStatistics;
import com.ticketing.agent.statistics.ChatStatisticsService.OverviewStatistics;
import com.ticketing.agent.transfer.ChatTransferService;
import com.ticketing.entity.ChatHistory;
import com.ticketing.entity.ChatTransfer;
import com.ticketing.vo.ResponseVo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/chat/admin")
@Slf4j
public class ChatAdminController {

    @Autowired
    private ChatTransferService chatTransferService;

    @Autowired
    private ChatStatisticsService chatStatisticsService;

    @PostMapping("/transfer")
    public ResponseVo createTransfer(@RequestBody TransferRequest request) {
        try {
            ChatTransfer transfer = chatTransferService.createTransfer(
                request.getSessionId(),
                request.getUserId(),
                request.getUserName(),
                request.getUserPhone(),
                request.getQuestion()
            );
            return ResponseVo.success(transfer);
        } catch (Exception e) {
            log.error("创建转接失败", e);
            return ResponseVo.error("创建转接失败");
        }
    }

    @GetMapping("/transfer/pending")
    public ResponseVo getPendingTransfers() {
        try {
            List<ChatTransfer> transfers = chatTransferService.getPendingTransfers();
            return ResponseVo.success(transfers);
        } catch (Exception e) {
            log.error("获取待处理转接失败", e);
            return ResponseVo.error("获取待处理转接失败");
        }
    }

    @GetMapping("/transfer/session/{sessionId}")
    public ResponseVo getTransfersBySessionId(@PathVariable String sessionId) {
        try {
            List<ChatTransfer> transfers = chatTransferService.getTransfersBySessionId(sessionId);
            return ResponseVo.success(transfers);
        } catch (Exception e) {
            log.error("获取会话转接记录失败", e);
            return ResponseVo.error("获取会话转接记录失败");
        }
    }

    @PutMapping("/transfer/{id}/status")
    public ResponseVo updateTransferStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        try {
            boolean success = chatTransferService.updateTransferStatus(
                id,
                request.getStatus(),
                request.getAgentId(),
                request.getAgentName(),
                request.getRemark()
            );
            if (success) {
                return ResponseVo.success("状态更新成功");
            } else {
                return ResponseVo.error("转接记录不存在");
            }
        } catch (Exception e) {
            log.error("更新转接状态失败", e);
            return ResponseVo.error("更新转接状态失败");
        }
    }

    @GetMapping("/statistics/overview")
    public ResponseVo getOverviewStatistics() {
        try {
            OverviewStatistics overview = chatStatisticsService.getOverviewStatistics();
            return ResponseVo.success(overview);
        } catch (Exception e) {
            log.error("获取概览统计失败", e);
            return ResponseVo.error("获取概览统计失败");
        }
    }

    @GetMapping("/statistics/daily")
    public ResponseVo getDailyStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            if (date == null) {
                date = LocalDate.now();
            }
            DailyStatistics daily = chatStatisticsService.getDailyStatistics(date);
            return ResponseVo.success(daily);
        } catch (Exception e) {
            log.error("获取日报统计失败", e);
            return ResponseVo.error("获取日报统计失败");
        }
    }

    @GetMapping("/history/recent")
    public ResponseVo getRecentChats(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<ChatHistory> chats = chatStatisticsService.getRecentChats(limit);
            return ResponseVo.success(chats);
        } catch (Exception e) {
            log.error("获取最近对话失败", e);
            return ResponseVo.error("获取最近对话失败");
        }
    }

    @Data
    public static class TransferRequest {
        private String sessionId;
        private Long userId;
        private String userName;
        private String userPhone;
        private String question;
    }

    @Data
    public static class UpdateStatusRequest {
        private String status;
        private Long agentId;
        private String agentName;
        private String remark;
    }
}
