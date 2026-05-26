package com.mu.transitmap.enums;

public enum CityStatusEnum {

    PENDING(0, "审核中"),
    APPROVED(1, "审核通过"),
    REJECTED(2, "审核不通过"),
    ONLINE(3, "上线"),
    OFFLINE(4, "下线");

    private final int code;
    private final String description;

    CityStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码获取枚举
     * @param code 状态码
     * @return 枚举
     */
    public static CityStatusEnum fromCode(int code) {
        for (CityStatusEnum status : CityStatusEnum.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;  // 或者抛出异常：throw new IllegalArgumentException("Invalid status code: " + code);
    }

    /**
     * 根据状态描述获取枚举
     * @param description 状态描述
     * @return 枚举
     */
    public static CityStatusEnum fromDescription(String description) {
        for (CityStatusEnum status : CityStatusEnum.values()) {
            if (status.getDescription().equalsIgnoreCase(description)) {
                return status;
            }
        }
        return null;  // 或者抛出异常：throw new IllegalArgumentException("Invalid status description: " + description);
    }
}
