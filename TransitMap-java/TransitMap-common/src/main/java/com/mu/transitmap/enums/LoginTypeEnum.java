package com.mu.transitmap.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LoginTypeEnum {

    MOBILE(1, "mobile", "手机号登录"),
    EMAIL(2, "email", "邮箱登录"),
    WECHAT_QR(3, "wechat_qr", "微信扫码登录"),
    QQ_QR(4, "qq_qr", "QQ扫码登录");

    /** 登录方式编码（数据库 / 前端传输） */
    private final Integer code;

    /** 登录方式标识（接口参数使用） */
    private final String type;

    /** 描述 */
    private final String desc;

    /**
     * 根据 type 获取枚举
     */
    public static LoginTypeEnum fromType(String type) {
        for (LoginTypeEnum e : values()) {
            if (e.type.equalsIgnoreCase(type)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据 code 获取枚举
     */
    public static LoginTypeEnum fromCode(Integer code) {
        for (LoginTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
