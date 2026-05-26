package com.mu.transitmap.interceptor;

import com.mu.transitmap.utils.RedisUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口限流拦截器：基于 IP 的滑动窗口限流
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisUtils redisUtils;

    /** 每个窗口允许的最大请求数 */
    @Value("${rate-limit.max-requests:60}")
    private int maxRequests;

    /** 窗口时长（秒） */
    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri.contains("/error")) {
            return true;
        }

        String ip = getClientIp(request);
        String key = "rate_limit:" + ip;

        Long count = redisUtils.incrementKey(key, windowSeconds);
        if (count != null && count > maxRequests) {
            log.warn("接口限流触发 ip={} count={} url={}", ip, count, uri);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
                response.getWriter().flush();
            } catch (Exception e) {
                log.debug("限流响应写入失败: {}", e.getMessage());
            }
            return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
