package com.ticket.platform.state;

import com.ticket.platform.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单状态机流转规则测试。
 */
class OrderStatusTest {

    @Test
    void waitPay_canTransferTo_paid() {
        assertDoesNotThrow(() ->
                OrderStatus.WAIT_PAY.assertCanTransferTo(OrderStatus.PAID));
    }

    @Test
    void waitPay_canTransferTo_canceled() {
        assertDoesNotThrow(() ->
                OrderStatus.WAIT_PAY.assertCanTransferTo(OrderStatus.CANCELED));
    }

    @Test
    void paid_canTransferTo_verified() {
        assertDoesNotThrow(() ->
                OrderStatus.PAID.assertCanTransferTo(OrderStatus.VERIFIED));
    }

    @Test
    void canceled_cannotTransferTo_paid() {
        assertThrows(BizException.class, () ->
                OrderStatus.CANCELED.assertCanTransferTo(OrderStatus.PAID));
    }

    @Test
    void waitPay_cannotTransferTo_verified() {
        assertThrows(BizException.class, () ->
                OrderStatus.WAIT_PAY.assertCanTransferTo(OrderStatus.VERIFIED));
    }

    @Test
    void verified_isFinalState() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThrows(BizException.class, () ->
                    OrderStatus.VERIFIED.assertCanTransferTo(target));
        }
    }

    @Test
    void refunded_isFinalState() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThrows(BizException.class, () ->
                    OrderStatus.REFUNDED.assertCanTransferTo(target));
        }
    }
}
