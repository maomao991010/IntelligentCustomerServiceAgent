package com.ticketing.service;

import com.ticketing.entity.RefundRecord;
import com.ticketing.vo.ResponseVo;
import java.util.List;

public interface RefundService {
    ResponseVo applyRefund(String orderId, Long userId, String refundReason, String refundType);
    ResponseVo auditRefund(Long refundId, String status, String auditRemark, Long auditUserId);
    ResponseVo getRefundByOrderId(String orderId);
    ResponseVo getRefundList(Long userId);
    ResponseVo getRefundListAll(int pageNum, int pageSize, String status);
    RefundRecord getByRefundNo(String refundNo);
}
