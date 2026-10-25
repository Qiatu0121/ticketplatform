package com.ticket.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.service.GrabRequestCompensationService;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.RedisRollbackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 回补修复任务。
 * 扫描 FAILED 且 redis_rollback_state=PENDING 的请求，继续幂等回补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabRequestRedisRepairTask {

    private final GrabRequestMapper requestMapper;
    private final GrabRequestCompensationService compensationService;
    private final AsyncOrderProperties properties;

    @Scheduled(fixedDelayString = "${app.async-order.redis-repair-fixed-delay-ms:30000}")
    public void repair() {
        List<GrabRequest> requests = requestMapper.selectList(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getStatus, GrabRequestStatus.FAILED.getCode())
                        .eq(GrabRequest::getRedisRollbackState, RedisRollbackState.PENDING.getCode())
                        .orderByAsc(GrabRequest::getUpdateTime)
                        .last("limit " + properties.getWatchdogBatchSize()));

        if (requests.isEmpty()) {
            return;
        }

        int repaired = 0;
        for (GrabRequest request : requests) {
            try {
                if (compensationService.retryRedisRollback(request.getRequestId())) {
                    repaired++;
                }
            } catch (Exception e) {
                log.error("Redis 回补修复失败 requestId={}", request.getRequestId(), e);
            }
        }
        log.info("Redis 回补修复：待处理 {}，完成 {} 条", requests.size(), repaired);
    }
}
