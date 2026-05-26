package com.mu.transitmap.enums;


import lombok.Getter;

/**
 * 用户状态枚举
 * */
@Getter
public enum UserStatusEnum {

    EXCEPTION(0, "异常"),
    ONLINE(1, "在线"),
    OFFLINE(2, "下线"),
    DISABLED(3, "禁用");

    private final int code;
    private final String desc;

    UserStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserStatusEnum of(int code) {
        for (UserStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return EXCEPTION;
    }

    public static UserStatusEnum fromCode(int code) {
        return of(code);
    }
}
