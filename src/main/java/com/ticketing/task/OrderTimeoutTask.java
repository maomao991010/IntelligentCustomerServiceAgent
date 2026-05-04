package com.ticketing.task;

import com.ticketing.dao.OrderDao;
import com.ticketing.entity.Order;
import com.ticketing.producer.OrderMessageProducer;
import com.ticketing.service.SeatLockService;
import com.ticketing.utils.RedisDistributedLock;
import com.ticketing.vo.OrderMessageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class OrderTimeoutTask {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private SeatLockService seatLockService;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    // 每5分钟执行一次
    @Scheduled(cron = "0 */5 * * * ?")
    public void checkTimeoutOrders() {
        log.info("开始检查超时未支付订单");
        
        try {
            // 查询所有状态为待支付且已过期的订单
            List<Order> orders = orderDao.selectByStatus("PENDING_PAYMENT");
            LocalDateTime now = LocalDateTime.now();
            
            for (Order order : orders) {
                if (order.getExpireTime() != null && order.getExpireTime().isBefore(now)) {
                    // 处理超时订单
                    processTimeoutOrder(order);
                }
            }
            
            log.info("检查超时未支付订单完成");
        } catch (Exception e) {
            log.error("检查超时未支付订单失败", e);
        }
    }

    private void processTimeoutOrder(Order order) {
        String lockKey = "timeout_order_" + order.getOrderId();
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 使用分布式锁保证原子性
            if (redisDistributedLock.tryLock(lockKey, lockValue, 3, 100)) {
                log.info("获取订单超时处理锁成功: {}", order.getOrderId());
                
                // 再次检查订单状态，防止并发修改
                Order currentOrder = orderDao.selectByOrderId(order.getOrderId());
                if (currentOrder != null && "PENDING_PAYMENT".equals(currentOrder.getOrderStatus())) {
                    // 检查订单是否真的过期
                    if (currentOrder.getExpireTime() != null && currentOrder.getExpireTime().isBefore(LocalDateTime.now())) {
                        // 更新订单状态为已取消
                        currentOrder.setOrderStatus("CANCELLED");
                        orderDao.updateById(currentOrder);
                        log.info("更新订单状态为已取消: {}", currentOrder.getOrderId());
                        
                        // 释放锁定的座位
                        if (currentOrder.getLockOrderId() != null) {
                            seatLockService.releaseSeats(currentOrder.getLockOrderId());
                            log.info("释放锁定的座位成功: 锁定订单ID={}", currentOrder.getLockOrderId());
                        }
                        
                        // 发送订单取消消息到RabbitMQ
                        OrderMessageVo orderMessageVo = new OrderMessageVo();
                        orderMessageVo.setOrderId(currentOrder.getOrderId());
                        orderMessageVo.setLockOrderId(currentOrder.getLockOrderId());
                        orderMessageVo.setUserId(currentOrder.getUserId().toString());
                        orderMessageProducer.sendOrderCancelMessage(orderMessageVo);
                        log.info("发送订单取消消息成功: {}", currentOrder.getOrderId());
                        
                        log.info("处理超时订单成功: {}", currentOrder.getOrderId());
                    } else {
                        log.info("订单未过期，跳过处理: {}", currentOrder.getOrderId());
                    }
                } else {
                    log.info("订单状态已变更，跳过处理: {}", order.getOrderId());
                }
            } else {
                log.warn("获取订单超时处理锁失败，跳过处理: {}", order.getOrderId());
            }
        } catch (Exception e) {
            log.error("处理超时订单失败: {}", order.getOrderId(), e);
        } finally {
            // 释放分布式锁
            redisDistributedLock.unlock(lockKey, lockValue);
            log.info("释放订单超时处理锁: {}", order.getOrderId());
        }
    }
}
