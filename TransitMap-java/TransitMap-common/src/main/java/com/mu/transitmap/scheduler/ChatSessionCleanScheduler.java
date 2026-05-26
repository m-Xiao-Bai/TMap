package com.mu.transitmap.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.entity.ChatIntentLog;
import com.mu.transitmap.entity.ChatMessage;
import com.mu.transitmap.entity.ChatSession;
import com.mu.transitmap.mapper.ChatIntentLogMapper;
import com.mu.transitmap.mapper.ChatMessageMapper;
import com.mu.transitmap.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天会话定时清理：删除过期会话及其关联数据
 */
@Component
public class ChatSessionCleanScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionCleanScheduler.class);

    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private ChatIntentLogMapper chatIntentLogMapper;

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpiredSessions() {
        log.info("开始清理过期聊天会话...");

        List<ChatSession> expired = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .lt(ChatSession::getExpireAt, LocalDateTime.now())
                        .select(ChatSession::getId));

        if (expired.isEmpty()) {
            log.info("没有过期会话需要清理");
            return;
        }

        List<Long> sessionIds = expired.stream().map(ChatSession::getId).toList();

        // 级联删除消息
        int msgDeleted = 0;
        for (Long sessionId : sessionIds) {
            msgDeleted += chatMessageMapper.delete(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, sessionId));
        }

        // 级联删除节点日志
        int logDeleted = 0;
        for (Long sessionId : sessionIds) {
            logDeleted += chatIntentLogMapper.delete(
                    new LambdaQueryWrapper<ChatIntentLog>()
                            .eq(ChatIntentLog::getSessionId, sessionId));
        }

        // 删除会话
        for (Long id : sessionIds) {
            chatSessionMapper.deleteById(id);
        }

        log.info("清理完成：删除 {} 个过期会话，{} 条消息，{} 条节点日志",
                sessionIds.size(), msgDeleted, logDeleted);
    }
}
