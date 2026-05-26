package com.mu.transitmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CityManageCreateDTO {

    @NotNull(message = "请选择所属国家")
    private Long countryId;

    @NotBlank(message = "城市名称不能为空")
    @Size(max = 50, message = "城市名称最长50字符")
    private String cityName;

    @Size(max = 100, message = "英文名称最长100字符")
    private String cityNameEn;

    @Size(max = 100, message = "别称最长100字符")
    private String cityAlias;

    @Size(max = 255, message = "LOGO地址最长255字符")
    private String metroLineLogo;

    private Integer metroCount;

    private Integer metroLineCount;

    private Integer hsrCount;

    private BigDecimal metroKm;

    private BigDecimal hsrKm;

    private Long population;

    private String metroLines;

    private String extra;

    private Integer statusCode;
}
