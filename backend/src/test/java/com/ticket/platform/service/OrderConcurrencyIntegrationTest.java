package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.config.DataInitializer;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.Order;
import com.ticket.platform.entity.OrderLog;
import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.OrderLogMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.mapper.UserMapper;
import com.ticket.platform.state.OrderStatus;
import com.ticket.platform.task.OrderTimeoutTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付与超时关单并发回归测试。
 *
 * <p>需要本地 MySQL 和 Redis。默认跳过，显式开启：
 * RUN_INTEGRATION_TESTS=true mvn -Dtest=OrderConcurrencyIntegrationTest test</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderLogMapper orderLogMapper;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityTicketMapper ticketMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 测试不需要启动初始化对账和定时任务 */
    @MockBean
    private DataInitializer dataInitializer;
    @MockBean
    private OrderTimeoutTask orderTimeoutTask;

    @Test
    void payAndTimeoutCancelCannotBothTakeEffect() throws Exception {
        for (int i = 0; i < 5; i++) {
            runCase(i);
        }
    }

    @Test
    void failedCreateOrderRestoresRedisAfterRollback() {
        String suffix = System.nanoTime() + "_rollback";
        Long userId = null;
        Long activityId = null;
        Long ticketId = null;

        try {
            User user = new User();
            user.setUsername("it_user_" + suffix);
            user.setPhone("158" + String.format("%08d",
                    Math.abs(System.nanoTime()) % 100_000_000));
            user.setNickname("it");
            user.setRole("USER");
            user.setPasswordHash("not-used");
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            userId = user.getId();

            Activity activity = new Activity();
            activity.setName("IT_ACTIVITY_" + suffix);
            activity.setStartTime(LocalDateTime.now().plusDays(1));
            activity.setLocation("test");
            activity.setDetail("rollback test");
            activity.setStatus(1);
            activity.setCreateTime(LocalDateTime.now());
            activityMapper.insert(activity);
            activityId = activity.getId();

            ActivityTicket ticket = new ActivityTicket();
            ticket.setActivityId(activityId);
            ticket.setName("IT_TICKET");
            ticket.setPrice(BigDecimal.TEN);
            ticket.setTotalStock(1);
            ticket.setStock(1);
            ticket.setCreateTime(LocalDateTime.now());
            ticketMapper.insert(ticket);
            ticketId = ticket.getId();
            orderService.initStock(ticketId);

            // 人为制造 Redis 与数据库库存差异：Redis 还有 1 张，数据库已经为 0
            ActivityTicket stockUpdate = new ActivityTicket();
            stockUpdate.setId(ticketId);
            stockUpdate.setStock(0);
            ticketMapper.updateById(stockUpdate);

            Long finalUserId = userId;
            Long finalTicketId = ticketId;
            assertThrows(BizException.class,
                    () -> orderService.createOrder(finalUserId, finalTicketId, 1));

            // 数据库扣减失败后事务回滚，Redis 预扣也应在回滚后归还
            assertEquals("1", redisTemplate.opsForValue().get("ticket:stock:" + ticketId));
            assertEquals(Boolean.FALSE, redisTemplate.opsForSet().isMember(
                    "ticket:buyers:" + ticketId, String.valueOf(userId)));
            assertEquals(Integer.valueOf(0), ticketMapper.selectById(ticketId).getStock());
        } finally {
            if (ticketId != null) {
                ticketMapper.deleteById(ticketId);
                redisTemplate.delete(List.of(
                        "ticket:stock:" + ticketId,
                        "ticket:buyers:" + ticketId,
                        "ticket:detail:" + ticketId,
                        "ticket:lock:" + ticketId));
            }
            if (activityId != null) {
                activityMapper.deleteById(activityId);
                redisTemplate.delete(List.of(
                        "activity:detail:" + activityId,
                        "activity:lock:" + activityId));
            }
            if (userId != null) {
                userMapper.deleteById(userId);
                if (activityId != null) {
                    redisTemplate.delete("rate:activity:" + activityId + ":" + userId);
                }
            }
        }
    }

    private void runCase(int index) throws Exception {
        String suffix = System.nanoTime() + "_" + index;
        Long userId = null;
        Long activityId = null;
        Long ticketId = null;
        Long orderId = null;

        try {
            User user = new User();
            user.setUsername("it_user_" + suffix);
            user.setPhone("159" + String.format("%08d",
                    Math.abs(System.nanoTime() + index) % 100_000_000));
            user.setNickname("it");
            user.setRole("USER");
            user.setPasswordHash("not-used");
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
            userId = user.getId();

            Activity activity = new Activity();
            activity.setName("IT_ACTIVITY_" + suffix);
            activity.setStartTime(LocalDateTime.now().plusDays(1));
            activity.setLocation("test");
            activity.setDetail("integration test");
            activity.setStatus(1);
            activity.setCreateTime(LocalDateTime.now());
            activityMapper.insert(activity);
            activityId = activity.getId();

            ActivityTicket ticket = new ActivityTicket();
            ticket.setActivityId(activityId);
            ticket.setName("IT_TICKET");
            ticket.setPrice(BigDecimal.TEN);
            ticket.setTotalStock(10);
            ticket.setStock(10);
            ticket.setCreateTime(LocalDateTime.now());
            ticketMapper.insert(ticket);
            ticketId = ticket.getId();
            orderService.initStock(ticketId);

            orderId = orderService.createOrder(userId, ticketId, 1);
            assertEquals(Integer.valueOf(9), ticketMapper.selectById(ticketId).getStock());

            Long finalOrderId = orderId;
            Long finalUserId = userId;
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);

            try {
                Future<?> payFuture = pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        orderService.payOrder(finalOrderId, finalUserId);
                    } catch (Exception ignored) {
                        // 关单先成功时支付会失败，属于预期结果
                    }
                    return null;
                });

                Future<?> cancelFuture = pool.submit(() -> {
                    ready.countDown();
                    await(start);
                    orderService.cancelTimeoutOrder(finalOrderId);
                    return null;
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                payFuture.get(10, TimeUnit.SECONDS);
                cancelFuture.get(10, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            Order order = orderMapper.selectById(orderId);
            assertNotNull(order);
            assertTrue(order.getStatus() == OrderStatus.PAID
                            || order.getStatus() == OrderStatus.CANCELED,
                    "最终状态应为已支付或已取消，实际=" + order.getStatus());

            ActivityTicket latestTicket = ticketMapper.selectById(ticketId);
            String redisStock = redisTemplate.opsForValue().get("ticket:stock:" + ticketId);
            Boolean buyer = redisTemplate.opsForSet().isMember(
                    "ticket:buyers:" + ticketId, String.valueOf(userId));

            if (order.getStatus() == OrderStatus.PAID) {
                assertEquals(Integer.valueOf(9), latestTicket.getStock(), "支付成功时不应回补库存");
                assertEquals("9", redisStock);
                assertEquals(Boolean.TRUE, buyer, "已支付订单应保留购买标记");
            } else {
                assertEquals(Integer.valueOf(10), latestTicket.getStock(), "取消时只能回补一次库存");
                assertEquals("10", redisStock);
                assertEquals(Boolean.FALSE, buyer, "取消后应移除购买标记");
            }

            List<OrderLog> logs = orderLogMapper.selectList(
                    new LambdaQueryWrapper<OrderLog>().eq(OrderLog::getOrderId, orderId));
            long transitions = logs.stream()
                    .filter(log -> "WAIT_PAY".equals(log.getFromStatus()))
                    .count();
            assertEquals(1, transitions, "同一订单只能成功流转一次");
        } finally {
            if (orderId != null) {
                orderLogMapper.delete(
                        new LambdaQueryWrapper<OrderLog>().eq(OrderLog::getOrderId, orderId));
                orderMapper.deleteById(orderId);
            }
            if (ticketId != null) {
                ticketMapper.deleteById(ticketId);
                redisTemplate.delete(List.of(
                        "ticket:stock:" + ticketId,
                        "ticket:buyers:" + ticketId,
                        "ticket:detail:" + ticketId,
                        "ticket:lock:" + ticketId));
            }
            if (activityId != null) {
                activityMapper.deleteById(activityId);
                redisTemplate.delete(List.of(
                        "activity:detail:" + activityId,
                        "activity:lock:" + activityId));
            }
            if (userId != null) {
                userMapper.deleteById(userId);
                if (activityId != null) {
                    redisTemplate.delete("rate:activity:" + activityId + ":" + userId);
                }
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
