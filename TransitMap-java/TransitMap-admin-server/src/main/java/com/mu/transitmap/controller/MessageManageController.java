package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.entity.SystemMessage;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.MessageServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/manage/message")
public class MessageManageController {

    @Autowired
    private MessageServiceImpl messageService;

    @GetMapping("/list")
    public Result<Page<SystemMessage>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) Integer isRead,
            HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.success(messageService.getAdminMessages(pageNum, pageSize, type, isRead, types));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("count", messageService.getAdminUnreadCount());
        return Result.success(result);
    }

    @GetMapping("/unread-by-category")
    public Result<Map<String, Long>> unreadByCategory(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Map<String, Long> result = new HashMap<>();
        // 订单消息
        result.put("order", messageService.getUnreadCountByTypes(
                "ORDER_CREATED", "ORDER_PAID", "ORDER_USED", "ORDER_EXPIRED", "ORDER_REFUNDED", "REFUND_PENDING"));
        // 用户消息
        result.put("user", messageService.getUnreadCountByTypes(
                "USER_CONTACT", "USER_FEEDBACK", "AGENT_CITY_REQUEST"));
        // 系统通知
        result.put("system", messageService.getUnreadCountByTypes(
                "SYSTEM_NOTICE", "SYSTEM_UPDATE", "SYSTEM_CONFIG"));
        // 系统异常
        result.put("error", messageService.getUnreadCountByTypes(
                "SYSTEM_ERROR", "SYSTEM_WARNING"));
        // 爬虫通知
        result.put("crawler", messageService.getUnreadCountByTypes(
                "CRAWLER_COMPLETE", "CRAWLER_FAILED", "CRAWLER_REVIEW"));
        return Result.success(result);
    }

    @PutMapping("/read-category")
    public Result<Void> markCategoryRead(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String types = body.get("types");
        if (types != null && !types.isEmpty()) {
            messageService.markAsReadByTypes(types.split(","));
        }
        return Result.success(null);
    }

    @PutMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        messageService.markAsRead(id);
        return Result.success(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        messageService.markAllAsReadForAdmin();
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        messageService.removeById(id);
        return Result.success(null);
    }
}
