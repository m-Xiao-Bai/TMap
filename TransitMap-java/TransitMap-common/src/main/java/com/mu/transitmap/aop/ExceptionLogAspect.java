package com.mu.transitmap.aop;

import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ExceptionLogAspect {
    /**
     * 拦截 Controller / Service 层异常
     */
    @AfterThrowing(
            pointcut = "execution(* com.mu..*(..))",
            throwing = "ex"
    )
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        log.error(
                "❌ 异常发生 | 类: {} | 方法: {} | 异常类型: {} | 异常信息: {}",
                className,
                methodName,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );
    }
}
