package com.mu.transitmap.enums;


import lombok.Getter;

/**
 * 时间格式枚举
 * 统一管理所有时间输出格式
 *
 * @author muxiaobai
 */
@Getter
public enum DatePattern {

    /** 年 */
    YEAR("yyyy"),
    YEAR_MONTH("yyyy{sep}MM"),
    YEAR_MONTH_DAY("yyyy{sep}MM{sep}dd"),
    YEAR_MONTH_DAY_HOUR("yyyy{sep}MM{sep}dd HH"),
    YEAR_MONTH_DAY_MINUTE("yyyy{sep}MM{sep}dd HH:mm"),
    YEAR_MONTH_DAY_SECOND("yyyy{sep}MM{sep}dd HH:mm:ss");

    private final String pattern;

    DatePattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * 根据分隔符构建真正的 pattern
     */
    public String build(String separator) {
        return pattern.replace("{sep}", separator);
    }
}
