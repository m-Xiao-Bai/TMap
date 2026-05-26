package com.mu.transitmap.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("metro_line")
public class MetroLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long countryId;

    private String countryName;

    private String lineName;

    private String lineNo;

    private String lineColor;

    private String lineColorCn;

    private Long cityId;

    private String cityName;

    private String cityNameEn;

    private BigDecimal totalKm;

    private Integer stationCount;

    private Integer transferLineCount;

    private String transferLines;

    private Integer transferStationCount;

    private String transferStations;

    private Integer trainCount;

    private BigDecimal avgSpeed;

    private LocalTime firstTime;

    private LocalTime lastTime;

    private Integer fullTime;

    private LocalDate openDate;

    private Integer statusCode;

    private String status;

    private String extra;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
