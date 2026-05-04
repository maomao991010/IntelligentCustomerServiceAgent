package com.ticketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.dao.OrderDao;
import com.ticketing.entity.Order;
import com.ticketing.entity.Seat;
import com.ticketing.producer.OrderMessageProducer;
import com.ticketing.dao.UserDao;
import com.ticketing.entity.User;
import com.ticketing.service.OrderService;
import com.ticketing.service.SeatLockService;
import com.ticketing.service.SeatService;
import com.ticketing.utils.RedisUtil;
import com.ticketing.utils.RedisDistributedLock;
import com.ticketing.vo.CreateOrderVo;
import com.ticketing.vo.OrderMessageVo;
import com.ticketing.vo.PageResultVo;
import com.ticketing.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * 订单服务实现类
 * <p>
 * 实现订单相关的核心业务逻辑，包括创建订单、获取订单详情、获取订单列表、支付订单、取消订单和处理支付回调等功能
 * </p>
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

    @Autowired
    private SeatLockService seatLockService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UserDao userDao;

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Value("${ticketing.seat-lock-timeout}")
    private long seatLockTimeout;

    /**
     * 创建订单
     * <p>
     * 根据提供的订单创建信息，从Redis获取锁定信息，生成订单号，创建订单对象并保存到数据库，
     * 然后发送订单创建消息到RabbitMQ
     * </p>
     * @param createOrderVo 订单创建信息，包含锁定订单ID、支付方式等
     * @return 响应对象，包含创建结果和订单信息
     */
    @Override
    public ResponseVo createOrder(CreateOrderVo createOrderVo) {
        String lockOrderId = createOrderVo.getLockOrderId();
        String paymentMethod = createOrderVo.getPaymentMethod();

        try {
            // 从Redis获取锁定信息
            Map<String, Object> lockInfo = (Map<String, Object>) redisUtil.get("lock_order_" + lockOrderId);
            if (lockInfo == null) {
                return ResponseVo.error(400, "锁定订单不存在或已过期");
            }

            // 生成订单号
            String orderId = "ORDER_" + UUID.randomUUID().toString().replaceAll("-", "");

            // 创建订单对象
            Order order = new Order();
            Long userId = Long.parseLong(lockInfo.get("userId").toString());
            order.setOrderId(orderId);
            order.setLockOrderId(lockOrderId);
            order.setUserId(userId);
            
            // 获取用户信息，设置userPhone
            User user = userDao.selectById(userId);
            if (user != null) {
                order.setUserPhone(user.getPhone());
            } else {
                order.setUserPhone(""); // 兜底处理
            }
            
            order.setActivityId(1L); // 简化处理，实际应该从锁定信息或其他地方获取
            order.setActivityName("周杰伦2026世界巡回演唱会"); // 简化处理
            order.setSessionId(Long.parseLong(lockInfo.get("sessionId").toString()));
            order.setSessionDate("2026-06-15"); // 简化处理
            order.setSessionTime("19:30"); // 简化处理
            order.setVenue("国家体育场"); // 简化处理
            order.setSeatInfo(lockInfo.get("seatIds").toString()); // 简化处理
            order.setTotalPrice(createOrderVo.getTotalPrice());
            order.setPaymentMethod(paymentMethod);
            order.setPaymentStatus("PENDING");
            order.setOrderStatus("PENDING_PAYMENT");
            order.setCreateTime(LocalDateTime.now());
            order.setExpireTime(LocalDateTime.now().plusSeconds(seatLockTimeout));

            // 保存订单到数据库
            orderDao.insert(order);

            // 发送订单创建消息到RabbitMQ
            OrderMessageVo orderMessageVo = new OrderMessageVo();
            orderMessageVo.setOrderId(orderId);
            orderMessageVo.setLockOrderId(lockOrderId);
            orderMessageVo.setUserId(order.getUserId().toString());
            orderMessageVo.setTotalPrice(order.getTotalPrice());
            orderMessageProducer.sendOrderCreateMessage(orderMessageVo);
            
            // 发送延迟消息，用于15分钟后检查订单是否支付
            // 15分钟 = 900000毫秒
            orderMessageProducer.sendDelayMessage(orderMessageVo, 900000);

            // 构建响应
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId);
            data.put("expireTime", seatLockTimeout);
            data.put("totalPrice", order.getTotalPrice());

            return ResponseVo.success(data);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return ResponseVo.error(500, "创建订单失败，请重试");
        }
    }

    /**
     * 获取订单详情
     * <p>
     * 根据订单ID从数据库获取订单的详细信息
     * </p>
     * @param orderId 订单ID
     * @return 响应对象，包含订单详情
     */
    @Override
    public ResponseVo getOrderDetail(String orderId) {
        try {
            Order order = orderDao.selectByOrderId(orderId);
            if (order == null) {
                return ResponseVo.error(400, "订单不存在");
            }
            return ResponseVo.success(order);
        } catch (Exception e) {
            log.error("获取订单详情失败", e);
            return ResponseVo.error(500, "获取订单详情失败");
        }
    }

    /**
     * 获取订单列表
     * <p>
     * 根据用户ID从数据库获取该用户的所有订单列表
     * </p>
     * @param userId 用户ID
     * @return 响应对象，包含订单列表
     */
    @Override
    public ResponseVo getOrderList(Long userId) {
        try {
            return ResponseVo.success(orderDao.selectByUserId(userId));
        } catch (Exception e) {
            log.error("获取订单列表失败", e);
            return ResponseVo.error(500, "获取订单列表失败");
        }
    }
    
    /**
     * 分页获取订单列表
     * <p>
     * 根据用户ID分页获取订单列表，支持关键词搜索
     * </p>
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param keyword 搜索关键词
     * @return 响应对象，包含分页订单列表
     */
    @Override
    public ResponseVo getOrderPage(Long userId, int pageNum, int pageSize, String keyword) {
        try {
            Page<Order> page = new Page<>(pageNum, pageSize);
            IPage<Order> orderPage = orderDao.selectOrderPageWithSearch(page, userId, keyword);
            
            PageResultVo<Order> pageResult = new PageResultVo<>();
            pageResult.setRecords(orderPage.getRecords());
            pageResult.setTotal(orderPage.getTotal());
            pageResult.setPageNum((int) orderPage.getCurrent());
            pageResult.setPageSize((int) orderPage.getSize());
            pageResult.setPages((int) orderPage.getPages());
            
            return ResponseVo.success(pageResult);
        } catch (Exception e) {
            log.error("获取订单分页列表失败", e);
            return ResponseVo.error(500, "获取订单列表失败");
        }
    }

    /**
     * 支付订单
     * <p>
     * 根据订单ID和支付方式支付订单，更新订单状态，更新座位状态为已售出，并发送订单支付消息到RabbitMQ
     * </p>
     * @param orderId 订单ID
     * @param paymentMethod 支付方式
     * @return 响应对象，包含支付结果
     */
    @Override
    public ResponseVo payOrder(String orderId, String paymentMethod) {
        String lockKey = "pay_order_" + orderId;
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 使用分布式锁保证原子性
            if (!redisDistributedLock.tryLock(lockKey, lockValue, 3, 100)) {
                return ResponseVo.error(400, "系统繁忙，请稍后再试");
            }
            
            Order order = orderDao.selectByOrderId(orderId);
            if (order == null) {
                return ResponseVo.error(400, "订单不存在");
            }
            if (!"PENDING_PAYMENT".equals(order.getOrderStatus())) {
                return ResponseVo.error(400, "订单状态不正确");
            }

            java.util.List<Long> seatIds = null;
            
            // 从Redis获取锁定信息
            Map<String, Object> lockInfo = (Map<String, Object>) redisUtil.get("lock_order_" + order.getLockOrderId());
            if (lockInfo != null) {
                seatIds = (java.util.List<Long>) lockInfo.get("seatIds");
                log.info("从Redis获取到座位信息: orderId={}, seatIds={}", orderId, seatIds);
            } else {
                log.warn("Redis中无锁定信息，尝试从座位表检查: orderId={}, lockOrderId={}", orderId, order.getLockOrderId());
                
                // Redis中没有锁定信息，需要检查座位的实际状态
                // 从订单的seatInfo字段解析座位ID（如果有的话）
                if (order.getSeatInfo() != null && !order.getSeatInfo().isEmpty()) {
                    try {
                        // 尝试解析seatInfo（假设存储的是JSON数组格式）
                        ObjectMapper objectMapper = new ObjectMapper();
                        seatIds = objectMapper.readValue(order.getSeatInfo(), 
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Long>>() {});
                        log.info("从订单seatInfo解析到座位信息: orderId={}, seatIds={}", orderId, seatIds);
                    } catch (Exception e) {
                        log.warn("解析seatInfo失败: {}", e.getMessage());
                    }
                }
                
                if (seatIds == null || seatIds.isEmpty()) {
                    return ResponseVo.error(400, "座位信息不存在或已过期，请重新下单");
                }
                
                // 检查座位状态：确保座位要么是锁定给当前用户，要么是可用的
                boolean seatsAvailable = true;
                for (Long seatId : seatIds) {
                    Seat seat = seatService.getSeatById(seatId);
                    if (seat == null) {
                        seatsAvailable = false;
                        log.error("座位不存在: seatId={}", seatId);
                        break;
                    }
                    
                    String seatStatus = seat.getStatus();
                    String lockUserId = seat.getLockUserId();
                    
                    log.info("检查座位状态: seatId={}, status={}, lockUserId={}, orderUserId={}", 
                        seatId, seatStatus, lockUserId, order.getUserId());
                    
                    // 检查座位是否有效
                    if ("SOLD".equals(seatStatus)) {
                        // 座位已售出，无法支付
                        seatsAvailable = false;
                        log.error("座位已售出: seatId={}", seatId);
                        break;
                    }
                    
                    if ("LOCKED".equals(seatStatus)) {
                        // 座位是锁定状态，检查是否锁定给当前用户
                        if (lockUserId != null && !lockUserId.equals(order.getUserId().toString())) {
                            seatsAvailable = false;
                            log.error("座位已被其他用户锁定: seatId={}, lockUserId={}", seatId, lockUserId);
                            break;
                        }
                    }
                    
                    // AVAILABLE状态也可以，只要没被其他人锁定就行
                }
                
                if (!seatsAvailable) {
                    return ResponseVo.error(400, "座位已被其他用户锁定或售出，请重新下单");
                }
            }

            // 更新座位状态为已售出
            seatService.updateSeatStatusBatch(seatIds, "SOLD", order.getUserId().toString());

            // 更新订单状态
            order.setPaymentMethod(paymentMethod);
            order.setPaymentStatus("SUCCESS");
            order.setOrderStatus("PAID");
            order.setPayTime(LocalDateTime.now());
            orderDao.updateById(order);

            // 删除Redis中的锁定信息
            redisUtil.delete("lock_order_" + order.getLockOrderId());
            redisUtil.delete("user_lock_" + order.getUserId() + "_" + order.getLockOrderId());

            // 发送订单支付消息到RabbitMQ
            OrderMessageVo orderMessageVo = new OrderMessageVo();
            orderMessageVo.setOrderId(orderId);
            orderMessageVo.setUserId(order.getUserId().toString());
            orderMessageVo.setTotalPrice(order.getTotalPrice());
            orderMessageProducer.sendOrderPayMessage(orderMessageVo);

            log.info("订单支付成功: orderId={}, seatIds={}", orderId, seatIds);
            return ResponseVo.success(null);
        } catch (Exception e) {
            log.error("支付订单失败", e);
            return ResponseVo.error(500, "支付订单失败，请重试");
        } finally {
            // 释放分布式锁
            redisDistributedLock.unlock(lockKey, lockValue);
        }
    }

    /**
     * 取消订单
     * <p>
     * 根据订单ID取消订单，更新订单状态，释放座位，并发送订单取消消息到RabbitMQ
     * </p>
     * @param orderId 订单ID
     * @return 响应对象，包含取消结果
     */
    @Override
    public ResponseVo cancelOrder(String orderId) {
        return cancelOrder(orderId, "用户主动取消");
    }

    @Override
    public ResponseVo cancelOrder(String orderId, String cancelReason) {
        try {
            Order order = orderDao.selectByOrderId(orderId);
            if (order == null) {
                return ResponseVo.error(400, "订单不存在");
            }

            if ("CANCELLED".equals(order.getOrderStatus())) {
                return ResponseVo.error(400, "订单已取消");
            }
            if ("REFUNDED".equals(order.getOrderStatus())) {
                return ResponseVo.error(400, "订单已退款");
            }

            if ("PENDING_PAYMENT".equals(order.getOrderStatus())) {
                if (order.getLockOrderId() != null) {
                    seatLockService.releaseSeats(order.getLockOrderId());
                    log.info("释放订单座位成功: 锁定订单ID={}", order.getLockOrderId());
                }
                order.setOrderStatus("CANCELLED");
                order.setPaymentStatus("CANCELLED");
            } else if ("PAID".equals(order.getOrderStatus())) {
                order.setOrderStatus("CANCELLED");
                order.setRefundStatus("REFUNDING");
                releaseSeatsForCancelledOrder(order);
            } else {
                return ResponseVo.error(400, "当前订单状态不允许取消");
            }

            order.setCancelReason(cancelReason);
            order.setCancelTime(LocalDateTime.now());
            orderDao.updateById(order);

            OrderMessageVo orderMessageVo = new OrderMessageVo();
            orderMessageVo.setOrderId(orderId);
            orderMessageVo.setLockOrderId(order.getLockOrderId());
            orderMessageVo.setUserId(order.getUserId().toString());
            orderMessageProducer.sendOrderCancelMessage(orderMessageVo);

            return ResponseVo.success(null);
        } catch (Exception e) {
            log.error("取消订单失败", e);
            return ResponseVo.error(500, "取消订单失败，请重试");
        }
    }

    /**
     * 处理支付回调
     * <p>
     * 根据支付平台的回调信息，更新订单的支付状态和订单状态
     * </p>
     * @param orderId 订单ID
     * @param paymentStatus 支付状态
     * @param paymentAmount 支付金额
     * @param paymentTime 支付时间
     */
    @Override
    public void processPaymentCallback(String orderId, String paymentStatus, String paymentAmount, String paymentTime) {
        String lockKey = "payment_callback_" + orderId;
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 使用分布式锁保证原子性，防止与超时释放并发冲突
            if (redisDistributedLock.tryLock(lockKey, lockValue, 3, 100)) {
                log.info("获取支付回调处理锁成功: {}", orderId);
                
                Order order = orderDao.selectByOrderId(orderId);
                if (order == null) {
                    log.error("订单不存在: {}", orderId);
                    return;
                }

                // 检查订单状态，防止重复处理
                if (!"PENDING_PAYMENT".equals(order.getOrderStatus())) {
                    log.info("订单状态已变更，跳过支付回调处理: {}", orderId);
                    return;
                }

                if ("SUCCESS".equals(paymentStatus)) {
                    order.setPaymentStatus("SUCCESS");
                    order.setOrderStatus("PAID");
                    order.setPayTime(LocalDateTime.parse(paymentTime));
                    log.info("支付成功，更新订单状态为已支付: {}", orderId);
                } else {
                    order.setPaymentStatus("FAILED");
                    order.setOrderStatus("PAYMENT_FAILED");
                    log.info("支付失败，更新订单状态为支付失败: {}", orderId);
                }

                orderDao.updateById(order);
                log.info("支付回调处理完成: {}", orderId);
            } else {
                log.warn("获取支付回调处理锁失败，跳过处理: {}", orderId);
            }
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
        } finally {
            // 释放分布式锁
            redisDistributedLock.unlock(lockKey, lockValue);
            log.info("释放支付回调处理锁: {}", orderId);
        }
    }

    /**
     * 根据订单ID获取订单
     * <p>
     * 从数据库根据订单ID获取订单对象
     * </p>
     * @param orderId 订单ID
     * @return 订单对象
     */
    @Override
    public Order getOrderByOrderId(String orderId) {
        return orderDao.selectByOrderId(orderId);
    }

    /**
     * 根据锁定订单ID获取订单
     * <p>
     * 从数据库根据锁定订单ID获取订单对象
     * </p>
     * @param lockOrderId 锁定订单ID
     * @return 订单对象
     */
    @Override
    public Order getOrderByLockOrderId(String lockOrderId) {
        return orderDao.selectByLockOrderId(lockOrderId);
    }

    private void releaseSeatsForCancelledOrder(Order order) {
        if (order.getSeatInfo() != null && !order.getSeatInfo().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                java.util.List<Long> seatIds = mapper.readValue(order.getSeatInfo(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Long>>() {});
                seatService.updateSeatStatusBatch(seatIds, "AVAILABLE", null);
                log.info("取消订单释放座位成功: orderId={}, seatIds={}", order.getOrderId(), seatIds);
            } catch (Exception e) {
                log.error("取消订单释放座位失败: orderId={}", order.getOrderId(), e);
            }
        }
    }
}
