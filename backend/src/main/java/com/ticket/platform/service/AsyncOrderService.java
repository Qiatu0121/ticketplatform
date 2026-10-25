package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.common.DuplicateRequestException;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.GrabRequest;
import com.ticket.platform.entity.Order;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.GrabRequestMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.vo.GrabRequestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 异步下单入口：校验请求、执行库存预留、返回 requestId 状态。
 * 真正的订单创建由 RabbitMQ 消费者完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncOrderService {

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private final GrabRequestMapper requestMapper;
    private final ActivityTicketMapper ticketMapper;
    private final ActivityMapper activityMapper;
    private final OrderMapper orderMapper;
    private final GrabRequestReservationService reservationService;
    private final StockService stockService;

    /**
     * 提交异步下单请求。
     * 同一个 requestId 重复提交时直接返回已有请求，不重复扣库存。
     */
    public GrabRequestVO submit(Long userId, String requestId, Long ticketId, int count) {
        validate(requestId, count);

        GrabRequest existing = findByRequestId(requestId);
        if (existing != null) {
            assertOwner(userId, existing);
            return GrabRequestVO.from(existing);
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

        BigDecimal price = ticket.getPrice() == null ? BigDecimal.ZERO : ticket.getPrice();
        BigDecimal amount = price.multiply(BigDecimal.valueOf(count));

        try {
            GrabRequest request = reservationService.reserve(
                    userId, activity, ticket, count, amount, requestId);
            return GrabRequestVO.from(request);
        } catch (DuplicateRequestException e) {
            GrabRequest concurrent = findByRequestId(requestId);
            if (concurrent == null) {
                throw e;
            }
            assertOwner(userId, concurrent);
            return GrabRequestVO.from(concurrent);
        }
    }

    /** 查询请求状态，只允许请求所属用户或管理员访问。 */
    public GrabRequestVO getStatus(Long userId, boolean admin, String requestId) {
        GrabRequest request = findByRequestId(requestId);
        if (request == null) {
            throw new BizException("请求不存在");
        }
        if (!admin && !userId.equals(request.getUserId())) {
            throw new BizException("无权查看该请求");
        }
        return GrabRequestVO.from(request);
    }

    private GrabRequest findByRequestId(String requestId) {
        return requestMapper.selectOne(
                new LambdaQueryWrapper<GrabRequest>()
                        .eq(GrabRequest::getRequestId, requestId));
    }

    private void assertOwner(Long userId, GrabRequest request) {
        if (!userId.equals(request.getUserId())) {
            throw new BizException("requestId 已被占用");
        }
    }

    private void validate(String requestId, int count) {
        if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            throw new BizException("requestId 格式不正确，应为 16-64 位字母、数字、下划线或短横线");
        }
        if (count <= 0) {
            throw new BizException("购买数量必须大于0");
        }
    }
}
