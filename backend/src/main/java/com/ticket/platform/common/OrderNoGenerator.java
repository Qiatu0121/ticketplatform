package com.ticket.platform.common;

import java.util.UUID;

/**
 * 业务订单号生成器：T + 毫秒时间戳 + 6 位随机字符。
 */
public final class OrderNoGenerator {

    private OrderNoGenerator() {
    }

    public static String next() {
        return "T" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6).toUpperCase();
    }
}
