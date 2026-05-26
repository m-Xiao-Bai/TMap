package com.mu.transitmap.constants;

/**
 * redisKey常用常量定义类
 * */
public class RedisKey {
    public static final String TRANSIT_MAP = "transitMap:";

    public static final String CAPTCHA = TRANSIT_MAP + "captcha:";
    public static final String IMAGE_KEY = CAPTCHA + "image:";
    public static final String EMAIL_KEY = CAPTCHA + "email:";
    public static final String TOKEN_KEY = TRANSIT_MAP + "token:";

    public static final String COUNTRY_KEY = TRANSIT_MAP + "COUNTRY:";
    public static final String ID_NAME_LIST_KEY = COUNTRY_KEY + "id_name_list:";

    // 缓存key：频繁访问数据
    public static final String CACHE = TRANSIT_MAP + "cache:";
    public static final String METRO_LINE_LIST_CACHE = CACHE + "metro_line_list";
    public static final String METRO_STATION_LIST_CACHE = CACHE + "metro_station_list:";
    public static final String CITY_LIST_CACHE = CACHE + "city_list";
    public static final String COUNTRY_LIST_CACHE = CACHE + "country_list";
}
