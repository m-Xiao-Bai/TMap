package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.entity.TicketOrder;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.TicketOrderServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/manage/ticket-order")
public class TicketOrderManageController {

    @Autowired
    private TicketOrderServiceImpl ticketOrderService;

    @GetMapping("/list")
    public Result<Page<TicketOrder>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<TicketOrder> wrapper = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like(TicketOrder::getOrderNo, orderNo);
        }
        if (status != null) {
            wrapper.eq(TicketOrder::getStatus, status);
        }
        wrapper.orderByDesc(TicketOrder::getOrderTime);
        return Result.success(ticketOrderService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/{id}")
    public Result<TicketOrder> detail(@PathVariable Long id, HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        TicketOrder order = ticketOrderService.getById(id);
        if (order == null) throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        return Result.success(order);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", ticketOrderService.count());
        result.put("unpaid", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 0)));
        result.put("paid", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 1)));
        result.put("used", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 2)));
        result.put("expired", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 3)));
        result.put("refunded", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 4)));
        result.put("refundPending", ticketOrderService.count(new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getStatus, 5)));
        return Result.success(result);
    }

    @PostMapping("/{id}/refund-approve")
    public Result<Void> approveRefund(@PathVariable Long id,
                                       @RequestBody Map<String, Integer> body,
                                       HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Integer action = body.get("action");
        if (action == null) throw new BusinessException(ErrorCode.PARAM_MISSING);
        ticketOrderService.approveRefund(id, action, roleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Void> updateOrder(@PathVariable Long id,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        ticketOrderService.adminUpdateOrder(id, body, roleCode);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteOrder(@PathVariable Long id, HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        ticketOrderService.adminDeleteOrder(id, roleCode);
        return Result.success(null);
    }
}
