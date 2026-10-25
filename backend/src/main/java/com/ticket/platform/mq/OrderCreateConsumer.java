package com.ticket.platform.mq;

import com.ticket.platform.common.DuplicateActivityOrderException;
import com.ticket.platform.common.DuplicateRequestOrderException;
import com.ticket.platform.common.OrderNoCollisionException;
import com.ticket.platform.config.RabbitMqConfig;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.service.GrabRequestCompensationService;
import com.ticket.platform.service.OrderCreationTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 异步下单消费者。
 * 监听器只负责异常分类和确认；订单创建事务由 OrderCreationTxService 完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private static final int ORDER_NO_RETRY_TIMES = 3;

    private final OrderCreationTxService orderCreationTxService;
    private final GrabRequestCompensationService compensationService;
    private final GrabRequestMapper requestMapper;

    @RabbitListener(queues = RabbitMqConfig.ORDER_QUEUE)
    public void onMessage(OrderCreateMessage message) {
        if (message == null || message.getRequestId() == null
                || message.getRequestId().isBlank()) {
            log.warn("忽略无效下单消息");
            return;
        }

        String requestId = message.getRequestId();
        requestMapper.incrementConsumeAttempts(requestId, LocalDateTime.now());

        for (int attempt = 1; attempt <= ORDER_NO_RETRY_TIMES; attempt++) {
            try {
                Long orderId = orderCreationTxService.createOrderFromRequest(requestId);
                log.info("异步下单消费完成 requestId={} orderId={}", requestId, orderId);
                return;
            } catch (OrderNoCollisionException e) {
                log.warn("订单号碰撞，第 {} 次重试 requestId={}", attempt, requestId);
            } catch (DuplicateActivityOrderException e) {
                // 同一用户/活动已有订单，本次预留需要归还
                compensationService.failAndRestore(requestId, "该活动已存在订单");
                return;
            } catch (DuplicateRequestOrderException e) {
                // 同一 requestId 已有订单，补齐请求状态即可
                orderCreationTxService.markSuccessFromExistingOrder(requestId);
                return;
            }
        }

        throw new IllegalStateException("订单号连续碰撞 requestId=" + requestId);
    }
}
