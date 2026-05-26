package com.mu.transitmap.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 国家信息表
 * </p>
 *
 * @author muxiaobai
 * @since 2026-01-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("country")
public class Country implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 国家唯一ID（雪花算法）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 国家名称（中文）
     */
    private String countryName;

    /**
     * 国家英文名称
     */
    private String countryNameEn;

    /**
     * 国家别称
     */
    private String countryAlias;

    /**
     * 国家城市数量
     */
    private Integer cityCount;

    /**
     * 国家地铁线路总数
     */
    private Integer metroLineCount;

    /**
     * 国家地铁站总数
     */
    private Integer metroStationCount;

    /**
     * 国家地铁总里程（公里）
     */
    private BigDecimal metroKm;

    /**
     * 国家高铁站总数
     */
    private Integer hsrStationCount;

    /**
     * 国家高铁总里程（公里）
     */
    private BigDecimal hsrKm;

    /**
     * 国家状态（审核中，审核通过，审核不通过，上线，下线）
     */
    private String status;

    /**
     * 国家状态码（0：审核中，1：审核通过，2：审核不通过，3：上线，4：下线）
     */
    private Integer statusCode;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 扩展字段
     */
    private String extra;


}
