package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mu.transitmap.entity.SystemMessage;
import com.mu.transitmap.mapper.SystemMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<SystemMessageMapper, SystemMessage> {

    // ═══════════════════════════════════════════
    //  发送消息
    // ═══════════════════════════════════════════

    /**
     * 发送订单相关消息
     */
    public void sendOrderMessage(String type, String title, String content, Long userId, Long orderId) {
        SystemMessage msg = new SystemMessage()
                .setType(type)
                .setTitle(title)
                .setContent(content)
                .setUserId(userId)
                .setOrderId(orderId)
                .setTarget(3) // 双方可见
                .setIsRead(0);
        save(msg);
        log.debug("消息已发送 type={} userId={} orderId={}", type, userId, orderId);
    }

    /**
     * 发送系统异常消息（仅管理员可见）
     */
    public void sendSystemError(String errorType, String url, String detail) {
        String content = String.format("接口: %s\n异常: %s", url, detail);
        if (content.length() > 500) content = content.substring(0, 500);
        SystemMessage msg = new SystemMessage()
                .setType("SYSTEM_ERROR")
                .setTitle("系统异常: " + errorType)
                .setContent(content)
                .setTarget(2) // 仅管理员
                .setIsRead(0);
        save(msg);
        log.debug("系统异常消息已记录 type={}", errorType);
    }

    /**
     * 用户联系管理员
     */
    public void sendUserContact(Long userId, String content) {
        SystemMessage msg = new SystemMessage()
                .setType("USER_CONTACT")
                .setTitle("用户来信")
                .setContent(content)
                .setUserId(userId)
                .setTarget(2) // 仅管理员可见
                .setIsRead(0);
        save(msg);
        log.debug("用户联系消息已发送 userId={}", userId);
    }

    // ═══════════════════════════════════════════
    //  查询消息
    // ═══════════════════════════════════════════

    /**
     * 用户消息列表（target 包含 1 或 3，且 userId 匹配）
     */
    public Page<SystemMessage> getUserMessages(Long userId, int page, int size) {
        LambdaQueryWrapper<SystemMessage> wrapper = new LambdaQueryWrapper<SystemMessage>()
                .eq(SystemMessage::getUserId, userId)
                .in(SystemMessage::getTarget, 1, 3)
                .orderByDesc(SystemMessage::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 管理员消息列表（target 包含 2 或 3）
     */
    public Page<SystemMessage> getAdminMessages(int page, int size, String type, Integer isRead) {
        LambdaQueryWrapper<SystemMessage> wrapper = new LambdaQueryWrapper<SystemMessage>()
                .in(SystemMessage::getTarget, 2, 3);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(SystemMessage::getType, type);
        }
        if (isRead != null) {
            wrapper.eq(SystemMessage::getIsRead, isRead);
        }
        wrapper.orderByDesc(SystemMessage::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 用户未读消息数
     */
    public long getUnreadCount(Long userId) {
        return count(new LambdaQueryWrapper<SystemMessage>()
                .eq(SystemMessage::getUserId, userId)
                .in(SystemMessage::getTarget, 1, 3)
                .eq(SystemMessage::getIsRead, 0));
    }

    /**
     * 管理员未读消息数
     */
    public long getAdminUnreadCount() {
        return count(new LambdaQueryWrapper<SystemMessage>()
                .in(SystemMessage::getTarget, 2, 3)
                .eq(SystemMessage::getIsRead, 0));
    }

    // ═══════════════════════════════════════════
    //  标记已读
    // ═══════════════════════════════════════════

    public void markAsRead(Long id) {
        update(new LambdaUpdateWrapper<SystemMessage>()
                .eq(SystemMessage::getId, id)
                .set(SystemMessage::getIsRead, 1));
    }

    public void markAllAsReadForUser(Long userId) {
        update(new LambdaUpdateWrapper<SystemMessage>()
                .eq(SystemMessage::getUserId, userId)
                .in(SystemMessage::getTarget, 1, 3)
                .eq(SystemMessage::getIsRead, 0)
                .set(SystemMessage::getIsRead, 1));
    }

    public void markAllAsReadForAdmin() {
        update(new LambdaUpdateWrapper<SystemMessage>()
                .in(SystemMessage::getTarget, 2, 3)
                .eq(SystemMessage::getIsRead, 0)
                .set(SystemMessage::getIsRead, 1));
    }
}
