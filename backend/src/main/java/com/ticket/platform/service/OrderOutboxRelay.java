package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mq.OrderMessageProducer;
import com.ticket.platform.state.GrabRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务性 Outbox 发布器。
 * 扫描仍处于 PENDING 的请求并发送消息；发送失败或进程崩溃后，
 * 下一轮会根据 next_publish_time 继续重发。重复消息由消费者幂等吸收。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelay {

    /** 发布中状态的租约时间，超时后允许其他实例重新抢占 */
    private static final long PUBLISH_LEASE_SECONDS = 30;

    private final GrabRequestMapper requestMapper;
    private final OrderMessageProducer producer;
    private final AsyncOrderProperties properties;

    @Scheduled(fixedDelayString = "${app.async-order.relay-fixed-delay-ms:1000}")
    public void relayPending() {
        LocalDateTime now = LocalDateTime.now();
        List<GrabRequest> candidates = requestMapper.selectList(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getStatus, GrabRequestStatus.PENDING.getCode())
                        .le(GrabRequest::getNextPublishTime, now)
                        .orderByAsc(GrabRequest::getNextPublishTime)
                        .last("limit " + properties.getRelayBatchSize()));

        if (candidates.isEmpty()) {
            return;
        }

        int published = 0;
        int failed = 0;
        for (GrabRequest request : candidates) {
            LocalDateTime claimTime = LocalDateTime.now();
            int claimed = requestMapper.claimForPublish(
                    request.getRequestId(),
                    claimTime,
                    claimTime.minusSeconds(PUBLISH_LEASE_SECONDS));
            if (claimed == 0) {
                continue;
            }

            boolean success = producer.publish(request.getRequestId());
            LocalDateTime updateTime = LocalDateTime.now();

            if (success) {
                LocalDateTime nextPublishTime = updateTime.plusSeconds(
                        properties.getRepublishIntervalSeconds());
                requestMapper.markPublishConfirmed(
                        request.getRequestId(), nextPublishTime, updateTime);
                published++;
            } else {
                int attempts = (request.getPublishAttempts() == null
                        ? 0 : request.getPublishAttempts()) + 1;
                long delaySeconds = Math.min(
                        properties.getPublishRetryBaseSeconds()
                                * (1L << Math.min(attempts - 1, 10)),
                        properties.getPublishRetryMaxSeconds());
                requestMapper.markPublishFailed(
                        request.getRequestId(),
                        updateTime.plusSeconds(delaySeconds),
                        "RabbitMQ 发布失败",
                        updateTime);
                failed++;
            }
        }

        log.info("Outbox 发布：候选 {}，成功 {}，失败 {}", candidates.size(), published, failed);
    }
}
