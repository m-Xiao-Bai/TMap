package com.mu.transitmap.websocket;

import com.mu.transitmap.utils.JwtUtil;
import com.mu.transitmap.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手拦截器：从 URL query 参数提取鉴权信息
 */
@Component
public class AgentWebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisUtils redisUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            var params = UriComponentsBuilder.fromUri(request.getURI())
                    .build().getQueryParams().toSingleValueMap();

            String token = params.get("token");
            String anonToken = params.get("anon");
            String sessionIdStr = params.get("sessionId");

            Long userId = null;

            // 尝试解析登录用户的 JWT
            if (token != null && !token.isEmpty()) {
                try {
                    if (jwtUtil.validateToken(token)) {
                        userId = jwtUtil.getUserIdFromToken(token);
                        String storedToken = redisUtils.getToken(userId);
                        if (storedToken == null || !storedToken.equals(token)) {
                            userId = null; // token 已失效
                        }
                    }
                } catch (Exception ignored) {
                    // token 解析失败，视为匿名
                }
            }

            // 匿名用户必须有 anonToken
            if (userId == null && (anonToken == null || anonToken.isEmpty())) {
                return false;
            }

            attributes.put("userId", userId);
            attributes.put("anonToken", anonToken);

            if (sessionIdStr != null && !sessionIdStr.isEmpty()) {
                try {
                    attributes.put("sessionId", Long.parseLong(sessionIdStr));
                } catch (NumberFormatException ignored) {
                }
            }

            // 提取客户端 IP
            String ip = servletReq.getServletRequest().getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = servletReq.getRemoteAddress().getAddress().getHostAddress();
            }
            attributes.put("clientIp", ip);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
