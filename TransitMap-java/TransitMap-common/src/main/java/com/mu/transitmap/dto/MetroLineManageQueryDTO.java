package com.mu.transitmap.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MetroLineManageQueryDTO {

    private String keyword;

    private Long countryId;

    private Long cityId;

    private Integer statusCode;

    private BigDecimal minTotalKm;
    private BigDecimal maxTotalKm;

    private Integer minStationCount;
    private Integer maxStationCount;

    private Integer minTrainCount;
    private Integer maxTrainCount;

    private BigDecimal minAvgSpeed;
    private BigDecimal maxAvgSpeed;

    private String sortField;

    private String sortOrder;

    @Min(1)
    private Integer pageNum;

    @Min(1)
    private Integer pageSize;
}
