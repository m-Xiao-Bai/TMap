package com.mu.transitmap.aop;

import com.mu.transitmap.annotation.RequireRole;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RoleCheckAspect {

    @Around("@annotation(com.mu.transitmap.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        HttpServletRequest request = attributes.getRequest();
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        int[] allowedRoles = requireRole.value();

        boolean allowed = Arrays.stream(allowedRoles).anyMatch(r -> r == roleCode);
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }
}
