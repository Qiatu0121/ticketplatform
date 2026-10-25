package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单状态变更日志。 */
@Data
@TableName("`order_log`")
public class OrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 变更前状态；创建订单时为空 */
    private String fromStatus;

    private String toStatus;

    private LocalDateTime createTime;
}
