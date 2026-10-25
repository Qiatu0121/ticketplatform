package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.common.OrderNoGenerator;
import com.ticket.platform.common.TransactionHooks;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.Order;
import com.ticket.platform.entity.OrderLog;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.OrderLogMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.state.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 同步订单业务：下单、支付、核销、超时取消。
 * 库存相关操作统一委托给 StockService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderLogMapper orderLogMapper;
    private final ActivityTicketMapper ticketMapper;
    private final ActivityMapper activityMapper;
    private final StockService stockService;

    /**
     * 同步创建待支付订单。
     *
     * <p>流程：参数与活动校验 → 限流与重复购买校验 → Redis 预扣 →
     * 数据库条件扣减 → 写入订单与状态日志。数据库事务失败时，
     * 在事务回滚完成后按 token 幂等回补 Redis。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long userId, Long ticketId, int count) {
        if (count <= 0) {
            throw new BizException("购买数量必须大于0");
        }

        ActivityTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException("票种不存在");
        }

        Activity activity = activityMapper.selectById(ticket.getActivityId());
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        if (activity.getStatus() == null || activity.getStatus() != 1) {
            throw new BizException("活动未开始或已经结束");
        }

        stockService.checkRate(activity.getId(), userId);

        Long existing = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getActivityId, activity.getId()));
        if (existing != null && existing > 0) {
            throw new BizException("您已经购买过该活动的票，不能重复下单");
        }

        String redisToken = stockService.newSyncToken();
        long left = stockService.preDeduct(userId, ticketId, count);
        if (left == -1) {
            throw new BizException("余票不足，已被抢完");
        }
        if (left == -2) {
            throw new BizException("您已经购买过该活动的票，不能重复下单");
        }

        try {
            if (stockService.deductDb(ticketId, count) == 0) {
                throw new BizException("余票不足，已被抢完");
            }

            Order order = new Order();
            order.setOrderNo(OrderNoGenerator.next());
            order.setUserId(userId);
            order.setActivityId(activity.getId());
            order.setTicketId(ticket.getId());
            order.setCount(count);
            order.setAmount(ticket.getPrice().multiply(BigDecimal.valueOf(count)));
            order.setStatus(OrderStatus.WAIT_PAY);
            order.setCreateTime(LocalDateTime.now());
            orderMapper.insert(order);

            saveLog(order.getId(), null, OrderStatus.WAIT_PAY);
            TransactionHooks.afterCommit(
                    () -> stockService.evictDetailCache(activity.getId(), ticketId));

            return order.getId();
        } catch (RuntimeException e) {
            TransactionHooks.afterRollback(() ->
                    stockService.releaseIdempotentWithRetry(redisToken, userId, ticketId, count));
            throw e;
        }
    }

    /** 支付订单。状态更新带旧状态条件，避免与超时关单并发覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }

        OrderStatus.WAIT_PAY.assertCanTransferTo(OrderStatus.PAID);
        int updated = orderMapper.payIfMatch(
                orderId,
                userId,
                OrderStatus.WAIT_PAY.getCode(),
                OrderStatus.PAID.getCode(),
                LocalDateTime.now());

        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest == null || !latest.getUserId().equals(userId)) {
                throw new BizException("订单不存在");
            }
            if (latest.getStatus() == OrderStatus.PAID) {
                return;
            }
            throw new BizException("当前状态不可支付");
        }

        saveLog(orderId, OrderStatus.WAIT_PAY, OrderStatus.PAID);
    }

    /** 核销订单。状态更新带旧状态条件，重复或并发核销只会生效一次。 */
    @Transactional(rollbackFor = Exception.class)
    public void verifyOrder(Long orderId) {
        int updated = updateStatusIfMatch(
                orderId, OrderStatus.PAID, OrderStatus.VERIFIED);

        if (updated == 0) {
            Order latest = orderMapper.selectById(orderId);
            if (latest == null) {
                throw new BizException("订单不存在");
            }
            if (latest.getStatus() == OrderStatus.VERIFIED) {
                return;
            }
            throw new BizException("当前状态不可核验");
        }

        saveLog(orderId, OrderStatus.PAID, OrderStatus.VERIFIED);
    }

    /**
     * 超时关单：WAIT_PAY → CANCELED，并回补数据库与 Redis 库存。
     * 只有真正把订单从待支付改成已取消的线程才执行库存回补。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != OrderStatus.WAIT_PAY) {
            return;
        }

        int updated = updateStatusIfMatch(
                orderId, OrderStatus.WAIT_PAY, OrderStatus.CANCELED);
        if (updated == 0) {
            return;
        }

        stockService.incrementDb(order.getTicketId(), order.getCount());
        saveLog(orderId, OrderStatus.WAIT_PAY, OrderStatus.CANCELED);

        String redisToken = "cancel:" + orderId;
        TransactionHooks.afterCommit(() -> {
            stockService.releaseIdempotentWithRetry(
                    redisToken, order.getUserId(), order.getTicketId(), order.getCount());
            stockService.evictDetailCache(order.getActivityId(), order.getTicketId());
        });

        log.info("超时关单成功 orderId={} userId={} ticketId={}",
                orderId, order.getUserId(), order.getTicketId());
    }

    /** 管理端新增票种后初始化 Redis 库存。 */
    public void initStock(Long ticketId) {
        stockService.initStock(ticketId);
    }

    /** 启动时以数据库为准重建 Redis 库存与购买标记。 */
    public int reconcileStockFromDb() {
        return stockService.reconcileStockFromDb();
    }

    /** 状态机校验 + 条件更新，返回影响行数。 */
    private int updateStatusIfMatch(Long orderId, OrderStatus from, OrderStatus to) {
        from.assertCanTransferTo(to);
        return orderMapper.updateStatusIfMatch(orderId, from.getCode(), to.getCode());
    }

    /** 记录订单状态流转。from 为 null 表示创建订单。 */
    private void saveLog(Long orderId, OrderStatus from, OrderStatus to) {
        OrderLog entry = new OrderLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(from == null ? null : from.name());
        entry.setToStatus(to.name());
        entry.setCreateTime(LocalDateTime.now());
        orderLogMapper.insert(entry);
    }
}
