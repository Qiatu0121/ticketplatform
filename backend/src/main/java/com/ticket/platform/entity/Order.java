package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.platform.state.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** order 表：订单主记录。 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 异步下单请求 ID；同步下单为空 */
    private String requestId;

    private Long userId;

    private Long activityId;

    private Long ticketId;

    private Integer count;

    /** 下单时的总价，后续价格变动不影响已生成订单 */
    private BigDecimal amount;

    private OrderStatus status;

    private LocalDateTime createTime;

    private LocalDateTime payTime;
}
