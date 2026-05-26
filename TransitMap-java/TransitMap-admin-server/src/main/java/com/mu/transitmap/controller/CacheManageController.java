package com.mu.transitmap.controller;

import com.mu.transitmap.config.RedisTemplateWrapper;
import com.mu.transitmap.constants.RedisKey;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/manage/cache")
public class CacheManageController {

    @Autowired
    private RedisTemplateWrapper redis;

    private static final Map<String, List<String>> CACHE_CATEGORIES = new LinkedHashMap<>();

    static {
        CACHE_CATEGORIES.put("captcha_image", List.of(RedisKey.IMAGE_KEY + "*"));
        CACHE_CATEGORIES.put("captcha_email", List.of(RedisKey.EMAIL_KEY + "*"));
        CACHE_CATEGORIES.put("token", List.of(RedisKey.TOKEN_KEY + "*"));
        CACHE_CATEGORIES.put("country", List.of(RedisKey.ID_NAME_LIST_KEY, RedisKey.COUNTRY_LIST_CACHE));
        CACHE_CATEGORIES.put("city", List.of(RedisKey.CITY_LIST_CACHE));
        CACHE_CATEGORIES.put("metro_line", List.of(RedisKey.METRO_LINE_LIST_CACHE));
        CACHE_CATEGORIES.put("metro_station", List.of(RedisKey.METRO_STATION_LIST_CACHE + "*"));
        CACHE_CATEGORIES.put("all", List.of(RedisKey.TRANSIT_MAP + "*"));
    }

    /**
     * 获取各分类缓存的 key 数量
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus(HttpServletRequest request) {
        checkPermission(request);
        Map<String, Object> result = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, List<String>> entry : CACHE_CATEGORIES.entrySet()) {
            int count = countKeys(entry.getValue());
            result.put(entry.getKey(), count);
            if (!entry.getKey().equals("all")) {
                total += count;
            }
        }
        result.put("_total", total);
        return Result.success(result);
    }

    /**
     * 清除指定分类的缓存
     */
    @DeleteMapping("/clear/{category}")
    public Result<Map<String, Object>> clearCategory(@PathVariable String category,
                                                      HttpServletRequest request) {
        checkPermission(request);
        List<String> patterns = CACHE_CATEGORIES.get(category);
        if (patterns == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        int deleted = 0;
        for (String pattern : patterns) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long count = redis.delete(keys);
                deleted += (count != null ? count.intValue() : 0);
            }
        }

        log.info("管理员清除Redis缓存, category={}, deleted={}", category, deleted);
        Map<String, Object> data = new HashMap<>();
        data.put("deleted", deleted);
        return Result.success(data);
    }

    private void checkPermission(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private int countKeys(List<String> patterns) {
        int count = 0;
        for (String pattern : patterns) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null) {
                count += keys.size();
            }
        }
        return count;
    }
}
