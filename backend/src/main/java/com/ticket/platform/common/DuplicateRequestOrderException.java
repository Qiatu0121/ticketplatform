package com.ticket.platform.common;

/**
 * 同一 requestId 已经生成过订单。
 */
public class DuplicateRequestOrderException extends RuntimeException {

    public DuplicateRequestOrderException() {
        super("该请求已生成订单");
    }
}
