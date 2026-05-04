package com.ticketing.producer;

import com.ticketing.config.RabbitMQConfig;
import com.ticketing.vo.OrderMessageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单消息生产者
 * 负责将订单相关消息发送到 RabbitMQ，并处理消息确认与重试逻辑
 */
@Component
@Slf4j
public class OrderMessageProducer implements RabbitTemplate.ConfirmCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 用于缓存已发送但尚未收到确认的消息，key 为 messageId
     */
    private final ConcurrentHashMap<String, OrderMessageVo> pendingMessages = new ConcurrentHashMap<>();

    /**
     * 构造器：注入 RabbitTemplate 并设置消息确认回调
     *
     * @param rabbitTemplate Spring AMQP 的 RabbitTemplate 实例
     */
    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setConfirmCallback(this);
    }

    /**
     * 发送订单创建消息
     *
     * @param orderMessageVo 订单消息体
     */
    public void sendOrderCreateMessage(OrderMessageVo orderMessageVo) {
        sendMessage(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CREATE_ROUTING_KEY, orderMessageVo, "订单创建");
    }

    /**
     * 发送订单支付消息
     *
     * @param orderMessageVo 订单消息体
     */
    public void sendOrderPayMessage(OrderMessageVo orderMessageVo) {
        sendMessage(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_PAY_ROUTING_KEY, orderMessageVo, "订单支付");
    }

    /**
     * 发送订单取消消息
     *
     * @param orderMessageVo 订单消息体
     */
    public void sendOrderCancelMessage(OrderMessageVo orderMessageVo) {
        sendMessage(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CANCEL_ROUTING_KEY, orderMessageVo, "订单取消");
    }

    /**
     * 发送死信消息（消息消费失败后的兜底队列）
     *
     * @param orderMessageVo 订单消息体
     */
    public void sendDeadLetterMessage(OrderMessageVo orderMessageVo) {
        sendMessage(RabbitMQConfig.DEAD_LETTER_EXCHANGE, RabbitMQConfig.DEAD_LETTER_ROUTING_KEY, orderMessageVo, "死信");
    }

    /**
     * 发送延迟消息
     *
     * @param orderMessageVo 订单消息体
     * @param delayMillis    延迟时间（毫秒）
     */
    public void sendDelayMessage(OrderMessageVo orderMessageVo, long delayMillis) {
        sendMessage(RabbitMQConfig.DELAY_EXCHANGE, RabbitMQConfig.DELAY_ROUTING_KEY, orderMessageVo, "延迟消息", delayMillis);
    }

    /**
     * 内部通用发送方法（无延迟）
     *
     * @param exchange     交换机
     * @param routingKey   路由键
     * @param orderMessageVo 消息体
     * @param messageType  消息类型描述，用于日志
     */
    private void sendMessage(String exchange, String routingKey, OrderMessageVo orderMessageVo, String messageType) {
        sendMessage(exchange, routingKey, orderMessageVo, messageType, 0);
    }

    /**
     * 内部通用发送方法（支持延迟）
     *
     * @param exchange     交换机
     * @param routingKey   路由键
     * @param orderMessageVo 消息体
     * @param messageType  消息类型描述，用于日志
     * @param delayMillis  延迟时间（毫秒），0 表示不延迟
     */
    private void sendMessage(String exchange, String routingKey, OrderMessageVo orderMessageVo, String messageType, long delayMillis) {
        // 生成全局唯一消息 ID
        String messageId = UUID.randomUUID().toString();
        orderMessageVo.setMessageId(messageId);

        try {
            // 创建关联数据，用于消息确认
            CorrelationData correlationData = new CorrelationData(messageId);
            // 将待确认消息放入缓存
            pendingMessages.put(messageId, orderMessageVo);

            if (delayMillis > 0) {
                // 发送延迟消息：设置消息过期时间
                rabbitTemplate.convertAndSend(exchange, routingKey, orderMessageVo, messagePostProcessor -> {
                    messagePostProcessor.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    messagePostProcessor.getMessageProperties().setMessageId(messageId);
                    return messagePostProcessor;
                }, correlationData);
            } else {
                // 发送普通消息
                rabbitTemplate.convertAndSend(exchange, routingKey, orderMessageVo, correlationData);
            }

            log.info("发送{}消息成功: {}, 消息ID: {}", messageType, orderMessageVo.getOrderId(), messageId);
        } catch (Exception e) {
            log.error("发送{}消息失败: {}", messageType, orderMessageVo.getOrderId(), e);
            // 发送异常时移除缓存
            pendingMessages.remove(messageId);
        }
    }

    /**
     * RabbitMQ 消息确认回调
     * 当 Broker 返回 ack/nack 时触发
     *
     * @param correlationData 关联数据，包含消息 ID
     * @param ack             是否收到 ack
     * @param cause           nack 时的原因描述
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        String messageId = correlationData.getId();
        // 从缓存中移除并获取待确认消息
        OrderMessageVo orderMessageVo = pendingMessages.remove(messageId);

        if (ack) {
            log.info("消息确认成功: {}, 消息ID: {}", orderMessageVo != null ? orderMessageVo.getOrderId() : "未知", messageId);
        } else {
            log.error("消息确认失败: {}, 原因: {}, 消息ID: {}",
                    orderMessageVo != null ? orderMessageVo.getOrderId() : "未知", cause, messageId);

            // 收到 nack 时触发重试
            if (orderMessageVo != null) {
                retrySendMessage(orderMessageVo, 3);
            }
        }
    }

    /**
     * 消息重试发送
     * 根据消息类型路由到对应队列，最多重试 maxRetries 次
     *
     * @param orderMessageVo 待重试消息
     * @param maxRetries     最大重试次数
     */
    private void retrySendMessage(OrderMessageVo orderMessageVo, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                String exchange = RabbitMQConfig.ORDER_EXCHANGE;
                String routingKey = RabbitMQConfig.ORDER_CREATE_ROUTING_KEY;

                // 根据消息类型选择路由键
                if ("PAY".equals(orderMessageVo.getMessageType())) {
                    routingKey = RabbitMQConfig.ORDER_PAY_ROUTING_KEY;
                } else if ("CANCEL".equals(orderMessageVo.getMessageType())) {
                    routingKey = RabbitMQConfig.ORDER_CANCEL_ROUTING_KEY;
                }

                // 重新生成消息 ID 并发送
                String messageId = UUID.randomUUID().toString();
                orderMessageVo.setMessageId(messageId);

                CorrelationData correlationData = new CorrelationData(messageId);
                rabbitTemplate.convertAndSend(exchange, routingKey, orderMessageVo, correlationData);

                log.info("消息重试成功: {}, 消息ID: {}, 重试次数: {}", orderMessageVo.getOrderId(), messageId, i + 1);
                return; // 重试成功则退出
            } catch (Exception e) {
                log.error("消息重试失败: {}, 重试次数: {}", orderMessageVo.getOrderId(), i + 1, e);
            }
        }

        // 达到最大重试次数后记录错误日志
        log.error("消息重试达到最大次数，消息丢失: {}", orderMessageVo.getOrderId());
    }
}
