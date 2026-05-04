package com.ticketing.service;

import com.ticketing.entity.Seat;
import com.ticketing.vo.ResponseVo;

import java.util.List;

public interface SeatService {
    ResponseVo getSeatMap(Long sessionId);
    List<Seat> getSeatsBySessionId(Long sessionId);
    void updateSeatStatus(Long seatId, String status);
    void updateSeatStatusBatch(List<Long> seatIds, String status, String userId);
    Seat getSeatById(Long seatId);
    ResponseVo generateSeats(Long sessionId, int totalSeats);
}
