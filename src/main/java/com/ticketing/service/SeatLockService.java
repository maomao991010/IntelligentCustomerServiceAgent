package com.ticketing.service;

import com.ticketing.vo.LockSeatVo;
import com.ticketing.vo.ResponseVo;

public interface SeatLockService {
    ResponseVo lockSeats(LockSeatVo lockSeatVo);
    ResponseVo releaseSeats(String lockOrderId);
    void releaseExpiredLocks();
    boolean checkRateLimit(String userId);
}
