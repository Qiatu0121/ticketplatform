package com.ticket.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.platform.entity.ActivityTicket;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 票种数据访问，包含原子扣减与回补库存。
 */
public interface ActivityTicketMapper extends BaseMapper<ActivityTicket> {

    /** 条件扣减库存，stock 不足时不更新并返回 0。 */
    @Update("UPDATE activity_ticket SET stock = stock - #{count}"
            + " WHERE id = #{ticketId} AND stock >= #{count}")
    int deductStock(@Param("ticketId") Long ticketId, @Param("count") int count);

    /** 原子回补库存。 */
    @Update("UPDATE activity_ticket SET stock = stock + #{count} WHERE id = #{ticketId}")
    int incrementStock(@Param("ticketId") Long ticketId, @Param("count") int count);
}
