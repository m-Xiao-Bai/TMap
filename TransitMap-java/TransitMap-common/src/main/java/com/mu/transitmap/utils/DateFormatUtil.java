package com.mu.transitmap.utils;

import com.mu.transitmap.enums.DatePattern;
import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 时间格式化工具类
 * <p>
 * 支持格式：
 * 1️⃣ 年 / 年月 / 年月日 / 年月日时 / 年月日时分 / 年月日时分秒
 * 2️⃣ 分隔符：/ 、. 、中文（年月日时分秒）
 *
 * @author muxiaobai
 */
@UtilityClass
public class DateFormatUtil {

    /* ===================== 常用分隔符 ===================== */

    public static final String SLASH = "/";
    public static final String DOT = ".";
    public static final String CHINESE = "中文";

    /* ===================== 对外方法 ===================== */

    /**
     * 格式化 LocalDateTime
     *
     * @param time     时间
     * @param pattern  格式枚举
     * @param style    分隔符样式（/ . 中文）
     */
    public static String format(LocalDateTime time, DatePattern pattern, String style) {
        if (time == null) {
            return "";
        }

        if (CHINESE.equals(style)) {
            return formatChinese(time, pattern);
        }

        String fmt = pattern.build(style);
        return time.format(DateTimeFormatter.ofPattern(fmt));
    }

    /**
     * 格式化 LocalDate
     */
    public static String format(LocalDate date, DatePattern pattern, String style) {
        return format(date.atStartOfDay(), pattern, style);
    }

    /**
     * 格式化 Date
     */
    public static String format(Date date, DatePattern pattern, String style) {
        return format(
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                pattern,
                style
        );
    }

    /**
     * 格式化时间戳
     */
    public static String format(long timestamp, DatePattern pattern, String style) {
        return format(
                Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime(),
                pattern,
                style
        );
    }

    /* ===================== 中文格式 ===================== */

    /**
     * 中文时间格式化（年月日时分秒）
     */
    private static String formatChinese(LocalDateTime time, DatePattern pattern) {
        return switch (pattern) {
            case YEAR ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年"));
            case YEAR_MONTH ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
            case YEAR_MONTH_DAY ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            case YEAR_MONTH_DAY_HOUR ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时"));
            case YEAR_MONTH_DAY_MINUTE ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分"));
            case YEAR_MONTH_DAY_SECOND ->
                    time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日HH时mm分ss秒"));
        };
    }
}
