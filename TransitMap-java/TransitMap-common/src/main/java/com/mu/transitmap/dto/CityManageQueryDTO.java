package com.mu.transitmap.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CityManageQueryDTO {

    private String keyword;

    private Long countryId;

    private Integer statusCode;

    private Integer onlineStatusCode;

    private Integer minMetroCount;
    private Integer maxMetroCount;

    private Integer minMetroLineCount;
    private Integer maxMetroLineCount;

    private Integer minHsrCount;
    private Integer maxHsrCount;

    private BigDecimal minMetroKm;
    private BigDecimal maxMetroKm;

    private BigDecimal minHsrKm;
    private BigDecimal maxHsrKm;

    private Long minPopulation;
    private Long maxPopulation;

    private String sortField;

    private String sortOrder;

    @Min(1)
    private Integer pageNum;

    @Min(1)
    private Integer pageSize;
}
