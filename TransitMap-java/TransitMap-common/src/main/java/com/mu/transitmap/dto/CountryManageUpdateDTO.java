package com.mu.transitmap.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CountryManageUpdateDTO {

    @Size(max = 100, message = "国家名称最长100字符")
    private String countryName;

    @Size(max = 150, message = "英文名称最长150字符")
    private String countryNameEn;

    @Size(max = 150, message = "别称最长150字符")
    private String countryAlias;

    private Integer cityCount;

    private Integer metroLineCount;

    private Integer metroStationCount;

    private java.math.BigDecimal metroKm;

    private Integer hsrStationCount;

    private java.math.BigDecimal hsrKm;

    private Integer statusCode;
}
