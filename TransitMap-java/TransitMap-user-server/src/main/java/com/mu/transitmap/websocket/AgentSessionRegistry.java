package com.mu.transitmap.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 会话注册表
 *
 * 维护三个维度：
 * - sessionId (chat_session.id) → WebSocketSession，用于按会话推送
 * - wsSessionId → ownerKey ("u:userId" 或 "a:anonToken")，用于统计单用户并发
 * - 累计指标：连入/断开/当前在线
 */
@Component
public class AgentSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, Long> wsToSession = new ConcurrentHashMap<>();
    private final Map<String, String> wsToOwner = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> ownerToWs = new ConcurrentHashMap<>();

    private final AtomicLong totalOpened = new AtomicLong(0);
    private final AtomicLong totalClosed = new AtomicLong(0);

    public void add(Long sessionId, WebSocketSession ws, String ownerKey) {
        sessionMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(ws);
        wsToSession.put(ws.getId(), sessionId);
        if (ownerKey != null) {
            wsToOwner.put(ws.getId(), ownerKey);
            ownerToWs.computeIfAbsent(ownerKey, k -> ConcurrentHashMap.newKeySet()).add(ws.getId());
        }
        totalOpened.incrementAndGet();
    }

    /** 兼容旧调用 */
    public void add(Long sessionId, WebSocketSession ws) {
        add(sessionId, ws, null);
    }

    public void remove(WebSocketSession ws) {
        Long sessionId = wsToSession.remove(ws.getId());
        if (sessionId != null) {
            Set<WebSocketSession> sessions = sessionMap.get(sessionId);
            if (sessions != null) {
                sessions.remove(ws);
                if (sessions.isEmpty()) sessionMap.remove(sessionId);
            }
        }
        String owner = wsToOwner.remove(ws.getId());
        if (owner != null) {
            Set<String> ids = ownerToWs.get(owner);
            if (ids != null) {
                ids.remove(ws.getId());
                if (ids.isEmpty()) ownerToWs.remove(owner);
            }
        }
        totalClosed.incrementAndGet();
    }

    public Set<WebSocketSession> getBySessionId(Long sessionId) {
        return sessionMap.getOrDefault(sessionId, Set.of());
    }

    public int countByOwner(String ownerKey) {
        if (ownerKey == null) return 0;
        Set<String> ids = ownerToWs.get(ownerKey);
        return ids == null ? 0 : ids.size();
    }

    public int size() {
        return wsToSession.size();
    }

    public boolean hasSession(Long sessionId) {
        Set<WebSocketSession> sessions = sessionMap.get(sessionId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 监控指标快照（供管理后台展示）
     */
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currentOnline", wsToSession.size());
        m.put("uniqueOwners", ownerToWs.size());
        m.put("totalOpened", totalOpened.get());
        m.put("totalClosed", totalClosed.get());
        return m;
    }
}
