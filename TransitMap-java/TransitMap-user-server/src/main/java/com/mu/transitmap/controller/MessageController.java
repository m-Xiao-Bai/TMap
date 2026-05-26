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
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageServiceImpl messageService;

    @GetMapping("/list")
    public Result<Page<SystemMessage>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return Result.success(messageService.getUserMessages(userId, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Map<String, Object> result = new HashMap<>();
        result.put("count", messageService.getUnreadCount(userId));
        return Result.success(result);
    }

    @PutMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markAsRead(id);
        return Result.success(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        messageService.markAllAsReadForUser(userId);
        return Result.success(null);
    }

    @PostMapping("/contact")
    public Result<Void> contact(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        messageService.sendUserContact(userId, content.trim());
        return Result.success(null);
    }
}
