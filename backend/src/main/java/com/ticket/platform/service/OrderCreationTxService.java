package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.DuplicateActivityOrderException;
import com.ticket.platform.common.DuplicateRequestOrderException;
import com.ticket.platform.common.OrderNoCollisionException;
import com.ticket.platform.common.OrderNoGenerator;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.entity.Order;
import com.ticket.platform.entity.OrderLog;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mapper.OrderLogMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 异步订单创建事务。
 * 通过 grab_request 行锁保证同一请求只处理一次；订单唯一索引作为跨请求的最终幂等保障。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationTxService {

    private final GrabRequestMapper requestMapper;
    private final OrderMapper orderMapper;
    private final OrderLogMapper orderLogMapper;

    /**
     * 根据请求创建订单。
     *
     * @return 订单 ID；请求不存在、已过期或已终结时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOrderFromRequest(String requestId) {
        GrabRequest request = requestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null) {
            log.warn("收到未知下单请求 requestId={}", requestId);
            return null;
        }
        if (request.getStatus() != GrabRequestStatus.PENDING) {
            return request.getOrderId();
        }
        if (request.getExpireTime() != null
                && request.getExpireTime().isBefore(LocalDateTime.now())) {
            // 已过期，由看门狗统一补偿，消费者直接确认消息
            return null;
        }

        Order existedByRequest = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRequestId, requestId));
        if (existedByRequest != null) {
            markSuccess(request, existedByRequest.getId());
            return existedByRequest.getId();
        }

        Order existedByActivity = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, request.getUserId())
                        .eq(Order::getActivityId, request.getActivityId()));
        if (existedByActivity != null) {
            throw new DuplicateActivityOrderException();
        }

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.next());
        order.setRequestId(requestId);
        order.setUserId(request.getUserId());
        order.setActivityId(request.getActivityId());
        order.setTicketId(request.getTicketId());
        order.setCount(request.getCount());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.WAIT_PAY);
        order.setCreateTime(LocalDateTime.now());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw classifyDuplicate(e);
        }

        saveLog(order.getId());
        markSuccess(request, order.getId());
        return order.getId();
    }

    /** 同一 requestId 已存在订单时，补齐请求状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void markSuccessFromExistingOrder(String requestId) {
        GrabRequest request = requestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null || request.getStatus() != GrabRequestStatus.PENDING) {
            return;
        }

        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getRequestId, requestId));
        if (order == null) {
            throw new IllegalStateException("requestId 对应订单不存在：" + requestId);
        }
        markSuccess(request, order.getId());
    }

    private void markSuccess(GrabRequest request, Long orderId) {
        request.setStatus(GrabRequestStatus.SUCCESS);
        request.setOrderId(orderId);
        request.setErrorMessage(null);
        request.setUpdateTime(LocalDateTime.now());
        requestMapper.updateById(request);
    }

    private RuntimeException classifyDuplicate(DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uk_order_request")) {
            return new DuplicateRequestOrderException();
        }
        if (message != null && message.contains("uk_user_activity")) {
            return new DuplicateActivityOrderException();
        }
        if (message != null && message.contains("uk_order_no")) {
            return new OrderNoCollisionException();
        }
        return e;
    }

    private void saveLog(Long orderId) {
        OrderLog logEntry = new OrderLog();
        logEntry.setOrderId(orderId);
        logEntry.setToStatus(OrderStatus.WAIT_PAY.name());
        logEntry.setCreateTime(LocalDateTime.now());
        orderLogMapper.insert(logEntry);
    }
}
