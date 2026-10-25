package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** activity_ticket 表：某个活动的票种与库存。 */
@Data
@TableName("`activity_ticket`")
public class ActivityTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String name;

    private BigDecimal price;

    private Integer totalStock;

    /** 当前余票，由 Redis 预扣和数据库条件更新共同维护 */
    private Integer stock;

    private LocalDateTime createTime;
}
