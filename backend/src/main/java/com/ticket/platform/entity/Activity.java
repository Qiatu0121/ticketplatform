package com.ticket.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** activity 表：活动基本信息。 */
@Data
@TableName("`activity`")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime startTime;

    private String location;

    private String detail;

    /** 0 未开始，1 进行中，2 已结束 */
    private Integer status;

    private LocalDateTime createTime;
}
