package com.ticket.platform.common;

/**
 * 同一用户在该活动下已存在订单。
 */
public class DuplicateActivityOrderException extends RuntimeException {

    public DuplicateActivityOrderException() {
        super("该活动已存在订单");
    }
}
