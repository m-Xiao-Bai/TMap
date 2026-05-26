package com.mu.transitmap.interceptor;

import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.utils.JwtUtil;
import com.mu.transitmap.utils.RedisUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtils redisUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING);
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String storedToken = redisUtils.getToken(userId);
        if (storedToken == null || !storedToken.equals(token)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        request.setAttribute("userId", userId);
        request.setAttribute("roleCode", jwtUtil.getRoleCodeFromToken(token));
        return true;
    }
}
