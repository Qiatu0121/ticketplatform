package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.platform.state.GrabRequestStatus;
import com.ticket.platform.state.PublishState;
import com.ticket.platform.state.RedisRollbackState;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步下单请求，同时也是事务性 Outbox 记录。
 */
@Data
@TableName("`grab_request`")
public class GrabRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端生成的幂等键 */
    private String requestId;

    private Long userId;

    private Long activityId;

    private Long ticketId;

    private Integer count;

    /** 提交时快照的总价 */
    private BigDecimal amount;

    private GrabRequestStatus status;

    private Long orderId;

    private PublishState publishState;

    private Integer publishAttempts;

    private LocalDateTime lastPublishTime;

    private LocalDateTime nextPublishTime;

    private Integer consumeAttempts;

    /** Redis 回补幂等标记 */
    private String redisToken;

    private RedisRollbackState redisRollbackState;

    private LocalDateTime expireTime;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
