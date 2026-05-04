package com.ticketing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticketing.entity.Seat;

import java.util.List;

public interface SeatDao extends BaseMapper<Seat> {
    List<Seat> selectBySessionId(Long sessionId);
    List<Seat> selectBySessionIdAndStatus(Long sessionId, String status);
    void deleteBySessionId(Long sessionId);
}
