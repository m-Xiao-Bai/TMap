package com.mu.transitmap.schedule;

import com.mu.transitmap.service.impl.TicketOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TicketExpireSchedule {

    @Autowired
    private TicketOrderServiceImpl ticketOrderService;

    /**
     * 每 5 分钟执行一次：自动过期超时未支付和二维码过期的订单
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void expireOrders() {
        try {
            ticketOrderService.expireOrders();
        } catch (Exception e) {
            log.error("定时过期订单任务异常", e);
        }
    }
}
