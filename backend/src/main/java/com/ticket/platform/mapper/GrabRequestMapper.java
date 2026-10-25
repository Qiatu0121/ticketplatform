package com.ticket.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.platform.entity.GrabRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 异步下单请求数据访问，包含 Outbox 抢占和幂等回补状态更新。
 */
public interface GrabRequestMapper extends BaseMapper<GrabRequest> {

    /** 行锁读取，用于消费者和看门狗之间的互斥。 */
    @Select("SELECT * FROM `grab_request` WHERE request_id = #{requestId} FOR UPDATE")
    GrabRequest selectByRequestIdForUpdate(@Param("requestId") String requestId);

    /**
     * 抢占一条待发布记录。
     * 已处于发布中且租约未过期的记录不会被抢占。
     */
    @Update("UPDATE `grab_request` "
            + "SET publish_state = 1, publish_attempts = publish_attempts + 1, "
            + "    last_publish_time = #{now}, update_time = #{now} "
            + "WHERE request_id = #{requestId} AND status = 0 "
            + "  AND (publish_state <> 1 OR last_publish_time IS NULL OR last_publish_time < #{staleBefore})")
    int claimForPublish(@Param("requestId") String requestId,
                        @Param("now") LocalDateTime now,
                        @Param("staleBefore") LocalDateTime staleBefore);

    /** 发布确认成功；请求仍未成功时按间隔再次发布。 */
    @Update("UPDATE `grab_request` "
            + "SET publish_state = 2, next_publish_time = #{nextPublishTime}, "
            + "    error_message = NULL, update_time = #{now} "
            + "WHERE request_id = #{requestId} AND status = 0")
    int markPublishConfirmed(@Param("requestId") String requestId,
                             @Param("nextPublishTime") LocalDateTime nextPublishTime,
                             @Param("now") LocalDateTime now);

    /** 发布失败，等待下一次重试。 */
    @Update("UPDATE `grab_request` "
            + "SET publish_state = 3, next_publish_time = #{nextPublishTime}, "
            + "    error_message = #{errorMessage}, update_time = #{now} "
            + "WHERE request_id = #{requestId} AND status = 0")
    int markPublishFailed(@Param("requestId") String requestId,
                          @Param("nextPublishTime") LocalDateTime nextPublishTime,
                          @Param("errorMessage") String errorMessage,
                          @Param("now") LocalDateTime now);

    /** 记录一次消费尝试。 */
    @Update("UPDATE `grab_request` SET consume_attempts = consume_attempts + 1, update_time = #{now} "
            + "WHERE request_id = #{requestId} AND status = 0")
    int incrementConsumeAttempts(@Param("requestId") String requestId,
                                 @Param("now") LocalDateTime now);

    /** Redis 回补完成后更新状态。 */
    @Update("UPDATE `grab_request` SET redis_rollback_state = 2, update_time = #{now} "
            + "WHERE request_id = #{requestId} AND redis_rollback_state = 1")
    int markRedisRollbackDone(@Param("requestId") String requestId,
                              @Param("now") LocalDateTime now);
}
