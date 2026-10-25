package com.ticket.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.service.GrabRequestCompensationService;
import com.ticket.platform.state.GrabRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步下单看门狗。
 * 请求超过 TTL 仍未成功时自动标记失败，并回补数据库与 Redis 库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabRequestWatchdogTask {

    private final GrabRequestMapper requestMapper;
    private final GrabRequestCompensationService compensationService;
    private final AsyncOrderProperties properties;

    @Scheduled(fixedDelayString = "${app.async-order.watchdog-fixed-delay-ms:10000}")
    public void closeExpiredRequests() {
        LocalDateTime now = LocalDateTime.now();
        List<GrabRequest> expired = requestMapper.selectList(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getStatus, GrabRequestStatus.PENDING.getCode())
                        .le(GrabRequest::getExpireTime, now)
                        .orderByAsc(GrabRequest::getExpireTime)
                        .last("limit " + properties.getWatchdogBatchSize()));

        if (expired.isEmpty()) {
            return;
        }

        int restored = 0;
        for (GrabRequest request : expired) {
            try {
                if (compensationService.failAndRestore(request.getRequestId(), "订单创建超时")) {
                    restored++;
                }
            } catch (Exception e) {
                log.error("看门狗补偿失败 requestId={}", request.getRequestId(), e);
            }
        }
        log.info("看门狗扫描 {} 条过期请求，完成 Redis 回补 {} 条", expired.size(), restored);
    }
}
