package com.ticketing.service;

import com.ticketing.entity.Order;
import com.ticketing.vo.CreateOrderVo;
import com.ticketing.vo.PageResultVo;
import com.ticketing.vo.ResponseVo;

public interface OrderService {
    ResponseVo createOrder(CreateOrderVo createOrderVo);
    ResponseVo getOrderDetail(String orderId);
    ResponseVo getOrderList(Long userId);
    ResponseVo getOrderPage(Long userId, int pageNum, int pageSize, String keyword);
    ResponseVo payOrder(String orderId, String paymentMethod);
    ResponseVo cancelOrder(String orderId);
    ResponseVo cancelOrder(String orderId, String cancelReason);
    void processPaymentCallback(String orderId, String paymentStatus, String paymentAmount, String paymentTime);
    Order getOrderByOrderId(String orderId);
    Order getOrderByLockOrderId(String lockOrderId);
}
