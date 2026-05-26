package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.SystemConfig;
import com.mu.transitmap.mapper.SystemConfigMapper;
import com.mu.transitmap.service.ISystemConfigService;
import com.mu.transitmap.util.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig> implements ISystemConfigService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    @Autowired
    private CryptoService cryptoService;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        List<SystemConfig> all = list();
        Map<String, String> newCache = new ConcurrentHashMap<>();
        for (SystemConfig c : all) {
            newCache.put(c.getConfigKey(), c.getConfigValue());
        }
        configCache.clear();
        configCache.putAll(newCache);
    }

    @Override
    public String getConfigValue(String key) {
        return configCache.getOrDefault(key, getDefaultValue(key));
    }

    public String getConfigJson(String key) {
        return configCache.getOrDefault(key, getDefaultValue(key));
    }

    public int getConfigInt(String key, int defaultValue) {
        String v = configCache.get(key);
        if (v != null) {
            try { return Integer.parseInt(v); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public <T> T getConfigObject(String key, TypeReference<T> typeRef) {
        String json = configCache.get(key);
        if (json != null) {
            try { return objectMapper.readValue(json, typeRef); } catch (Exception ignored) {}
        }
        String def = getDefaultValue(key);
        if (def != null) {
            try { return objectMapper.readValue(def, typeRef); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 获取原始配置值（自动解密 enc: 前缀的密文字段）
     * 解密失败会抛 RuntimeException，调用方应该处理（业务真正用 key 时）
     * - 自动清理所有空白字符（包括用户复制时混入的尾部空格、换行、零宽字符等）
     * - 对 secret 类型尤其重要：高德/LLM API 对 key 严格逐字节匹配
     */
    public String getRaw(String key) {
        String value = configCache.get(key);
        if (value == null) {
            value = getDefaultValue(key);
        }
        String decrypted = cryptoService.decrypt(value);
        return decrypted == null ? null : cleanInvisible(decrypted);
    }

    /**
     * 清理字符串中的所有空白字符（含 \r\n\t、零宽字符 ​ 等）
     * 仅对 secret 类型这种需要严格匹配的场景调用
     */
    private String cleanInvisible(String s) {
        if (s == null || s.isEmpty()) return s;
        // 1. trim 两端
        s = s.trim();
        // 2. 移除所有 Unicode 空白 + 零宽字符
        return s.replaceAll("[\\s\\u200b\\u200c\\u200d\\ufeff]", "");
    }

    /**
     * 获取密钥字段的掩码显示（如 sk-****abcd）
     * 容错：解密失败不抛错，返回明确的提示，便于管理端列出所有配置项
     */
    public String getSecretMask(String key) {
        String value = configCache.get(key);
        if (value == null) {
            value = getDefaultValue(key);
        }
        if (value == null || value.isEmpty()) {
            return "";
        }
        // 未加密，直接掩码
        if (!value.startsWith("enc:")) {
            return cryptoService.mask(value);
        }
        // 已加密，尝试解密
        try {
            String plain = cryptoService.decrypt(value);
            return cryptoService.mask(plain);
        } catch (Exception e) {
            // 旧密文用新 master key 解不开 —— 返回明显的占位文案
            return "(解密失败, 请重新填写)";
        }
    }

    /**
     * 解析 JSON 配置为指定类型
     */
    public <T> T getJson(String key, Class<T> clazz) {
        String json = configCache.get(key);
        if (json == null) {
            json = getDefaultValue(key);
        }
        if (json != null) {
            try { return objectMapper.readValue(json, clazz); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 根据状态码获取状态名称，从 station.status_map 配置中查找
     */
    public String getStatusName(Integer code) {
        if (code == null) return "未知";
        Map<String, String> statusMap = getConfigObject("station.status_map", new TypeReference<Map<String, String>>() {});
        if (statusMap != null) {
            String name = statusMap.get(String.valueOf(code));
            if (name != null) return name;
        }
        return "未知";
    }

    @Override
    public List<SystemConfig> getAllPublicConfigs() {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getIsPublic, 1)
               .orderByAsc(SystemConfig::getConfigGroup, SystemConfig::getConfigKey);
        return list(wrapper);
    }

    @Override
    public Map<String, List<SystemConfig>> getAllConfigsGrouped() {
        Map<String, List<SystemConfig>> result = new LinkedHashMap<>();
        List<SystemConfig> all = list(new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getConfigGroup, SystemConfig::getConfigKey));
        for (SystemConfig c : all) {
            result.computeIfAbsent(c.getConfigGroup(), k -> new ArrayList<>()).add(c);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateConfigs(List<SystemConfig> configs) {
        for (SystemConfig c : configs) {
            SystemConfig existing = getOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, c.getConfigKey()));
            if (existing != null) {
                String newValue = c.getConfigValue();
                // secret 类型自动加密：除非已经是 enc: 前缀
                if ("secret".equals(existing.getConfigType())
                        && newValue != null && !newValue.isEmpty()
                        && !newValue.startsWith("enc:")) {
                    try {
                        // 先 trim 掉用户复制时可能带入的尾部空格/换行/不可见字符
                        newValue = cryptoService.encrypt(newValue.trim());
                    } catch (Exception e) {
                        newValue = existing.getConfigValue();
                    }
                }
                existing.setConfigValue(newValue);
                existing.setDescription(c.getDescription());
                updateById(existing);
            }
        }
        refreshCache();
    }

    private String getDefaultValue(String key) {
        switch (key) {
            case "station.status_map":
                return "{\"0\":\"未开通\",\"1\":\"运营中\",\"2\":\"建设中\",\"3\":\"规划中\",\"4\":\"已停运\"}";
            case "station.type_map":
                return "{\"0\":\"地下\",\"1\":\"地面\",\"2\":\"高架\"}";
            case "pagination.default_size":
                return "10";
            case "pagination.size_options":
                return "[10,20,50,100]";
            case "auth.captcha_image_expiry":
                return "300";
            case "auth.captcha_email_expiry":
                return "300";
            case "auth.token_expiry":
                return "86400000";
            case "cache.ttl.metroLine":
                return "86400";
            case "cache.ttl.metroStation":
                return "86400";
            case "cache.ttl.city":
                return "86400";
            case "cache.ttl.country":
                return "86400";
            case "map.route_style":
                return "{\"routeColor\":\"#ff6b35\",\"glowWeight\":14,\"glowOpacity\":0.25,\"lineWeight\":6,\"lineOpacity\":0.95,\"dashArray\":\"12 6\",\"endpointRadius\":10,\"endpointWeight\":4,\"midpointRadius\":7,\"midpointWeight\":3,\"dimLineGlowOpacity\":0.04,\"dimLineOpacity\":0.12,\"dimStationOpacity\":0.12,\"dimStationRadius\":3}";
            case "map.label_config":
                return "{\"baseFontSize\":11,\"minFontSize\":6,\"shrinkStartZoom\":13,\"hideZoom\":11,\"fontWeight\":500,\"color\":\"#3a3a4a\"}";
            case "ticket.price_tiers":
                return "[{\"maxStops\":3,\"price\":2},{\"maxStops\":6,\"price\":3},{\"maxStops\":9,\"price\":4},{\"maxStops\":12,\"price\":5},{\"maxStops\":18,\"price\":6},{\"maxStops\":999,\"price\":7}]";
            case "ticket.estimate_params":
                return "{\"minutesPerStop\":3,\"minMinutes\":2,\"kmPerStop\":1.8}";
            case "ticket.payment_timeout_hours":
                return "24";
            case "ticket.qr_validity_hours":
                return "24";
            default:
                return null;
        }
    }
}
