package com.mu.transitmap.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CountryManageQueryDTO {

    private String keyword;

    private Integer statusCode;

    private Integer onlineStatusCode;

    private Integer minCityCount;
    private Integer maxCityCount;

    private Integer minMetroLineCount;
    private Integer maxMetroLineCount;

    private Integer minMetroStationCount;
    private Integer maxMetroStationCount;

    private BigDecimal minMetroKm;
    private BigDecimal maxMetroKm;

    private Integer minHsrStationCount;
    private Integer maxHsrStationCount;

    private BigDecimal minHsrKm;
    private BigDecimal maxHsrKm;

    private String sortField;

    private String sortOrder;

    @Min(1)
    @NotNull
    private Integer pageNum;

    @Min(1)
    @NotNull
    private Integer pageSize;
}
