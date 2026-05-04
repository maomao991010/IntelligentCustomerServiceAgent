package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.dao.OrderDao;
import com.ticketing.dao.RefundRecordDao;
import com.ticketing.entity.Order;
import com.ticketing.entity.RefundRecord;
import com.ticketing.service.RefundService;
import com.ticketing.service.SeatService;
import com.ticketing.vo.PageResultVo;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    @Autowired
    private RefundRecordDao refundRecordDao;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private SeatService seatService;

    @Override
    @Transactional
    public ResponseVo applyRefund(String orderId, Long userId, String refundReason, String refundType) {
        Order order = orderDao.selectByOrderId(orderId);
        if (order == null) {
            return ResponseVo.error(400, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseVo.error(403, "无权操作此订单");
        }
        if (!"PAID".equals(order.getOrderStatus())) {
            return ResponseVo.error(400, "只有已支付的订单才能申请退款");
        }
        if ("REFUNDING".equals(order.getRefundStatus()) || "REFUNDED".equals(order.getRefundStatus())) {
            return ResponseVo.error(400, "订单已在退款流程中或已退款");
        }

        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getOrderId, orderId)
               .ne(RefundRecord::getStatus, "REJECTED");
        Long count = refundRecordDao.selectCount(wrapper);
        if (count > 0) {
            return ResponseVo.error(400, "该订单已有进行中的退款申请");
        }

        RefundRecord record = new RefundRecord();
        record.setRefundNo("REF_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16));
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setRefundAmount(new BigDecimal(order.getTotalPrice()));
        record.setRefundReason(refundReason);
        record.setRefundType(refundType);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        refundRecordDao.insert(record);

        order.setRefundStatus("REFUNDING");
        orderDao.updateById(order);

        return ResponseVo.success(record);
    }

    @Override
    @Transactional
    public ResponseVo auditRefund(Long refundId, String status, String auditRemark, Long auditUserId) {
        RefundRecord record = refundRecordDao.selectById(refundId);
        if (record == null) {
            return ResponseVo.error(400, "退款记录不存在");
        }
        if (!"PENDING".equals(record.getStatus())) {
            return ResponseVo.error(400, "该退款申请已处理");
        }

        record.setStatus(status);
        record.setAuditRemark(auditRemark);
        record.setAuditUserId(auditUserId);
        record.setAuditTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        Order order = orderDao.selectByOrderId(record.getOrderId());
        if (order == null) {
            return ResponseVo.error(400, "关联订单不存在");
        }

        if ("APPROVED".equals(status)) {
            processRefund(record, order);
        } else if ("REJECTED".equals(status)) {
            order.setRefundStatus("REJECTED");
            orderDao.updateById(order);
        }

        refundRecordDao.updateById(record);
        return ResponseVo.success(record);
    }

    private void processRefund(RefundRecord record, Order order) {
        order.setRefundStatus("REFUNDED");
        order.setRefundTime(LocalDateTime.now());
        order.setRefundAmount(order.getTotalPrice());
        order.setOrderStatus("REFUNDED");
        orderDao.updateById(order);

        record.setRefundTime(LocalDateTime.now());

        releaseSeatsForOrder(order);

        log.info("退款处理完成: orderId={}, refundAmount={}", order.getOrderId(), order.getTotalPrice());
    }

    private void releaseSeatsForOrder(Order order) {
        if (order.getSeatInfo() != null && !order.getSeatInfo().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Long> seatIds = mapper.readValue(order.getSeatInfo(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
                seatService.updateSeatStatusBatch(seatIds, "AVAILABLE", null);
                log.info("退款释放座位成功: orderId={}, seatIds={}", order.getOrderId(), seatIds);
            } catch (Exception e) {
                log.error("退款释放座位失败: orderId={}", order.getOrderId(), e);
            }
        }
    }

    @Override
    public ResponseVo getRefundByOrderId(String orderId) {
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getOrderId, orderId)
               .orderByDesc(RefundRecord::getCreateTime);
        return ResponseVo.success(refundRecordDao.selectList(wrapper));
    }

    @Override
    public ResponseVo getRefundList(Long userId) {
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getUserId, userId)
               .orderByDesc(RefundRecord::getCreateTime);
        return ResponseVo.success(refundRecordDao.selectList(wrapper));
    }

    @Override
    public ResponseVo getRefundListAll(int pageNum, int pageSize, String status) {
        Page<RefundRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RefundRecord::getStatus, status);
        }
        wrapper.orderByDesc(RefundRecord::getCreateTime);
        IPage<RefundRecord> result = refundRecordDao.selectPage(page, wrapper);

        PageResultVo<RefundRecord> pageResult = new PageResultVo<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum((int) result.getCurrent());
        pageResult.setPageSize((int) result.getSize());
        pageResult.setPages((int) result.getPages());
        return ResponseVo.success(pageResult);
    }

    @Override
    public RefundRecord getByRefundNo(String refundNo) {
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundRecord::getRefundNo, refundNo);
        return refundRecordDao.selectOne(wrapper);
    }
}
