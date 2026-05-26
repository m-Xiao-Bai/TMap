package com.mu.transitmap.handler;

import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.MessageServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    @org.springframework.context.annotation.Lazy
    private MessageServiceImpl messageService;

    private String getUrl(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "unknown";
    }

    private void recordSystemError(String errorType, HttpServletRequest request, Exception e) {
        try {
            String url = getUrl(request);
            String detail = e.getClass().getSimpleName() + ": " +
                    (e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 200)) : "null");
            messageService.sendSystemError(errorType, url, detail);
        } catch (Exception ex) {
            log.debug("记录系统异常消息失败", ex);
        }
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("业务异常: code={}, message={}", code.getCode(), e.getMessage());
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(Result.fail(code.getCode(), e.getMessage()));
    }

    /**
     * JSON 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity
                .status(ErrorCode.PARAM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_ERROR.getCode(), msg));
    }

    /**
     * 请求参数缺失异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity
                .status(ErrorCode.PARAM_MISSING.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_MISSING.getCode(), "缺少必要参数：" + e.getParameterName()));
    }

    /**
     * 校验错误
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("参数校验失败");
        log.warn("约束校验失败: {}", msg);
        return ResponseEntity
                .status(ErrorCode.PARAM_MISSING.getHttpStatus())
                .body(Result.fail(ErrorCode.PARAM_MISSING.getCode(), msg));
    }

    /**
     * 邮件发送异常
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Result<Void>> handleMailException(MailException e) {
        log.error("邮件发送失败", e);
        return ResponseEntity
                .status(ErrorCode.EMAIL_SEND_FAILED.getHttpStatus())
                .body(Result.fail(ErrorCode.EMAIL_SEND_FAILED.getCode(), ErrorCode.EMAIL_SEND_FAILED.getMessage()));
    }

    /**
     * 请求方法不支持（如GET请求访问了POST接口）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {} {}", e.getMethod(), e.getMessage());
        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持：" + e.getMethod()));
    }

    /**
     * 请求体不可读（JSON格式错误等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求数据格式错误，请检查JSON格式"));
    }

    /**
     * 数据库访问异常
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        log.error("数据库访问异常", e);
        recordSystemError("DataAccessException", request, e);
        return ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "数据操作失败，请稍后再试"));
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {}", e.getMessage(), e);
        recordSystemError("Exception", request, e);
        return ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "系统异常，请稍后再试"));
    }
}
