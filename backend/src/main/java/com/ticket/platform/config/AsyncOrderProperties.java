package com.ticket.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步下单可调参数。
 */
@Data
@ConfigurationProperties(prefix = "app.async-order")
public class AsyncOrderProperties {

    /** 请求最长处理时间（秒） */
    private int requestTtlSeconds = 180;

    private long watchdogFixedDelayMs = 10_000;

    private int watchdogBatchSize = 100;

    private long relayFixedDelayMs = 1_000;

    private int relayBatchSize = 50;

    /** 已确认发布但请求仍未成功时的重新发布间隔（秒） */
    private int republishIntervalSeconds = 30;

    private int publishConfirmTimeoutSeconds = 3;

    private int publishRetryBaseSeconds = 2;

    private int publishRetryMaxSeconds = 60;

    /** Redis 回补幂等标记保留天数 */
    private int redisRollbackMarkerTtlDays = 7;
}
