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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("city")
public class City implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long countryId;

    private String countryName;

    private String cityName;

    private String cityNameEn;

    private String cityAlias;

    private String metroLineLogo;

    private Integer metroCount;

    private Integer metroLineCount;

    private Integer hsrCount;

    private BigDecimal metroKm;

    private BigDecimal hsrKm;

    private Long population;

    private String metroLines;

    private String extra;

    private String status;

    private Integer statusCode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
