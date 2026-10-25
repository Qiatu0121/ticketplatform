package com.ticket.platform.state;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 异步下单请求状态。
 */
public enum GrabRequestStatus {

    PENDING(0, "处理中"),
    SUCCESS(1, "已创建订单"),
    FAILED(2, "处理失败");

    @EnumValue
    private final int code;

    private final String desc;

    GrabRequestStatus(int code, String desc) {
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
