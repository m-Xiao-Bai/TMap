package com.mu.transitmap.enums;

import lombok.Getter;

/**
 * 用户性别枚举
 * */
@Getter
public enum UserGenderEnum {

    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女");

    private final int code;
    private final String desc;

    UserGenderEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserGenderEnum of(int code) {
        for (UserGenderEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static UserGenderEnum fromCode(int code) {
        return of(code);
    }
}
