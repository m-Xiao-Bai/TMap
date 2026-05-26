package com.mu.transitmap.enums;

public enum CountryStatusEnum {

    PENDING(0, "审核中"),
    APPROVED(1, "审核通过"),
    REJECTED(2, "审核不通过"),
    ONLINE(3, "上线"),
    OFFLINE(4, "下线");

    private final int code;
    private final String description;

    CountryStatusEnum(int code, String description) {
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
    public static CountryStatusEnum fromCode(int code) {
        for (CountryStatusEnum status : CountryStatusEnum.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }

    public static CountryStatusEnum fromDescription(String description) {
        for (CountryStatusEnum status : CountryStatusEnum.values()) {
            if (status.getDescription().equalsIgnoreCase(description)) {
                return status;
            }
        }
        return null;
    }
}
