package com.ticket.platform.vo;

import com.ticket.platform.entity.Activity;
import com.ticket.platform.entity.ActivityTicket;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 活动详情视图：活动信息与票种列表，作为 Redis 缓存内容直接序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityVO {

    private Activity activity;

    private List<ActivityTicket> tickets;
}
