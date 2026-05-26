package com.mu.transitmap.controller;

import com.mu.transitmap.entity.TicketOrder;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CaptchaImageService;
import com.mu.transitmap.service.impl.TicketOrderServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketOrderServiceImpl ticketOrderService;
    @Autowired
    private CaptchaImageService captchaImageService;

    @PostMapping("/create")
    public Result<List<Map<String, Object>>> createOrders(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        // 验证码校验
        String captchaKey = (String) body.get("captchaKey");
        String captchaCode = (String) body.get("captchaCode");
        if (captchaKey == null || captchaCode == null || captchaKey.isEmpty() || captchaCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        if (!captchaImageService.validate(captchaKey, captchaCode)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }

        Long startStationId = body.get("startStationId") != null
                ? Long.parseLong(String.valueOf(body.get("startStationId"))) : null;
        Long endStationId = body.get("endStationId") != null
                ? Long.parseLong(String.valueOf(body.get("endStationId"))) : null;
        int quantity = body.get("quantity") != null
                ? Integer.parseInt(String.valueOf(body.get("quantity"))) : 1;

        if (startStationId == null || endStationId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }

        return Result.success(ticketOrderService.createOrders(userId, startStationId, endStationId, quantity));
    }

    @PostMapping("/pay")
    public Result<Void> payOrder(@RequestBody Map<String, Long> body,
                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Long orderId = body.get("orderId");
        if (orderId == null) throw new BusinessException(ErrorCode.PARAM_MISSING);
        ticketOrderService.payOrder(userId, orderId);
        return Result.success(null);
    }

    @PostMapping("/refund")
    public Result<Void> refundOrder(@RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Long orderId = body.get("orderId") != null ? Long.parseLong(String.valueOf(body.get("orderId"))) : null;
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : "";
        if (orderId == null) throw new BusinessException(ErrorCode.PARAM_MISSING);
        ticketOrderService.requestRefund(userId, orderId, reason);
        return Result.success(null);
    }

    @GetMapping("/my-orders")
    public Result<List<TicketOrder>> getMyOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return Result.success(ticketOrderService.getUserOrders(userId));
    }

    @GetMapping("/qr/{qrCode}")
    public Result<Map<String, Object>> getQrInfo(@PathVariable String qrCode) {
        return Result.success(ticketOrderService.verifyQrCode(qrCode));
    }

    @PostMapping("/refresh-qr")
    public Result<Map<String, Object>> refreshQrCode(@RequestBody Map<String, Long> body,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Long orderId = body.get("orderId");
        if (orderId == null) throw new BusinessException(ErrorCode.PARAM_MISSING);
        return Result.success(ticketOrderService.refreshQrCode(userId, orderId));
    }

    @PostMapping("/verify")
    public Result<Map<String, Object>> verifyQrCode(@RequestBody Map<String, String> body) {
        String qrCode = body.get("qrCode");
        if (qrCode == null || qrCode.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        return Result.success(ticketOrderService.verifyQrCode(qrCode));
    }
}
