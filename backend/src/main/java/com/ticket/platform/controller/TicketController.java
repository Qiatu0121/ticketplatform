package com.ticket.platform.controller;

import com.ticket.platform.common.Result;
import com.ticket.platform.service.TicketService;
import com.ticket.platform.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 票种详情接口，匿名可访问。
 */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/{id}")
    public Result<TicketVO> detail(@PathVariable Long id) {
        return Result.ok(ticketService.getTicketDetail(id));
    }
}
