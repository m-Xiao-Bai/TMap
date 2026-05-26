package com.mu.transitmap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;     // 手机号 或 QQ邮箱

    @NotBlank(message = "登录类型不能为空")
    private String type;        // mobile / qq

    @NotBlank(message = "密码不能为空")
    private String password;    // 前端 SHA-256 后的密码

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    @NotBlank(message = "验证码Key不能为空")
    private String captchaKey;
}
