package com.ticketing.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.config.RabbitMQConfig;
import com.ticketing.service.OrderService;
import com.ticketing.service.SeatLockService;
import com.ticketing.vo.OrderMessageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class OrderMessageConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeatLockService seatLockService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String MESSAGE_PROCESSED_KEY = "message_processed_";
    private static final long MESSAGE_DEDUP_EXPIRE = 24 * 60 * 60;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATE_QUEUE)
    public void handleOrderCreateMessage(OrderMessageVo orderMessageVo) {
        if (!processMessage(orderMessageVo)) {
            log.warn("消息已处理或处理失败，跳过: {}", orderMessageVo);
            return;
        }

        try {
            log.info("收到订单创建消息: {}", orderMessageVo.getOrderId());

            log.info("处理订单创建详情: 订单ID={}, 用户ID={}, 总金额={}",
                    orderMessageVo.getOrderId(), orderMessageVo.getUserId(), orderMessageVo.getTotalPrice());

            log.info("订单创建消息处理完成: {}", orderMessageVo.getOrderId());

        } catch (Exception e) {
            log.error("处理订单创建消息失败", e);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DEAD_LETTER_EXCHANGE,
                    RabbitMQConfig.DEAD_LETTER_ROUTING_KEY, orderMessageVo);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAY_QUEUE)
    public void handleOrderPayMessage(OrderMessageVo orderMessageVo) {
        if (!processMessage(orderMessageVo)) {
            log.warn("消息已处理或处理失败，跳过: {}", orderMessageVo);
            return;
        }

        try {
            log.info("收到订单支付消息: {}", orderMessageVo.getOrderId());

            log.info("处理订单支付详情: 订单ID={}, 用户ID={}, 支付金额={}",
                    orderMessageVo.getOrderId(), orderMessageVo.getUserId(), orderMessageVo.getTotalPrice());

            log.info("订单支付消息处理完成: {}", orderMessageVo.getOrderId());

        } catch (Exception e) {
            log.error("处理订单支付消息失败", e);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DEAD_LETTER_EXCHANGE,
                    RabbitMQConfig.DEAD_LETTER_ROUTING_KEY, orderMessageVo);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderCancelMessage(OrderMessageVo orderMessageVo) {
        if (!processMessage(orderMessageVo)) {
            log.warn("消息已处理或处理失败，跳过: {}", orderMessageVo);
            return;
        }

        try {
            log.info("收到订单取消消息: {}", orderMessageVo.getOrderId());

            log.info("处理订单取消详情: 订单ID={}, 用户ID={}, 锁定订单ID={}",
                    orderMessageVo.getOrderId(), orderMessageVo.getUserId(), orderMessageVo.getLockOrderId());

            if (orderMessageVo.getLockOrderId() != null) {
                seatLockService.releaseSeats(orderMessageVo.getLockOrderId());
                log.info("座位释放成功: 锁定订单ID={}", orderMessageVo.getLockOrderId());
            }

            log.info("订单取消消息处理完成: {}", orderMessageVo.getOrderId());

        } catch (Exception e) {
            log.error("处理订单取消消息失败", e);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DEAD_LETTER_EXCHANGE,
                    RabbitMQConfig.DEAD_LETTER_ROUTING_KEY, orderMessageVo);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.DEAD_LETTER_QUEUE)
    public void handleDeadLetterMessage(OrderMessageVo orderMessageVo) {
        try {
            log.warn("收到死信消息: {}", orderMessageVo.getOrderId());

            log.warn("死信消息详情: 订单ID={}, 用户ID={}, 锁定订单ID={}",
                    orderMessageVo.getOrderId(), orderMessageVo.getUserId(), orderMessageVo.getLockOrderId());

            try {
                com.ticketing.entity.Order order = orderService.getOrderByOrderId(orderMessageVo.getOrderId());
                if (order != null && "PENDING_PAYMENT".equals(order.getOrderStatus())) {
                    log.info("订单超时未支付，执行超时处理: {}", orderMessageVo.getOrderId());

                    if (orderMessageVo.getLockOrderId() != null) {
                        seatLockService.releaseSeats(orderMessageVo.getLockOrderId());
                        log.info("释放锁定的座位成功: 锁定订单ID={}", orderMessageVo.getLockOrderId());
                    }

                    orderService.cancelOrder(orderMessageVo.getOrderId());
                    log.info("订单取消成功: {}", orderMessageVo.getOrderId());
                } else if (order != null) {
                    log.info("订单状态已变更，跳过超时处理: {}，当前状态: {}", 
                            orderMessageVo.getOrderId(), order.getOrderStatus());
                } else {
                    log.warn("订单不存在，跳过超时处理: {}", orderMessageVo.getOrderId());
                }
            } catch (Exception e) {
                log.error("处理订单超时检查失败", e);
            }

            log.info("死信消息处理完成: {}", orderMessageVo.getOrderId());

        } catch (Exception e) {
            log.error("处理死信消息失败", e);
        }
    }

    private boolean processMessage(OrderMessageVo orderMessageVo) {
        try {
            String messageId = orderMessageVo.getMessageId();

            if (messageId == null || messageId.isEmpty()) {
                messageId = String.valueOf(orderMessageVo.hashCode());
            }

            String dedupKey = MESSAGE_PROCESSED_KEY + messageId;

            Boolean isProcessed = redisTemplate.hasKey(dedupKey);
            if (Boolean.TRUE.equals(isProcessed)) {
                log.warn("消息重复消费: messageId={}", messageId);
                return false;
            }

            redisTemplate.opsForValue().set(dedupKey, "1", MESSAGE_DEDUP_EXPIRE, TimeUnit.SECONDS);
            return true;

        } catch (Exception e) {
            log.error("消息去重处理失败", e);
            return true;
        }
    }
}
