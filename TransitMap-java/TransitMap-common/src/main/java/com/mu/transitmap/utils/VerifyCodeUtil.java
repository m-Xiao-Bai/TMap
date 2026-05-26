package com.mu.transitmap.utils;

public class VerifyCodeUtil {

    /**
     * 生成 6 位数字验证码
     */
    public static String generateCode() {
        return String.valueOf((int)((Math.random() * 9 + 1) * 100000));
    }
}