package com.mu.transitmap.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将非 JSON 字符串转换为合法 JSON 字符串值，防止 MySQL JSON 列报错
     * 如果已经是合法 JSON（以 [ 或 { 开头），则原样返回
     */
    public static String toJsonValue(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) return trimmed;
        try {
            return objectMapper.writeValueAsString(trimmed);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
