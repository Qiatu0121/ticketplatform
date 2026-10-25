package com.ticket.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.platform.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 订单数据访问。 */
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 仅当订单当前状态等于 fromStatus 时才更新状态。
     * 返回 0 表示状态已被其他事务改变，调用方不能再执行库存回补等后续动作。
     */
    @Update("UPDATE `order` SET status = #{toStatus} "
            + "WHERE id = #{orderId} AND status = #{fromStatus}")
    int updateStatusIfMatch(@Param("orderId") Long orderId,
                            @Param("fromStatus") int fromStatus,
                            @Param("toStatus") int toStatus);

    /** 支付：带上用户归属和旧状态条件，同时写入支付时间。 */
    @Update("UPDATE `order` SET status = #{toStatus}, pay_time = #{payTime} "
            + "WHERE id = #{orderId} AND user_id = #{userId} AND status = #{fromStatus}")
    int payIfMatch(@Param("orderId") Long orderId,
                   @Param("userId") Long userId,
                   @Param("fromStatus") int fromStatus,
                   @Param("toStatus") int toStatus,
                   @Param("payTime") LocalDateTime payTime);
}
