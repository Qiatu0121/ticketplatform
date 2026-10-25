package com.ticket.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.vo.ActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 活动详情缓存。缓存未命中时用 Redis 互斥锁控制重建，避免缓存击穿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final String DETAIL_KEY = "activity:detail:";
    private static final String LOCK_KEY = "activity:lock:";
    private static final int MAX_RETRY = 20;
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_MILLIS = 50;

    private final StringRedisTemplate redisTemplate;
    private final ActivityMapper activityMapper;
    private final ActivityTicketMapper ticketMapper;
    private final ObjectMapper objectMapper;

    /** 查询活动详情，优先读缓存。 */
    public ActivityVO getDetail(Long activityId) {
        String cacheKey = DETAIL_KEY + activityId;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            ActivityVO vo = fromJson(cached, cacheKey);
            if (vo != null) {
                return vo;
            }
        }

        ActivityVO vo = loadAndCache(activityId, cacheKey, 0);
        if (vo == null) {
            throw new BizException("活动不存在");
        }
        return vo;
    }

    /** 重建缓存；retry 为抢锁重试次数，超过上限后直接查库兜底。 */
    private ActivityVO loadAndCache(Long activityId, String cacheKey, int retry) {
        String lockKey = LOCK_KEY + activityId;

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：抢到锁期间缓存可能已被其他请求写入
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    ActivityVO cachedVo = fromJson(cached, cacheKey);
                    if (cachedVo != null) {
                        return cachedVo;
                    }
                }

                ActivityVO vo = queryDb(activityId);
                if (vo == null) {
                    return null;
                }

                // TTL 加入随机分钟数，降低同时过期导致的缓存雪崩风险
                long ttlMinutes = 10 + ThreadLocalRandom.current().nextInt(5);
                redisTemplate.opsForValue().set(
                        cacheKey,
                        toJson(vo),
                        Duration.ofMinutes(ttlMinutes));
                return vo;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        if (retry >= MAX_RETRY) {
            log.warn("等待缓存重建超时，直接查库 activityId={}", activityId);
            return queryDb(activityId);
        }
        sleepQuietly(RETRY_INTERVAL_MILLIS);
        return loadAndCache(activityId, cacheKey, retry + 1);
    }

    /** 查询活动及其票种。 */
    private ActivityVO queryDb(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return null;
        }
        List<ActivityTicket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<ActivityTicket>()
                        .eq(ActivityTicket::getActivityId, activityId));
        return new ActivityVO(activity, tickets);
    }

    /** 反序列化缓存；内容损坏时删除 key 并按未命中处理。 */
    private ActivityVO fromJson(String json, String cacheKey) {
        try {
            return objectMapper.readValue(json, ActivityVO.class);
        } catch (JsonProcessingException e) {
            log.warn("缓存内容无法解析，已删除并重新查库。key={},原因={}", cacheKey, e.getMessage());
            redisTemplate.delete(cacheKey);
            return null;
        }
    }

    private String toJson(ActivityVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("活动详情序列化失败", e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 活动列表，按开始时间倒序。 */
    public List<Activity> list() {
        return activityMapper.selectList(
                new LambdaQueryWrapper<Activity>()
                        .orderByDesc(Activity::getStartTime));
    }

    /** 删除活动详情缓存，供管理端新增票种后刷新详情。 */
    public void evictDetail(Long activityId) {
        try {
            redisTemplate.delete(DETAIL_KEY + activityId);
        } catch (Exception e) {
            log.warn("删除活动详情缓存失败 activityId={}", activityId, e);
        }
    }
}
