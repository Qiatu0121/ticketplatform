package com.ticket.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 票种详情视图：票种信息与所属活动名称。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketVO {

    private String activityName;

    private Long id;

    private String name;

    private BigDecimal price;

    private Integer totalStock;

    private Integer stock;
}
