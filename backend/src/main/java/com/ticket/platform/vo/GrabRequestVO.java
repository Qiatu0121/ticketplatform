package com.ticket.platform.vo;

import com.ticket.platform.entity.GrabRequest;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步下单请求的状态视图。
 */
@Data
public class GrabRequestVO {

    private String requestId;

    /** PENDING / SUCCESS / FAILED */
    private String status;

    private Long orderId;

    private String message;

    private LocalDateTime createTime;

    public static GrabRequestVO from(GrabRequest request) {
        GrabRequestVO vo = new GrabRequestVO();
        vo.setRequestId(request.getRequestId());
        vo.setStatus(request.getStatus().name());
        vo.setOrderId(request.getOrderId());
        vo.setMessage(request.getErrorMessage());
        vo.setCreateTime(request.getCreateTime());
        return vo;
    }
}
