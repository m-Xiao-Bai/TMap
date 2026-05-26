package com.mu.transitmap.dto;

import com.mu.transitmap.constants.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;


@Data
public class CityQueryDTO {


    /** 国家ID（精确匹配，下拉选择） */
    private Long countryId;


    /** 城市状态码（0~4） */
    @Min(Constants.STATUS_MIN)
    @Max(Constants.STATUS_MAX)
    private Integer statusCode;


    /** 地铁系统数 >= */
    @Min(0)
    private Integer metroCount;


    /** 高铁数量 >= */
    @Min(0)
    private Integer hsrCount;


    /** 页码 */
    @Min(Constants.PAGE_MIN)
    private Integer page = 1;


    /** 每页大小 */
    @Min(Constants.PAGE_SIZE_MIN)
    @Max(Constants.PAGE_SIZE_MAX)
    private Integer pageSize = 10;


    /** 排序字段：created_at / updated_at */
    private String sortField = Constants.CREATED_AT;


    /** 排序方式：asc / desc */
    private String sortOrder = Constants.SORT_ORDER_DESC;
}