package com.ticket.platform.common;

/**
 * 业务异常。由 GlobalExceptionHandler 转换为统一的错误响应，
 * 消息直接面向用户。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
