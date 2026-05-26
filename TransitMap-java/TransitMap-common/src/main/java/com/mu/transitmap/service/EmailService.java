package com.mu.transitmap.service;

import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.utils.RedisUtils;
import com.mu.transitmap.utils.VerifyCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
@ConditionalOnProperty(name = "spring.mail.host")
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendVerifyCode(String email) {
        String code = VerifyCodeUtil.generateCode();
        int expiry = systemConfigService.getConfigInt("auth.captcha_email_expiry", 300);
        redisUtils.redisCaptchaEmail(email, code, expiry, TimeUnit.SECONDS);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("验证码通知");
        message.setText(
                "您的验证码是：" + code + "\n\n"
                        + (expiry / 60) + " 分钟内有效，请勿泄露。"
        );
        mailSender.send(message);
        log.info("邮箱验证码发送成功 email={}", email);
    }

    public boolean validate(String email, String code) {
        String redisCode = redisUtils.getCaptchaEmail(email);
        if (redisCode == null) {
            return false;
        }
        if (redisCode.equals(code)) {
            redisUtils.deleCaptchaEmail(email);
            return true;
        }
        return false;
    }
}
