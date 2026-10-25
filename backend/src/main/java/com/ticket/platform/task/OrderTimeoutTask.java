package com.ticket.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.entity.Order;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.service.OrderService;
import com.ticket.platform.state.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单任务：定期关闭待支付超时的订单并回补库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private static final int TIMEOUT_MINUTES = 15;
    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    /**
     * 使用 fixedDelay 避免上一轮未结束时任务堆积；服务启动 10 秒后开始首轮。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void closeTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<Order> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, OrderStatus.WAIT_PAY.getCode())
                        .lt(Order::getCreateTime, deadline)
                        .last("limit " + BATCH_SIZE));

        if (timeoutOrders.isEmpty()) {
            return;
        }

        int success = 0;
        for (Order order : timeoutOrders) {
            try {
                orderService.cancelTimeoutOrder(order.getId());
                success++;
            } catch (Exception e) {
                // 单笔失败不影响本轮其他订单
                log.error("关单失败 orderId={}", order.getId(), e);
            }
        }
        log.info("超时关单：本轮扫描 {} 笔，成功 {} 笔", timeoutOrders.size(), success);
    }
}
