package com.mu.transitmap.vo;


import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 国家下拉接口
 * */
@Data
@AllArgsConstructor
public class CountrySelectIdNameVO {

    private Long id;

    /**
     * 国家名称（中文）
     */
    private String countryName;

    @Override
    public String toString() {
        return "CountryAllListIdPo{" +
                "id=" + id +
                ", countryName='" + countryName + '\'' +
                '}';
    }
}
