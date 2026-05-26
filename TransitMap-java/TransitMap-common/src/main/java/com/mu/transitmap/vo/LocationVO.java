package com.mu.transitmap.vo;

import lombok.Data;

@Data
public class LocationVO {
    private Double lat;
    private Double lng;
    private String city;
    private String address;
    private String formattedAddress;
}
