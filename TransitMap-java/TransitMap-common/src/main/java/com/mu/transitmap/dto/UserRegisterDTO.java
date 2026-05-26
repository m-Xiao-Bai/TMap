package com.mu.transitmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 用户注册 DTO
 */
@Data
@Schema(description = "用户注册请求参数")
public class UserRegisterDTO {

    @Schema(description = "用户名")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在 2~20 之间")
    private String username;

    @Schema(description = "邮箱（QQ邮箱）")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Pattern(
            regexp = "^[1-9][0-9]{4,10}@qq\\.com$",
            message = "仅支持 QQ 邮箱"
    )
    private String email;

    @Schema(description = "邮箱验证码")
    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须是 6 位数字")
    private String emailCode;

    @Schema(description = "前端加密后的密码")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度至少6位")
    private String password;

    @Schema(description = "手机号（可选）")
    @Pattern(
            regexp = "^$|^1[3-9]\\d{9}$",
            message = "手机号格式不正确"
    )
    private String mobile;

    @Schema(description = "性别码（0未知 1男 2女）", example = "0")
    @Min(value = 0, message = "性别码不合法")
    @Max(value = 2, message = "性别码不合法")
    private Integer genderCode = 0;
}
