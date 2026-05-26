package com.mu.transitmap.controller;


import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CaptchaImageService;
import com.mu.transitmap.service.EmailService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@Validated
@RequestMapping("/utils")
public class UtilsController {

    @Autowired
    private CaptchaImageService captchaImage;
    @Autowired
    private EmailService emailService;
    /**
     * 获取图形验证码
     */
    @GetMapping("/captcha/image")
    public Result<Map<String, String>> getCaptcha() {
        try {
            return Result.success(captchaImage.generateCaptcha());
        } catch (RedisConnectionFailureException e) {
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }
    /**
     * 发送邮箱验证码
     * */
    @GetMapping("/captcha/send")
    public Result<Map<String, String>> postSend(@RequestParam("email")
                                                @NotBlank(message = "邮箱不能为空")
                                                @Email(message = "邮箱格式不正确")
                                                String email
    ) {
        try {
            emailService.sendVerifyCode(email);
            return Result.success(null);
        } catch (RedisConnectionFailureException e) {
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }

}
