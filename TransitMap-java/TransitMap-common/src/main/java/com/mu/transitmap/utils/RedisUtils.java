package com.mu.transitmap.utils;

import com.mu.transitmap.config.RedisTemplateWrapper;
import com.mu.transitmap.constants.RedisKey;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.vo.CountrySelectIdNameVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisUtils {
    @Autowired
    private RedisTemplateWrapper redis;

    /**
     * 存储图片验证码（默认5分钟）
     * */
    public void redisCaptchaImage(String key,String code ){
        redisCaptchaImage(key, code, 5, TimeUnit.MINUTES);
    }

    public void redisCaptchaImage(String key, String code, long timeout, TimeUnit unit) {
        redis.set(RedisKey.IMAGE_KEY + key, code, timeout, unit);
    }
    /**
     * 获取图片验证码
     * */
    public String getCaptchaImage(String key){
        return (String) redis.get(RedisKey.IMAGE_KEY+key);
    }
    /**
     * 删除图片验证码
     * */
    public void deleCaptchaImage(String key){
        redis.delete(RedisKey.IMAGE_KEY+key);
    }



    /**
     * 存储email验证码（默认5分钟）
     * */
    public void redisCaptchaEmail(String key,String code ){
        redisCaptchaEmail(key, code, 5, TimeUnit.MINUTES);
    }

    public void redisCaptchaEmail(String key, String code, long timeout, TimeUnit unit) {
        redis.set(RedisKey.EMAIL_KEY + key, code, timeout, unit);
    }
    /**
     * 获取email验证码
     * */
    public String getCaptchaEmail(String key){
        return (String) redis.get(RedisKey.EMAIL_KEY+key);
    }
    /**
     * 删除email验证码
     * */
    public void deleCaptchaEmail(String key){
        redis.delete(RedisKey.EMAIL_KEY+key);
    }

    public void storeToken(Long userId, String token, long expirationMs) {
        redis.set(RedisKey.TOKEN_KEY + userId, token, expirationMs, TimeUnit.MILLISECONDS);
    }

    public String getToken(Long userId) {
        return (String) redis.get(RedisKey.TOKEN_KEY + userId);
    }

    public void deleteToken(Long userId) {
        redis.delete(RedisKey.TOKEN_KEY + userId);
    }

    /**
     * 原子递增 key 的值，首次自动设置过期时间
     */
    public Long incrementKey(String key, long windowSeconds) {
        return redis.increment(key, windowSeconds);
    }

    /**
     * 获取 key 的计数值
     */
    public Long getKeyCount(String key) {
        Object val = redis.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * 删除指定 key
     */
    public void deleteKey(String key) {
        redis.delete(key);
    }



    /**
     * 获取全部国家名和id
     * */
    public List<CountrySelectIdNameVO> getCountryIdNameList(){
        return  (List<CountrySelectIdNameVO>) redis.get(RedisKey.ID_NAME_LIST_KEY);
    }
    /**
     * 删除全部国家名和id
     * */
    public void deleCountryIdNameList(){
        redis.delete(RedisKey.ID_NAME_LIST_KEY);
    }
    /**
     * 存储全部国家名和id
     * */
    public void redisCountryIdNameList(List<CountrySelectIdNameVO> list ){
        redis.set(RedisKey.ID_NAME_LIST_KEY,list,365, TimeUnit.DAYS  );
    }


    // ═══════════════════════════════════════════════════
    //  业务数据缓存（地铁线路、站点、城市、国家）
    // ═══════════════════════════════════════════════════

    /** 缓存地铁线路列表 */
    public void setMetroLineListCache(Object data, int ttlSeconds) {
        redis.set(RedisKey.METRO_LINE_LIST_CACHE, data, ttlSeconds, TimeUnit.SECONDS);
        log.debug("写入地铁线路缓存, TTL={}s", ttlSeconds);
    }

    public Object getMetroLineListCache() {
        return redis.get(RedisKey.METRO_LINE_LIST_CACHE);
    }

    public void deleteMetroLineListCache() {
        redis.delete(RedisKey.METRO_LINE_LIST_CACHE);
        log.debug("清除地铁线路缓存");
    }

    /** 缓存地铁站列表（按城市） */
    public void setMetroStationListCache(Long cityId, Object data, int ttlSeconds) {
        redis.set(RedisKey.METRO_STATION_LIST_CACHE + cityId, data, ttlSeconds, TimeUnit.SECONDS);
        log.debug("写入地铁站缓存 cityId={}, TTL={}s", cityId, ttlSeconds);
    }

    public Object getMetroStationListCache(Long cityId) {
        return redis.get(RedisKey.METRO_STATION_LIST_CACHE + cityId);
    }

    public void deleteMetroStationListCache(Long cityId) {
        redis.delete(RedisKey.METRO_STATION_LIST_CACHE + cityId);
        log.debug("清除地铁站缓存 cityId={}", cityId);
    }

    /** 清除所有地铁站缓存（用于批量操作后） */
    public void deleteAllMetroStationListCaches() {
        Set<String> keys = redis.keys(RedisKey.METRO_STATION_LIST_CACHE + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.debug("清除全部地铁站缓存, 共{}个key", keys.size());
        }
    }

    /** 缓存城市列表 */
    public void setCityListCache(Object data, int ttlSeconds) {
        redis.set(RedisKey.CITY_LIST_CACHE, data, ttlSeconds, TimeUnit.SECONDS);
        log.debug("写入城市列表缓存, TTL={}s", ttlSeconds);
    }

    public Object getCityListCache() {
        return redis.get(RedisKey.CITY_LIST_CACHE);
    }

    public void deleteCityListCache() {
        redis.delete(RedisKey.CITY_LIST_CACHE);
        log.debug("清除城市列表缓存");
    }

    /** 缓存国家列表 */
    public void setCountryListCache(Object data, int ttlSeconds) {
        redis.set(RedisKey.COUNTRY_LIST_CACHE, data, ttlSeconds, TimeUnit.SECONDS);
        log.debug("写入国家列表缓存, TTL={}s", ttlSeconds);
    }

    public Object getCountryListCache() {
        return redis.get(RedisKey.COUNTRY_LIST_CACHE);
    }

    public void deleteCountryListCache() {
        redis.delete(RedisKey.COUNTRY_LIST_CACHE);
        log.debug("清除国家列表缓存");
    }



}
