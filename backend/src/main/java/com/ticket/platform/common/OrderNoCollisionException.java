package com.ticket.platform.common;

/**
 * 订单号唯一索引碰撞，使用新订单号重试即可。
 */
public class OrderNoCollisionException extends RuntimeException {

    public OrderNoCollisionException() {
        super("订单号碰撞");
    }
}
