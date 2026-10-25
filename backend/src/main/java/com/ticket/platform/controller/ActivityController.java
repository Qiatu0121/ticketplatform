package com.ticket.platform.controller;

import com.ticket.platform.common.Result;
import com.ticket.platform.entity.Activity;
import com.ticket.platform.service.ActivityService;
import com.ticket.platform.vo.ActivityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 活动浏览接口，匿名可访问。
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** 活动列表。 */
    @GetMapping("/list")
    public Result<List<Activity>> list() {
        return Result.ok(activityService.list());
    }

    /** 活动详情，包含票种列表。 */
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.getDetail(id));
    }
}
