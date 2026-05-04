package com.ticketing.task;

import com.ticketing.service.SeatLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 座位锁定定时任务
 * <p>
 * 定期执行座位锁定相关的定时任务，包括释放过期锁定等功能
 * </p>
 */
@Component
@Slf4j
public class SeatLockTask {

    @Autowired
    private SeatLockService seatLockService;

    /**
     * 释放过期锁定任务
     * <p>
     * 每5分钟执行一次，释放所有过期30分钟未支付的座位锁定
     * </p>
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void releaseExpiredLocks() {
        log.info("开始释放过期座位锁定");
        seatLockService.releaseExpiredLocks();
        log.info("释放过期座位锁定完成");
    }
}