package com.mu.transitmap.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 城市展示 VO（用于前端列表 / 详情）
 * 说明：
 * 1. 屏蔽数据库中的 extra 字段
 * 2. 保留前端真正需要展示的字段
 * 3. 字段命名与前端保持一致（snake_case）
 */
@Data
public class CityVO {

    /** 城市唯一ID */
    private Long id;

    /** 国家ID */
    @JsonProperty("country_id")
    private Long countryId;

    /** 国家名称 */
    @JsonProperty("country_name")
    private String countryName;

    /** 城市名称（中文） */
    @JsonProperty("city_name")
    private String cityName;

    /** 城市英文名称 */
    @JsonProperty("city_name_en")
    private String cityNameEn;

    /** 城市别称 */
    @JsonProperty("city_alias")
    private String cityAlias;

    /** 地铁线 LOGO */
    @JsonProperty("metro_line_logo")
    private String metroLineLogo;

    /** 地铁系统数 */
    @JsonProperty("metro_count")
    private Integer metroCount;

    /** 地铁线路数 */
    @JsonProperty("metro_line_count")
    private Integer metroLineCount;

    /** 高铁数量 */
    @JsonProperty("hsr_count")
    private Integer hsrCount;

    /** 地铁总里程（公里） */
    @JsonProperty("metro_km")
    private BigDecimal metroKm;

    /** 高铁里程（公里） */
    @JsonProperty("hsr_km")
    private BigDecimal hsrKm;

    /** 城市人口 */
    private Long population;

    /** 城市状态（中文） */
    private String status;

    /** 城市状态码 */
    @JsonProperty("status_code")
    private Integer statusCode;

    /** 创建时间 */
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
