package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.RedisRollbackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 请求失败补偿编排：数据库回补在事务内完成，Redis 回补在事务提交后幂等执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrabRequestCompensationService {

    private final GrabRequestCompensationTxService compensationTxService;
    private final GrabRequestMapper requestMapper;
    private final StockService stockService;

    /** 标记请求失败并回补数据库和 Redis 库存。 */
    public boolean failAndRestore(String requestId, String reason) {
        GrabRequest request = compensationTxService.markFailedAndRestoreDb(requestId, reason);
        if (request == null) {
            return false;
        }

        boolean released = stockService.releaseIdempotentWithRetry(
                request.getRedisToken(),
                request.getUserId(),
                request.getTicketId(),
                request.getCount());

        if (released) {
            requestMapper.markRedisRollbackDone(requestId, LocalDateTime.now());
        } else {
            log.warn("Redis 回补未完成，等待修复任务重试 requestId={}", requestId);
        }
        return released;
    }

    /** 修复任务重试失败请求的 Redis 回补。 */
    public boolean retryRedisRollback(String requestId) {
        GrabRequest request = requestMapper.selectOne(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getRequestId, requestId));
        if (request == null
                || request.getStatus() != GrabRequestStatus.FAILED
                || request.getRedisRollbackState() != RedisRollbackState.PENDING) {
            return true;
        }

        boolean released = stockService.releaseIdempotentWithRetry(
                request.getRedisToken(),
                request.getUserId(),
                request.getTicketId(),
                request.getCount());
        if (released) {
            requestMapper.markRedisRollbackDone(requestId, LocalDateTime.now());
        }
        return released;
    }
}
