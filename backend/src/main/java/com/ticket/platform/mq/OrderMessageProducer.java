package com.ticket.platform.mq;

import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 下单消息发布器。
 * 使用 publisher confirm 等待 Broker 确认；确认超时、nack 或不可路由都视为发布失败，
 * 由 Outbox 后续重发。重复发送由消费者幂等处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final AsyncOrderProperties properties;

    /**
     * 发布一条下单消息。
     *
     * @return Broker 是否确认接收；false 时调用方应安排重试
     */
    public boolean publish(String requestId) {
        String correlationId = requestId + ":" + UUID.randomUUID().toString().replace("-", "");
        CorrelationData correlationData = new CorrelationData(correlationId);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.ORDER_EXCHANGE,
                    RabbitMqConfig.ORDER_ROUTING_KEY,
                    new OrderCreateMessage(requestId),
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlationData);

            if (correlationData.getFuture() == null) {
                log.warn("RabbitMQ 未开启发布确认 requestId={}", requestId);
                return false;
            }

            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getPublishConfirmTimeoutSeconds(), TimeUnit.SECONDS);

            if (confirm == null || !confirm.isAck()) {
                log.warn("RabbitMQ 发布未确认 requestId={} reason={}",
                        requestId, confirm == null ? "timeout" : confirm.getReason());
                return false;
            }

            if (correlationData.getReturned() != null) {
                log.warn("RabbitMQ 消息不可路由 requestId={} exchange={} routingKey={}",
                        requestId, RabbitMqConfig.ORDER_EXCHANGE, RabbitMqConfig.ORDER_ROUTING_KEY);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("RabbitMQ 发布异常 requestId={}", requestId, e);
            return false;
        }
    }
}
