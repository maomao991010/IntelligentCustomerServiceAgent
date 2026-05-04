package com.ticketing.agent.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticketing.dao.ChatHistoryDao;
import com.ticketing.dao.ChatStatisticsDao;
import com.ticketing.dao.ChatTransferDao;
import com.ticketing.entity.ChatHistory;
import com.ticketing.entity.ChatStatistics;
import com.ticketing.entity.ChatTransfer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChatStatisticsService {

    @Autowired
    private ChatHistoryDao chatHistoryDao;

    @Autowired
    private ChatTransferDao chatTransferDao;

    @Autowired
    private ChatStatisticsDao chatStatisticsDao;

    @Data
    public static class DailyStatistics {
        private LocalDate date;
        private int totalChats;
        private int aiHandled;
        private int transferred;
        private double transferRate;
        private Map<String, Integer> intentDistribution;
    }

    @Data
    public static class OverviewStatistics {
        private int totalChats;
        private int aiHandled;
        private int transferred;
        private double transferRate;
        private int todayChats;
        private int pendingTransfers;
    }

    public OverviewStatistics getOverviewStatistics() {
        OverviewStatistics overview = new OverviewStatistics();

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        LambdaQueryWrapper<ChatHistory> totalWrapper = new LambdaQueryWrapper<>();
        List<ChatHistory> allChats = chatHistoryDao.selectList(totalWrapper);
        overview.setTotalChats(allChats.size());

        int aiHandled = 0;
        int transferred = 0;
        for (ChatHistory chat : allChats) {
            if (chat.getIsTransfer() != null && chat.getIsTransfer() == 1) {
                transferred++;
            } else {
                aiHandled++;
            }
        }
        overview.setAiHandled(aiHandled);
        overview.setTransferred(transferred);
        overview.setTransferRate(allChats.size() > 0 ? (double) transferred / allChats.size() * 100 : 0);

        LambdaQueryWrapper<ChatHistory> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(ChatHistory::getCreateTime, startOfDay)
                   .le(ChatHistory::getCreateTime, endOfDay);
        overview.setTodayChats(chatHistoryDao.selectCount(todayWrapper).intValue());

        LambdaQueryWrapper<ChatTransfer> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(ChatTransfer::getStatus, "PENDING");
        overview.setPendingTransfers(chatTransferDao.selectCount(pendingWrapper).intValue());

        return overview;
    }

    public DailyStatistics getDailyStatistics(LocalDate date) {
        DailyStatistics daily = new DailyStatistics();
        daily.setDate(date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ChatHistory::getCreateTime, startOfDay)
               .le(ChatHistory::getCreateTime, endOfDay);
        List<ChatHistory> chats = chatHistoryDao.selectList(wrapper);

        daily.setTotalChats(chats.size());

        int aiHandled = 0;
        int transferred = 0;
        Map<String, Integer> intentDistribution = new HashMap<>();

        for (ChatHistory chat : chats) {
            if (chat.getIsTransfer() != null && chat.getIsTransfer() == 1) {
                transferred++;
            } else {
                aiHandled++;
            }

            String intent = chat.getIntent() != null ? chat.getIntent() : "未知";
            intentDistribution.put(intent, intentDistribution.getOrDefault(intent, 0) + 1);
        }

        daily.setAiHandled(aiHandled);
        daily.setTransferred(transferred);
        daily.setTransferRate(chats.size() > 0 ? (double) transferred / chats.size() * 100 : 0);
        daily.setIntentDistribution(intentDistribution);

        return daily;
    }

    public List<ChatHistory> getRecentChats(int limit) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChatHistory::getCreateTime)
               .last("LIMIT " + limit);
        return chatHistoryDao.selectList(wrapper);
    }

    public ChatStatistics saveOrUpdateDailyStatistics(LocalDate date) {
        DailyStatistics daily = getDailyStatistics(date);

        LambdaQueryWrapper<ChatStatistics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatStatistics::getStatDate, date);
        ChatStatistics statistics = chatStatisticsDao.selectOne(wrapper);

        if (statistics == null) {
            statistics = new ChatStatistics();
            statistics.setStatDate(date);
            statistics.setTotalChats(daily.getTotalChats());
            statistics.setAiHandled(daily.getAiHandled());
            statistics.setTransferred(daily.getTransferred());
            statistics.setCreateTime(LocalDateTime.now());
            statistics.setUpdateTime(LocalDateTime.now());
            chatStatisticsDao.insert(statistics);
        } else {
            statistics.setTotalChats(daily.getTotalChats());
            statistics.setAiHandled(daily.getAiHandled());
            statistics.setTransferred(daily.getTransferred());
            statistics.setUpdateTime(LocalDateTime.now());
            chatStatisticsDao.updateById(statistics);
        }

        return statistics;
    }
}
