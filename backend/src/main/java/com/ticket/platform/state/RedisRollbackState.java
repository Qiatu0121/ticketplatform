package com.ticket.platform.state;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 请求失败后的 Redis 库存回补状态。
 */
public enum RedisRollbackState {

    NONE(0, "无需回补"),
    PENDING(1, "待回补"),
    DONE(2, "已回补");

    @EnumValue
    private final int code;

    private final String desc;

    RedisRollbackState(int code, String desc) {
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
