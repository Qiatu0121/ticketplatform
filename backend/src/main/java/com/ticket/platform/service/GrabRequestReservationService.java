package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.common.DuplicateRequestException;
import com.ticket.platform.common.TransactionHooks;
import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.entity.Order;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.PublishState;
import com.ticket.platform.state.RedisRollbackState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步下单预留事务。
 * 数据库扣库存和 Outbox 请求记录必须在同一个事务内提交；Redis 预扣在
 * 事务回滚时通过幂等 token 补偿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrabRequestReservationService {

    private final GrabRequestMapper requestMapper;
    private final OrderMapper orderMapper;
    private final StockService stockService;
    private final AsyncOrderProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public GrabRequest reserve(Long userId,
                               Activity activity,
                               ActivityTicket ticket,
                               int count,
                               BigDecimal amount,
                               String requestId) {
        GrabRequest existing = requestMapper.selectOne(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getRequestId, requestId));
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new BizException("requestId 已被占用");
            }
            return existing;
        }

        Long existingOrder = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getActivityId, activity.getId()));
        if (existingOrder != null && existingOrder > 0) {
            throw new BizException("您已经购买过该活动的票，不能重复下单");
        }

        Long activeRequest = requestMapper.selectCount(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getUserId, userId)
                        .eq(GrabRequest::getActivityId, activity.getId())
                        .eq(GrabRequest::getStatus, GrabRequestStatus.PENDING.getCode()));
        if (activeRequest != null && activeRequest > 0) {
            throw new BizException("订单正在创建中，请勿重复提交");
        }

        String redisToken = stockService.newReservationToken(requestId);
        long left = stockService.preDeduct(userId, ticket.getId(), count);
        if (left == -1) {
            throw new BizException("余票不足，已被抢完");
        }
        if (left == -2) {
            throw new BizException("您已经购买过该活动的票，不能重复下单");
        }

        // 预扣成功后，数据库事务回滚时必须归还 Redis
        TransactionHooks.afterRollback(() ->
                stockService.releaseIdempotentWithRetry(redisToken, userId, ticket.getId(), count));

        if (stockService.deductDb(ticket.getId(), count) == 0) {
            throw new BizException("余票不足，已被抢完");
        }

        LocalDateTime now = LocalDateTime.now();
        GrabRequest request = new GrabRequest();
        request.setRequestId(requestId);
        request.setUserId(userId);
        request.setActivityId(activity.getId());
        request.setTicketId(ticket.getId());
        request.setCount(count);
        request.setAmount(amount);
        request.setStatus(GrabRequestStatus.PENDING);
        request.setPublishState(PublishState.NEW);
        request.setPublishAttempts(0);
        request.setNextPublishTime(now);
        request.setConsumeAttempts(0);
        request.setRedisToken(redisToken);
        request.setRedisRollbackState(RedisRollbackState.NONE);
        request.setExpireTime(now.plusSeconds(properties.getRequestTtlSeconds()));
        request.setCreateTime(now);
        request.setUpdateTime(now);

        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException e) {
            throw new DuplicateRequestException();
        }

        return request;
    }
}
