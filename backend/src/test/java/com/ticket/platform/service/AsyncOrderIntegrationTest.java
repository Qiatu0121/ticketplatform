package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.config.DataInitializer;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.entity.Order;
import com.ticket.platform.entity.OrderLog;
import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mapper.OrderLogMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.mapper.UserMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.RedisRollbackState;
import com.ticket.platform.task.GrabRequestRedisRepairTask;
import com.ticket.platform.task.GrabRequestWatchdogTask;
import com.ticket.platform.task.OrderTimeoutTask;
import com.ticket.platform.vo.GrabRequestVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步下单核心流程集成测试：预留、重复请求、消费者幂等、失败补偿。
 * 不启动 MQ 监听，直接调用 Service；需要本地 MySQL 和 Redis。
 *
 * RUN_INTEGRATION_TESTS=true mvn -Dtest=AsyncOrderIntegrationTest test
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class AsyncOrderIntegrationTest {

    @Autowired
    private AsyncOrderService asyncOrderService;
    @Autowired
    private OrderCreationTxService orderCreationTxService;
    @Autowired
    private GrabRequestCompensationService compensationService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ActivityMapper activityMapper;
    @Autowired
    private ActivityTicketMapper ticketMapper;
    @Autowired
    private GrabRequestMapper requestMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderLogMapper orderLogMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private DataInitializer dataInitializer;
    @MockBean
    private OrderTimeoutTask orderTimeoutTask;
    @MockBean
    private OrderOutboxRelay orderOutboxRelay;
    @MockBean
    private GrabRequestWatchdogTask grabRequestWatchdogTask;
    @MockBean
    private GrabRequestRedisRepairTask grabRequestRedisRepairTask;

    @Test
    void reserveConsumerAndDuplicateRequestAreIdempotent() {
        TempData data = createTempData("consume");
        String requestId = UUID.randomUUID().toString().replace("-", "");
        Long orderId = null;

        try {
            GrabRequestVO first = asyncOrderService.submit(
                    data.userId, requestId, data.ticketId, 1);
            assertEquals("PENDING", first.getStatus());
            assertEquals(Integer.valueOf(9), ticketMapper.selectById(data.ticketId).getStock());
            assertEquals("9", redisTemplate.opsForValue().get(stockKey(data.ticketId)));
            assertEquals(Boolean.TRUE, redisTemplate.opsForSet().isMember(
                    buyerKey(data.ticketId), String.valueOf(data.userId)));

            // 同一 requestId 重复提交：不重复预留
            GrabRequestVO second = asyncOrderService.submit(
                    data.userId, requestId, data.ticketId, 1);
            assertEquals("PENDING", second.getStatus());
            assertEquals(Integer.valueOf(9), ticketMapper.selectById(data.ticketId).getStock());
            assertEquals(1L, requestMapper.selectCount(
                    new LambdaQueryWrapper<GrabRequest>()
                            .eq(GrabRequest::getRequestId, requestId)));

            // 消费者重复投递：只产生一个订单
            orderId = orderCreationTxService.createOrderFromRequest(requestId);
            Long again = orderCreationTxService.createOrderFromRequest(requestId);
            assertNotNull(orderId);
            assertEquals(orderId, again);
            assertEquals(1L, orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getUserId, data.userId)
                            .eq(Order::getActivityId, data.activityId)));

            GrabRequest request = requestMapper.selectOne(
                    new LambdaQueryWrapper<GrabRequest>()
                            .eq(GrabRequest::getRequestId, requestId));
            assertEquals(GrabRequestStatus.SUCCESS, request.getStatus());
            assertEquals(orderId, request.getOrderId());

            List<OrderLog> logs = orderLogMapper.selectList(
                    new LambdaQueryWrapper<OrderLog>().eq(OrderLog::getOrderId, orderId));
            assertEquals(1, logs.size());
        } finally {
            cleanup(data, requestId, orderId);
        }
    }

    @Test
    void compensationRestoresStockOnlyOnce() {
        TempData data = createTempData("compensate");
        String requestId = UUID.randomUUID().toString().replace("-", "");

        try {
            asyncOrderService.submit(data.userId, requestId, data.ticketId, 1);
            assertEquals(Integer.valueOf(9), ticketMapper.selectById(data.ticketId).getStock());

            assertTrue(compensationService.failAndRestore(requestId, "测试失败"));
            GrabRequest failed = requestMapper.selectOne(
                    new LambdaQueryWrapper<GrabRequest>()
                            .eq(GrabRequest::getRequestId, requestId));
            assertEquals(GrabRequestStatus.FAILED, failed.getStatus());
            assertEquals(RedisRollbackState.DONE, failed.getRedisRollbackState());
            assertEquals(Integer.valueOf(10), ticketMapper.selectById(data.ticketId).getStock());
            assertEquals("10", redisTemplate.opsForValue().get(stockKey(data.ticketId)));
            assertEquals(Boolean.FALSE, redisTemplate.opsForSet().isMember(
                    buyerKey(data.ticketId), String.valueOf(data.userId)));

            // 再次补偿必须幂等
            assertFalse(compensationService.failAndRestore(requestId, "重复补偿"));
            assertEquals(Integer.valueOf(10), ticketMapper.selectById(data.ticketId).getStock());
        } finally {
            cleanup(data, requestId, null);
        }
    }

    private TempData createTempData(String suffix) {
        String unique = System.nanoTime() + "_" + suffix;

        User user = new User();
        user.setUsername("async_user_" + unique);
        user.setPhone("157" + String.format("%08d",
                Math.abs(System.nanoTime()) % 100_000_000));
        user.setNickname("async");
        user.setRole("USER");
        user.setPasswordHash("not-used");
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);

        Activity activity = new Activity();
        activity.setName("ASYNC_ACTIVITY_" + unique);
        activity.setStartTime(LocalDateTime.now().plusDays(1));
        activity.setLocation("test");
        activity.setDetail("async integration test");
        activity.setStatus(1);
        activity.setCreateTime(LocalDateTime.now());
        activityMapper.insert(activity);

        ActivityTicket ticket = new ActivityTicket();
        ticket.setActivityId(activity.getId());
        ticket.setName("ASYNC_TICKET");
        ticket.setPrice(BigDecimal.TEN);
        ticket.setTotalStock(10);
        ticket.setStock(10);
        ticket.setCreateTime(LocalDateTime.now());
        ticketMapper.insert(ticket);
        orderService.initStock(ticket.getId());

        return new TempData(user.getId(), activity.getId(), ticket.getId());
    }

    private void cleanup(TempData data, String requestId, Long orderId) {
        if (orderId != null) {
            orderLogMapper.delete(new LambdaQueryWrapper<OrderLog>()
                    .eq(OrderLog::getOrderId, orderId));
            orderMapper.deleteById(orderId);
        }
        if (requestId != null) {
            requestMapper.delete(new LambdaQueryWrapper<GrabRequest>()
                    .eq(GrabRequest::getRequestId, requestId));
        }
        ticketMapper.deleteById(data.ticketId);
        activityMapper.deleteById(data.activityId);
        userMapper.deleteById(data.userId);
        redisTemplate.delete(List.of(
                stockKey(data.ticketId),
                buyerKey(data.ticketId),
                "ticket:detail:" + data.ticketId,
                "ticket:lock:" + data.ticketId,
                "activity:detail:" + data.activityId,
                "activity:lock:" + data.activityId,
                "rate:activity:" + data.activityId + ":" + data.userId));
    }

    private String stockKey(Long ticketId) {
        return "ticket:stock:" + ticketId;
    }

    private String buyerKey(Long ticketId) {
        return "ticket:buyers:" + ticketId;
    }

    private static class TempData {
        private final Long userId;
        private final Long activityId;
        private final Long ticketId;

        private TempData(Long userId, Long activityId, Long ticketId) {
            this.userId = userId;
            this.activityId = activityId;
            this.ticketId = ticketId;
        }
    }
}
