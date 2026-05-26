package com.mu.transitmap.enums;



import lombok.Getter;
/**
 * 用户角色 / 权限枚举
 * */
@Getter
public enum UserRoleEnum {

    USER(1, "普通用户"),
    ADMIN(2, "管理员用户"),
    SUPER_ADMIN(3, "超级管理员"),
    ROOT_ADMIN(4, "最高级管理员");

    private final int code;
    private final String desc;

    UserRoleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserRoleEnum of(int code) {
        for (UserRoleEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return USER;
    }

    public static UserRoleEnum fromCode(int code) {
        return of(code);
    }
}
