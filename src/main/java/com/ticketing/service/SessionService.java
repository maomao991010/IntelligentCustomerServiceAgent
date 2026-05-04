package com.ticketing.service;

import com.ticketing.entity.Session;
import com.ticketing.vo.PageResultVo;
import com.ticketing.vo.ResponseVo;

import java.util.List;

public interface SessionService {
    ResponseVo getSessionList(Long activityId);
    ResponseVo getSessionDetail(Long sessionId);
    Session getSessionById(Long sessionId);
    void updateRemainingSeats(Long sessionId, Integer count);
    ResponseVo addSession(Session session);
    ResponseVo updateSession(Session session);
    ResponseVo deleteSession(Long sessionId);
    
    ResponseVo getSessionPage(int pageNum, int pageSize, String keyword);
}
