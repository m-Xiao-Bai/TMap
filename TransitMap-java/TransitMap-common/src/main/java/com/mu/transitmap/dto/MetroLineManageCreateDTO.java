package com.mu.transitmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MetroLineManageCreateDTO {

    @NotNull(message = "请选择所属国家")
    private Long countryId;

    @NotNull(message = "请选择所属城市")
    private Long cityId;

    @NotBlank(message = "线路名称不能为空")
    @Size(max = 100, message = "线路名称最长100字符")
    private String lineName;

    @NotBlank(message = "线路编号不能为空")
    @Size(max = 20, message = "线路编号最长20字符")
    private String lineNo;

    private String lineColor;

    private String lineColorCn;

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
