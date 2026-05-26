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
            @RequestParam(required = false) Integer isRead,
            HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.success(messageService.getAdminMessages(pageNum, pageSize, type, isRead));
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
