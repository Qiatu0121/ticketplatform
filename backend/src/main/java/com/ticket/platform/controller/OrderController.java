package com.ticket.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.common.Result;
import com.ticket.platform.config.AuthInterceptor;
import com.ticket.platform.entity.Order;
import com.ticket.platform.entity.User;
import com.ticket.platform.mapper.OrderMapper;
import com.ticket.platform.service.AsyncOrderService;
import com.ticket.platform.service.OrderService;
import com.ticket.platform.vo.GrabRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单接口。当前用户由 AuthInterceptor 写入 request attribute，
 * 不再接收前端传入的 userId，避免越权操作。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;
    private final AsyncOrderService asyncOrderService;
    private final OrderMapper orderMapper;

    /** 同步创建订单。兼容骨架的 /grab 路径，保留作为回退与压测入口。 */
    @PostMapping({"/create", "/grab"})
    public Result<Long> create(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser,
            @RequestParam Long ticketId,
            @RequestParam(defaultValue = "1") Integer count) {
        return Result.ok(orderService.createOrder(loginUser.getId(), ticketId, count));
    }

    /**
     * 异步创建订单：先落库预留库存，再由 RabbitMQ 消费者建单。
     * requestId 由客户端生成，用于 HTTP 超时重试时的幂等控制。
     */
    @PostMapping("/async-create")
    public ResponseEntity<Result<GrabRequestVO>> asyncCreate(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser,
            @RequestParam String requestId,
            @RequestParam Long ticketId,
            @RequestParam(defaultValue = "1") Integer count) {
        GrabRequestVO vo = asyncOrderService.submit(
                loginUser.getId(), requestId, ticketId, count);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.ok(vo));
    }

    /** 查询异步请求状态；只能查看自己的请求，管理员可查看全部。 */
    @GetMapping("/request/{requestId}")
    public Result<GrabRequestVO> requestStatus(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser,
            @PathVariable String requestId) {
        boolean admin = "ADMIN".equals(loginUser.getRole());
        return Result.ok(asyncOrderService.getStatus(loginUser.getId(), admin, requestId));
    }

    /** 支付自己的待支付订单。 */
    @PostMapping("/pay")
    public Result<Void> pay(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser,
            @RequestParam Long orderId) {
        orderService.payOrder(orderId, loginUser.getId());
        return Result.ok(null);
    }

    /** 核销订单，仅管理员可操作。 */
    @PostMapping("/verify")
    public Result<Void> verify(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser,
            @RequestParam Long orderId) {
        if (!"ADMIN".equals(loginUser.getRole())) {
            throw new BizException("无权限，仅管理员可操作");
        }
        orderService.verifyOrder(orderId);
        return Result.ok(null);
    }

    /** 当前用户的订单列表，按下单时间倒序。 */
    @GetMapping("/my")
    public Result<List<Order>> myOrders(
            @RequestAttribute(AuthInterceptor.LOGIN_USER_ATTR) User loginUser) {
        return Result.ok(orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, loginUser.getId())
                        .orderByDesc(Order::getCreateTime)));
    }
}
