package com.mu.transitmap.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.LangChain4jAgentEngine;
import com.mu.transitmap.service.AgentEngineRouter;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent WebSocket Handler：处理聊天、中止、心跳等消息
 *
 * 增强：
 * - 匿名用户日限制（已实现）
 * - 登录用户分钟级限流（agent.rate_limit.per_user_per_min）
 * - IP 分钟级限流（agent.rate_limit.per_ip_per_min）
 * - 单用户最大并发 WS 连接（agent.ws.max_conn_per_user）
 * - 敏感词过滤（agent.security.forbidden_words）
 */
@Component
public class AgentWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private static final String ANON_DAILY_KEY_PREFIX = "agent:anon:daily:";
    private static final String USER_MIN_KEY_PREFIX = "agent:rl:user:";
    private static final String IP_MIN_KEY_PREFIX = "agent:rl:ip:";

    @Autowired
    private LangChain4jAgentEngine engine;

    @Autowired
    private AgentEngineRouter engineRouter;

    @Autowired
    private AgentSessionRegistry registry;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 使用 Spring 配置的 ObjectMapper（含 JacksonConfig 中的 Long→String 序列化），
     * 关键：avoids JS 在前端处理雪花 ID 时的精度丢失
     */
    @Autowired
    private ObjectMapper objectMapper;

    /** wsSessionId → 当前运行的 runId */
    private final Map<String, String> currentRunIds = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        Long sessionId = (Long) ws.getAttributes().get("sessionId");
        Long userId = (Long) ws.getAttributes().get("userId");
        String anonToken = (String) ws.getAttributes().get("anonToken");

        // 单用户最大并发 WS 连接数限制（设计文档 §9）
        int maxPerUser = configService.getConfigInt("agent.ws.max_conn_per_user", 3);
        String ownerKey = userId != null ? "u:" + userId : (anonToken != null ? "a:" + anonToken : null);
        if (ownerKey != null && maxPerUser > 0) {
            int existing = registry.countByOwner(ownerKey);
            if (existing >= maxPerUser) {
                log.warn("WS conn limit reached for owner={}, existing={}, max={}",
                        ownerKey, existing, maxPerUser);
                try {
                    // 自定义关闭码 4029 = 限流（见设计 §C 附录）
                    ws.close(new CloseStatus(4029, "TOO_MANY_CONNECTIONS"));
                } catch (Exception ignored) {}
                return;
            }
        }

        if (sessionId != null) {
            registry.add(sessionId, ws, ownerKey);
        }

        int heartbeatMs = configService.getConfigInt("agent.ws.heartbeat_interval_ms", 25000);
        sendJson(ws, Map.of("type", "ready", "heartbeatMs", heartbeatMs));
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {
        JsonNode body;
        try {
            body = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(ws, "INVALID_JSON", "消息格式错误");
            return;
        }

        String type = body.path("type").asText();
        switch (type) {
            case "chat" -> handleChat(ws, body);
            case "stop" -> handleStop(ws);
            case "ping" -> sendJson(ws, Map.of("type", "pong"));
            case "regenerate" -> handleRegenerate(ws, body);
            default -> sendError(ws, "UNKNOWN_TYPE", "未知消息类型: " + type);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
        sendError(ws, "NOT_IMPLEMENTED", "语音功能暂未开放");
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        log.warn("WebSocket transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        handleStop(ws);
        registry.remove(ws);
        currentRunIds.remove(ws.getId());
    }

    private void handleChat(WebSocketSession ws, JsonNode body) {
        Long userId = (Long) ws.getAttributes().get("userId");
        String anonToken = (String) ws.getAttributes().get("anonToken");
        Long sessionId = (Long) ws.getAttributes().get("sessionId");
        String clientIp = (String) ws.getAttributes().get("clientIp");
        String content = body.path("content").asText("");

        // 1. 匿名用户：检查日发送次数限制
        if (userId == null && anonToken != null && !anonToken.isEmpty()) {
            int limit = configService.getConfigInt("agent.anon.daily_message_limit", 10);
            if (limit > 0) {
                String dailyKey = ANON_DAILY_KEY_PREFIX + anonToken + ":" + LocalDate.now();
                Long currentCount = safeIncr(dailyKey, 25 * 3600L);
                if (currentCount != null && currentCount > limit) {
                    sendJson(ws, Map.of(
                            "type", "error",
                            "code", "ANON_DAILY_LIMIT",
                            "message", "未登录用户每天最多可发送 " + limit + " 条消息，登录后无限制",
                            "limit", limit,
                            "used", currentCount - 1
                    ));
                    sendJson(ws, Map.of("type", "done", "messageId", 0,
                            "tokensIn", 0, "tokensOut", 0));
                    return;
                }
            }
        }

        // 2. 登录用户分钟级限流
        if (userId != null) {
            int perUserMin = configService.getConfigInt("agent.rate_limit.per_user_per_min", 30);
            if (perUserMin > 0) {
                String key = USER_MIN_KEY_PREFIX + userId + ":" + (System.currentTimeMillis() / 60_000);
                Long c = safeIncr(key, 65L);
                if (c != null && c > perUserMin) {
                    sendRateLimited(ws, "USER_RATE_LIMIT",
                            "请求过于频繁，每分钟最多 " + perUserMin + " 条，请稍后再试");
                    return;
                }
            }
        }

        // 3. IP 分钟级限流（不论登录与否）
        if (clientIp != null && !clientIp.isEmpty()) {
            int perIpMin = configService.getConfigInt("agent.rate_limit.per_ip_per_min", 60);
            if (perIpMin > 0) {
                String key = IP_MIN_KEY_PREFIX + clientIp + ":" + (System.currentTimeMillis() / 60_000);
                Long c = safeIncr(key, 65L);
                if (c != null && c > perIpMin) {
                    sendRateLimited(ws, "IP_RATE_LIMIT",
                            "当前网络访问过于频繁，请稍后再试");
                    return;
                }
            }
        }

        // 4. 敏感词过滤
        String hit = matchForbiddenWord(content);
        if (hit != null) {
            sendJson(ws, Map.of(
                    "type", "error",
                    "code", "FORBIDDEN_CONTENT",
                    "message", "您的提问包含不当内容，请调整后再试"
            ));
            sendJson(ws, Map.of("type", "done", "messageId", 0,
                    "tokensIn", 0, "tokensOut", 0));
            log.info("Forbidden word hit: '{}' in content (userId={}, anon={})", hit, userId, anonToken);
            return;
        }

        AgentContext ctx = AgentContext.builder()
                .wsSessionId(ws.getId())
                .userId(userId)
                .anonToken(anonToken)
                .chatSessionId(sessionId)
                .userMessage(content)
                .lat(body.path("lat").asDouble(0))
                .lng(body.path("lng").asDouble(0))
                .inputMethod(body.path("inputMethod").asText("text"))
                .clientIp(clientIp)
                .build();

        String runId = engineRouter.runAsync(ctx, msg -> sendJson(ws, msg));
        currentRunIds.put(ws.getId(), runId);
    }

    private void handleStop(WebSocketSession ws) {
        String runId = currentRunIds.remove(ws.getId());
        if (runId != null) {
            engineRouter.cancel(runId);
        }
    }

    private void handleRegenerate(WebSocketSession ws, JsonNode body) {
        Long messageId = body.path("messageId").asLong(0);
        Long sessionId = (Long) ws.getAttributes().get("sessionId");
        Long userId = (Long) ws.getAttributes().get("userId");
        String anonToken = (String) ws.getAttributes().get("anonToken");
        String clientIp = (String) ws.getAttributes().get("clientIp");

        if (messageId <= 0 || sessionId == null) {
            sendError(ws, "INVALID_PARAMS", "缺少必要参数");
            return;
        }

        AgentContext ctx = AgentContext.builder()
                .wsSessionId(ws.getId())
                .userId(userId)
                .anonToken(anonToken)
                .chatSessionId(sessionId)
                .clientIp(clientIp)
                .regenerateMessageId(messageId)
                .build();

        String runId = engineRouter.regenerate(ctx, msg -> sendJson(ws, msg));
        currentRunIds.put(ws.getId(), runId);
    }

    // ==== 辅助 ====

    private Long safeIncr(String key, long ttlSec) {
        try {
            return redisUtils.incrementKey(key, ttlSec);
        } catch (Exception e) {
            log.warn("Redis incr failed for key={}, 放行", key, e);
            return null;
        }
    }

    private void sendRateLimited(WebSocketSession ws, String code, String message) {
        sendJson(ws, Map.of("type", "error", "code", code, "message", message));
        sendJson(ws, Map.of("type", "done", "messageId", 0, "tokensIn", 0, "tokensOut", 0));
    }

    @SuppressWarnings("unchecked")
    private String matchForbiddenWord(String content) {
        if (content == null || content.isEmpty()) return null;
        List<String> words = (List<String>) configService.getConfigObject(
                "agent.security.forbidden_words", new TypeReference<List<String>>() {});
        if (words == null || words.isEmpty()) return null;
        String lower = content.toLowerCase();
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            if (lower.contains(w.toLowerCase())) return w;
        }
        return null;
    }

    private void sendJson(WebSocketSession ws, Object data) {
        if (ws.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(data);
                ws.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("Failed to send WS message: {}", e.getMessage());
            }
        }
    }

    private void sendError(WebSocketSession ws, String code, String message) {
        sendJson(ws, Map.of("type", "error", "code", code, "message", message));
    }
}
