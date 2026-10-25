package com.ticket.platform.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步下单消息，只携带 requestId，消费者以数据库请求记录为准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage {

    private String requestId;
}
