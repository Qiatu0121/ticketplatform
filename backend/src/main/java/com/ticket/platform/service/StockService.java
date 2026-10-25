package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.config.AsyncOrderProperties;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.entity.Order;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 库存服务：统一同步下单和异步下单的 Redis 预扣、数据库扣减、
 * 幂等回补、启动对账与详情缓存失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private static final String ACTIVITY_DETAIL_KEY = "activity:detail:";
    private static final String TICKET_DETAIL_KEY = "ticket:detail:";
    private static final String RATE_KEY_PREFIX = "rate:activity:";
    private static final int RATE_LIMIT_PER_SECOND = 10;

    private static final int REDIS_RETRY_TIMES = 3;
    private static final long REDIS_RETRY_BASE_MILLIS = 100;

    private static final DefaultRedisScript<Long> GRAB_SCRIPT = loadScript("lua/grab.lua");
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            loadScript("lua/grab_rollback_idempotent.lua");

    private final StringRedisTemplate redisTemplate;
    private final ActivityTicketMapper ticketMapper;
    private final OrderMapper orderMapper;
    private final GrabRequestMapper grabRequestMapper;
    private final AsyncOrderProperties properties;

    private static DefaultRedisScript<Long> loadScript(String classpath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpath)));
        script.setResultType(Long.class);
        return script;
    }

    private String stockKey(Long ticketId) {
        return "ticket:stock:" + ticketId;
    }

    private String buyersKey(Long ticketId) {
        return "ticket:buyers:" + ticketId;
    }

    private String rollbackMarkerKey(String token) {
        return "ticket:rollback:" + token;
    }

    /** 生成一次独立预扣的幂等回补 token。 */
    public String newReservationToken(String requestId) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return requestId + ":" + random;
    }

    /** 同步下单使用的独立 token。 */
    public String newSyncToken() {
        return "sync:" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Redis 预扣库存并记录购买用户。
     *
     * @return 剩余库存；-1 库存不足；-2 该用户已购买过
     */
    public long preDeduct(Long userId, Long ticketId, int count) {
        Long result = redisTemplate.execute(
                GRAB_SCRIPT,
                List.of(stockKey(ticketId), buyersKey(ticketId)),
                String.valueOf(userId),
                String.valueOf(count));

        if (result == null) {
            throw new BizException("系统繁忙，请稍后再试");
        }
        return result;
    }

    /** 数据库条件扣减，返回 0 表示库存不足。 */
    public int deductDb(Long ticketId, int count) {
        return ticketMapper.deductStock(ticketId, count);
    }

    /** 数据库回补库存。 */
    public int incrementDb(Long ticketId, int count) {
        return ticketMapper.incrementStock(ticketId, count);
    }

    /**
     * 幂等回补 Redis 库存并移除购买标记。
     * 同一个 token 无论调用多少次，只会真正回补一次。
     */
    public boolean releaseIdempotentWithRetry(String token, Long userId, Long ticketId, int count) {
        long markerTtlSeconds = Math.max(60L, properties.getRedisRollbackMarkerTtlDays() * 86_400L);

        for (int attempt = 1; attempt <= REDIS_RETRY_TIMES; attempt++) {
            try {
                Long result = redisTemplate.execute(
                        ROLLBACK_SCRIPT,
                        List.of(stockKey(ticketId), buyersKey(ticketId), rollbackMarkerKey(token)),
                        String.valueOf(userId),
                        String.valueOf(count),
                        String.valueOf(markerTtlSeconds));
                if (result != null) {
                    return true;
                }
                throw new IllegalStateException("Redis 回补脚本返回空结果");
            } catch (Exception e) {
                if (attempt == REDIS_RETRY_TIMES) {
                    log.error("Redis 回补失败，已重试 {} 次，需人工处理 token={} userId={} ticketId={} count={}",
                            REDIS_RETRY_TIMES, token, userId, ticketId, count, e);
                    return false;
                }
                log.warn("Redis 回补失败，准备第 {} 次重试 token={} userId={} ticketId={} count={}，原因={}",
                        attempt, token, userId, ticketId, count, e.getMessage());
                sleepQuietly(REDIS_RETRY_BASE_MILLIS * attempt);
            }
        }
        return false;
    }

    /** 把当前数据库库存写入 Redis，发布新票种后调用。 */
    public void initStock(Long ticketId) {
        ActivityTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket != null) {
            redisTemplate.opsForValue().set(stockKey(ticketId), String.valueOf(ticket.getStock()));
        }
    }

    /**
     * 启动时以数据库为准重建 Redis 库存和购买标记。
     * 购买标记包含活跃订单用户和仍在排队的异步请求用户。
     * 仅适用于单实例、开始接收请求前的初始化阶段。
     *
     * @return 库存值与数据库不一致并已修正的票种数量
     */
    public int reconcileStockFromDb() {
        List<ActivityTicket> tickets = ticketMapper.selectList(null);
        int corrected = 0;

        for (ActivityTicket ticket : tickets) {
            if (ticket.getStock() == null) {
                continue;
            }
            String key = stockKey(ticket.getId());
            String dbStock = String.valueOf(ticket.getStock());
            String redisStock = redisTemplate.opsForValue().get(key);
            if (!dbStock.equals(redisStock)) {
                redisTemplate.opsForValue().set(key, dbStock);
                corrected++;
            }
        }

        rebuildBuyerSets(tickets);
        return corrected;
    }

    /** 用活跃订单和待处理请求重建购买用户集合。 */
    private void rebuildBuyerSets(List<ActivityTicket> tickets) {
        for (ActivityTicket ticket : tickets) {
            redisTemplate.delete(buyersKey(ticket.getId()));
        }

        List<Order> activeOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus,
                                OrderStatus.WAIT_PAY.getCode(),
                                OrderStatus.PAID.getCode(),
                                OrderStatus.VERIFIED.getCode()));
        for (Order order : activeOrders) {
            redisTemplate.opsForSet().add(
                    buyersKey(order.getTicketId()),
                    String.valueOf(order.getUserId()));
        }

        List<GrabRequest> pendingRequests = grabRequestMapper.selectList(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getStatus, GrabRequestStatus.PENDING.getCode()));
        for (GrabRequest request : pendingRequests) {
            redisTemplate.opsForSet().add(
                    buyersKey(request.getTicketId()),
                    String.valueOf(request.getUserId()));
        }
    }

    /** 库存变化后删除活动/票种详情缓存。 */
    public void evictDetailCache(Long activityId, Long ticketId) {
        try {
            redisTemplate.delete(List.of(
                    ACTIVITY_DETAIL_KEY + activityId,
                    TICKET_DETAIL_KEY + ticketId));
        } catch (Exception e) {
            log.warn("清理余票缓存失败 activityId={} ticketId={}", activityId, ticketId, e);
        }
    }

    /** 按「活动 + 用户」做 1 秒窗口限流。 */
    public void checkRate(Long activityId, Long userId) {
        String key = RATE_KEY_PREFIX + activityId + ":" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(1));
        }
        if (count != null && count > RATE_LIMIT_PER_SECOND) {
            throw new BizException("操作过于频繁，请稍后再试");
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
