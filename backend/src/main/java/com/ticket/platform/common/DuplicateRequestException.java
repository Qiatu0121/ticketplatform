package com.ticket.platform.common;

/**
 * 并发提交了相同的 requestId，由唯一索引触发。
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException() {
        super("重复的下单请求");
    }
}
