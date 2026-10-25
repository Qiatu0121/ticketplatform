package com.ticket.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.common.Result;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.entity.Order;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.service.ActivityService;
import com.ticket.platform.service.OrderService;
import com.ticket.platform.state.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端接口：发布活动、维护票种、查询待核销订单。
 * 访问权限由 AuthInterceptor 统一校验（仅 ADMIN）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ActivityMapper activityMapper;
    private final ActivityTicketMapper ticketMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ActivityService activityService;

    /** 发布活动；未指定状态时默认「进行中」，发布后即可开抢。 */
    @PostMapping("/activity")
    public Result<Long> createActivity(@RequestBody Activity activity) {
        if (activity.getName() == null || activity.getName().isBlank()) {
            throw new BizException("活动名称不能为空");
        }
        activity.setId(null);
        if (activity.getStatus() == null) {
            activity.setStatus(1);
        }
        activity.setCreateTime(LocalDateTime.now());
        activityMapper.insert(activity);
        return Result.ok(activity.getId());
    }

    /** 新增票种，并把初始库存同步到 Redis。 */
    @PostMapping("/activity/{activityId}/ticket")
    public Result<Long> addTicket(@PathVariable Long activityId,
                                  @RequestBody ActivityTicket ticket) {
        if (activityMapper.selectById(activityId) == null) {
            throw new BizException("活动不存在");
        }
        if (ticket.getName() == null || ticket.getName().isBlank()) {
            throw new BizException("票种名称不能为空");
        }

        int totalStock = ticket.getTotalStock() == null ? 0 : ticket.getTotalStock();
        if (totalStock <= 0) {
            throw new BizException("总票数必须大于0");
        }

        ticket.setId(null);
        ticket.setActivityId(activityId);
        ticket.setTotalStock(totalStock);
        ticket.setStock(ticket.getStock() == null ? totalStock : ticket.getStock());
        ticket.setPrice(ticket.getPrice() == null ? BigDecimal.ZERO : ticket.getPrice());
        ticket.setCreateTime(LocalDateTime.now());
        ticketMapper.insert(ticket);

        orderService.initStock(ticket.getId());
        activityService.evictDetail(activityId);
        return Result.ok(ticket.getId());
    }

    /** 管理端订单列表；status 为空时查询全部，传值需为 OrderStatus 枚举名。 */
    @GetMapping("/orders")
    public Result<List<Order>> listOrders(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            try {
                wrapper.eq(Order::getStatus, OrderStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                throw new BizException("订单状态参数不正确");
            }
        }
        wrapper.orderByDesc(Order::getCreateTime);
        return Result.ok(orderMapper.selectList(wrapper));
    }
}
