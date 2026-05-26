package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.entity.TicketOrder;

import java.util.List;
import java.util.Map;

public interface ITicketOrderService extends IService<TicketOrder> {
    List<Map<String, Object>> createOrders(Long userId, Long startStationId, Long endStationId, int quantity);
    void payOrder(Long userId, Long orderId);
    void requestRefund(Long userId, Long orderId, String reason);
    Map<String, Object> refreshQrCode(Long userId, Long orderId);
    Map<String, Object> verifyQrCode(String qrCode);
    void expireOrders();
    List<TicketOrder> getUserOrders(Long userId);

    // 管理员操作
    void approveRefund(Long orderId, int action, Integer roleCode);
    void adminUpdateOrder(Long orderId, Map<String, Object> body, Integer roleCode);
    void adminDeleteOrder(Long orderId, Integer roleCode);
}
