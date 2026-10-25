package com.ticket.platform.service;

import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.RedisRollbackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 请求失败补偿的数据库部分。
 * 状态更新和数据库库存回补在同一事务内完成，行锁保证只补偿一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrabRequestCompensationTxService {

    private final GrabRequestMapper requestMapper;
    private final StockService stockService;

    /**
     * 将 PENDING 请求标记为 FAILED，并回补数据库库存。
     *
     * @return 需要继续回补 Redis 的请求；请求已终结时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public GrabRequest markFailedAndRestoreDb(String requestId, String reason) {
        GrabRequest request = requestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null || request.getStatus() != GrabRequestStatus.PENDING) {
            return null;
        }

        request.setStatus(GrabRequestStatus.FAILED);
        request.setErrorMessage(reason);
        request.setRedisRollbackState(RedisRollbackState.PENDING);
        request.setUpdateTime(LocalDateTime.now());
        requestMapper.updateById(request);

        stockService.incrementDb(request.getTicketId(), request.getCount());
        log.info("请求已标记失败 requestId={} reason={} ticketId={} count={}",
                requestId, reason, request.getTicketId(), request.getCount());
        return request;
    }
}
