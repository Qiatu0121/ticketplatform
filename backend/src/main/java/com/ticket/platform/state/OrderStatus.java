package com.ticket.platform.state;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.ticket.platform.common.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机。数据库保存 code，状态流转按 TRANSFER_MAP 校验。
 */
public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    PAID(1, "已支付"),
    CANCELED(2, "已取消"),
    VERIFIED(3, "已核销"),
    REFUNDED(4, "已退款");

    @EnumValue
    private final int code;

    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 当前状态 -> 允许流转到的状态；终态显式配置为空集合。 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSFER_MAP = Map.of(
            WAIT_PAY, Set.of(PAID, CANCELED),
            PAID, Set.of(VERIFIED, REFUNDED),
            CANCELED, Set.of(),
            VERIFIED, Set.of(),
            REFUNDED, Set.of()
    );

    /** 启动时校验每个状态都配置了流转规则。 */
    static {
        for (OrderStatus status : values()) {
            if (!TRANSFER_MAP.containsKey(status)) {
                throw new IllegalStateException("状态机缺规则：" + status + "，请检查 TRANSFER_MAP");
            }
        }
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 校验状态流转是否合法，非法时抛出 BizException。 */
    public void assertCanTransferTo(OrderStatus target) {
        Set<OrderStatus> allowed = TRANSFER_MAP.get(this);
        if (!allowed.contains(target)) {
            throw new BizException("非法状态流转：" + this.desc + " → " + target.desc);
        }
    }
}
