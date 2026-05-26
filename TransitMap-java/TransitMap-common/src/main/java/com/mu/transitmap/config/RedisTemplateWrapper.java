package com.mu.transitmap.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作统一封装工具类
 *
 * 说明：
 * 1. 对 RedisTemplate 进行二次封装，减少业务层直接依赖 Redis API
 * 2. 统一异常、日志、Key 管理（可扩展）
 *
 * @author xiaobai
 * @since 1.0
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RedisTemplateWrapper {

    /**
     * Redis 核心操作模板
     * 由 Spring 自动注入（构造器注入，线程安全）
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /* ================= String ================= */

    /**
     * 设置缓存（带过期时间）
     *
     * @param key     Redis Key
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
        log.debug("Redis SET key={}, timeout={} {}", key, timeout, unit);
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存
     */
    public Long delete(Set<String> keys) {
        return redisTemplate.delete(keys);
    }

    /**
     * 查找匹配的key集合
     */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 设置 Key 过期时间
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /* ================= Counter ================= */

    /**
     * 原子递增，首次自动设置过期时间
     */
    public Long increment(String key, long timeoutSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

    /* ================= Hash ================= */

    /**
     * Hash 设置字段
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Hash 获取字段
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Hash 获取全部字段
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /* ================= Set ================= */
    /**
     * Set 添加元素
     */
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }
    /**
     * Set 获取所有成员
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }
}
