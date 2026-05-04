package com.ticketing.controller;

import com.ticketing.annotation.OperationLog;
import com.ticketing.annotation.OperationLog.OperType;
import com.ticketing.annotation.RequirePermission;
import com.ticketing.service.RefundService;
import com.ticketing.utils.JwtUtil;
import com.ticketing.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/refunds")
public class RefundController {

    @Autowired
    private RefundService refundService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/apply")
    @OperationLog(value = "申请退款", module = "订单", type = OperType.CREATE)
    public ResponseVo applyRefund(@RequestHeader("Authorization") String token,
                                  @RequestBody Map<String, String> params) {
        Long userId = getUserIdFromToken(token);
        String orderId = params.get("orderId");
        String refundReason = params.get("refundReason");
        String refundType = params.getOrDefault("refundType", "ORDER_CANCEL");
        return refundService.applyRefund(orderId, userId, refundReason, refundType);
    }

    @PostMapping("/audit/{id}")
    @RequirePermission("refund:audit")
    @OperationLog(value = "审核退款", module = "订单", type = OperType.UPDATE)
    public ResponseVo auditRefund(@PathVariable Long id,
                                  @RequestBody Map<String, String> params,
                                  @RequestHeader("Authorization") String token) {
        String status = params.get("status");
        String auditRemark = params.get("auditRemark");
        Long auditUserId = getUserIdFromToken(token);
        return refundService.auditRefund(id, status, auditRemark, auditUserId);
    }

    @GetMapping("/order/{orderId}")
    public ResponseVo getRefundByOrderId(@PathVariable String orderId) {
        return refundService.getRefundByOrderId(orderId);
    }

    @GetMapping("/my")
    public ResponseVo getMyRefunds(@RequestHeader("Authorization") String token) {
        Long userId = getUserIdFromToken(token);
        return refundService.getRefundList(userId);
    }

    @GetMapping("/page")
    @RequirePermission("refund:view")
    public ResponseVo getRefundPage(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String status) {
        return refundService.getRefundListAll(pageNum, pageSize, status);
    }

    private Long getUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }
}
