package com.ticket.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.platform.common.BizException;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import com.ticket.platform.mapper.ActivityMapper;
import com.ticket.platform.mapper.ActivityTicketMapper;
import com.ticket.platform.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 票种详情缓存。与活动详情使用同一套互斥锁重建策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final String DETAIL_KEY = "ticket:detail:";
    private static final String LOCK_KEY = "ticket:lock:";
    private static final int MAX_RETRY = 20;
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_MILLIS = 50;

    private final ActivityMapper activityMapper;
    private final ActivityTicketMapper ticketMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 查询票种详情，优先读缓存。 */
    public TicketVO getTicketDetail(Long ticketId) {
        String cacheKey = DETAIL_KEY + ticketId;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            TicketVO vo = fromJson(cached, cacheKey);
            if (vo != null) {
                return vo;
            }
        }

        TicketVO vo = loadAndCache(ticketId, cacheKey, 0);
        if (vo == null) {
            throw new BizException("票种不存在");
        }
        return vo;
    }

    /** 重建缓存；重试次数达到上限后直接查库。 */
    private TicketVO loadAndCache(Long ticketId, String cacheKey, int retry) {
        String lockKey = LOCK_KEY + ticketId;

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查，避免持锁期间重复重建
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    TicketVO cachedVo = fromJson(cached, cacheKey);
                    if (cachedVo != null) {
                        return cachedVo;
                    }
                }

                TicketVO vo = queryDb(ticketId);
                if (vo == null) {
                    return null;
                }

                // TTL 加入随机分钟数，避免缓存集中过期
                long ttlMinutes = 10 + ThreadLocalRandom.current().nextInt(5);
                redisTemplate.opsForValue().set(
                        cacheKey, toJson(vo), Duration.ofMinutes(ttlMinutes));
                return vo;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        if (retry >= MAX_RETRY) {
            log.warn("等待缓存重建超时，直接查库 ticketId={}", ticketId);
            return queryDb(ticketId);
        }
        sleepQuietly(RETRY_INTERVAL_MILLIS);
        return loadAndCache(ticketId, cacheKey, retry + 1);
    }

    private TicketVO queryDb(Long ticketId) {
        log.debug("票种缓存未命中，查询数据库 ticketId={}", ticketId);

        ActivityTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            return null;
        }

        Activity activity = activityMapper.selectById(ticket.getActivityId());
        TicketVO vo = new TicketVO();
        vo.setId(ticket.getId());
        vo.setName(ticket.getName());
        vo.setPrice(ticket.getPrice());
        vo.setTotalStock(ticket.getTotalStock());
        vo.setStock(ticket.getStock());
        vo.setActivityName(activity == null ? null : activity.getName());
        return vo;
    }

    /** 反序列化缓存；内容损坏时删除 key 并按未命中处理。 */
    private TicketVO fromJson(String json, String cacheKey) {
        try {
            return objectMapper.readValue(json, TicketVO.class);
        } catch (JsonProcessingException e) {
            log.warn("缓存内容无法解析，已删除并重新查库。key={},原因={}", cacheKey, e.getMessage());
            redisTemplate.delete(cacheKey);
            return null;
        }
    }

    private String toJson(TicketVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("票种详情序列化失败", e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
