package com.ticket.platform.state;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Outbox 消息发布状态。
 */
public enum PublishState {

    NEW(0, "待发布"),
    PUBLISHING(1, "发布中"),
    CONFIRMED(2, "已确认"),
    RETRY_WAIT(3, "等待重发");

    @EnumValue
    private final int code;

    private final String desc;

    PublishState(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
