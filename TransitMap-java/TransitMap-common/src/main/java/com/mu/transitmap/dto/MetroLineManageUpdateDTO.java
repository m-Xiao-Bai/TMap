package com.mu.transitmap.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MetroLineManageUpdateDTO {

    @Size(max = 100, message = "线路名称最长100字符")
    private String lineName;

    @Size(max = 20, message = "线路编号最长20字符")
    private String lineNo;

    private String lineColor;

    private String lineColorCn;

    private Long cityId;

    private Long countryId;

    private BigDecimal totalKm;

    private Integer stationCount;

    private Integer transferLineCount;

    private String transferLines;

    private Integer transferStationCount;

    private String transferStations;

    private Integer trainCount;

    private BigDecimal avgSpeed;

    private String firstTime;

    private String lastTime;

    private Integer fullTime;

    private String openDate;

    private Integer statusCode;

    private String extra;
}
