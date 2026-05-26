package com.mu.transitmap.service;

import com.mu.transitmap.constants.RedisKey;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.utils.RedisUtils;
import com.wf.captcha.SpecCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaImageService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;


    /**
     * 生成验证码
     */
    public Map<String, String> generateCaptcha() {
        // 1. 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(com.wf.captcha.base.Captcha.TYPE_DEFAULT);

        String code = captcha.text().toLowerCase();;
        String base64 = captcha.toBase64();

        // 2. 生成唯一 key
        String key = UUID.randomUUID().toString();
        // 3. 存 Redis（从系统配置读取过期时间）
        int expiry = systemConfigService.getConfigInt("auth.captcha_image_expiry", 300);
        redisUtils.redisCaptchaImage(key, code, expiry, TimeUnit.SECONDS);
        // 4. 返回给前端
        Map<String, String> result = new HashMap<>();
        result.put("captchaKey", key);
        result.put("captchaImage", base64);

        return result;
    }

    /**
     * 校验验证码
     */
    public boolean validate(String key, String inputCode) {
        String redisCode = redisUtils.getCaptchaImage( key);
        if (redisCode == null) {
            return false;
        }
        redisUtils.deleCaptchaImage(key); // 一次性
        return redisCode.equalsIgnoreCase(inputCode);
    }
}
