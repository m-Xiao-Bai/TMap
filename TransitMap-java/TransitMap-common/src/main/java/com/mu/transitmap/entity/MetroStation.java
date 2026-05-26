package com.mu.transitmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@TableName("metro_station")
public class MetroStation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long countryId;
    private String countryName;
    private Long osmid;
    private String stationName;
    private String stationNameEn;
    private String stationAlias;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer isTransfer;
    private String lineIds;
    private String lineNames;
    private Integer exitCount;
    private Integer hasToilet;
    private Integer stationType;
    private LocalDate openDate;
    private Long cityId;
    private String cityName;
    private LocalTime firstTime;
    private LocalTime lastTime;
    private String prevStationIds;
    private String prevStationNames;
    private String prevStationDistances;
    private String nextStationIds;
    private String nextStationNames;
    private String nextStationDistances;
    private Integer statusCode;
    private String status;
    private String extra;
    private LocalDateTime geocodeTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCountryId() { return countryId; }
    public void setCountryId(Long countryId) { this.countryId = countryId; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public Long getOsmid() { return osmid; }
    public void setOsmid(Long osmid) { this.osmid = osmid; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getStationNameEn() { return stationNameEn; }
    public void setStationNameEn(String stationNameEn) { this.stationNameEn = stationNameEn; }
    public String getStationAlias() { return stationAlias; }
    public void setStationAlias(String stationAlias) { this.stationAlias = stationAlias; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public Integer getIsTransfer() { return isTransfer; }
    public void setIsTransfer(Integer isTransfer) { this.isTransfer = isTransfer; }
    public String getLineIds() { return lineIds; }
    public void setLineIds(String lineIds) { this.lineIds = lineIds; }
    public String getLineNames() { return lineNames; }
    public void setLineNames(String lineNames) { this.lineNames = lineNames; }
    public Integer getExitCount() { return exitCount; }
    public void setExitCount(Integer exitCount) { this.exitCount = exitCount; }
    public Integer getHasToilet() { return hasToilet; }
    public void setHasToilet(Integer hasToilet) { this.hasToilet = hasToilet; }
    public Integer getStationType() { return stationType; }
    public void setStationType(Integer stationType) { this.stationType = stationType; }
    public LocalDate getOpenDate() { return openDate; }
    public void setOpenDate(LocalDate openDate) { this.openDate = openDate; }
    public Long getCityId() { return cityId; }
    public void setCityId(Long cityId) { this.cityId = cityId; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public LocalTime getFirstTime() { return firstTime; }
    public void setFirstTime(LocalTime firstTime) { this.firstTime = firstTime; }
    public LocalTime getLastTime() { return lastTime; }
    public void setLastTime(LocalTime lastTime) { this.lastTime = lastTime; }
    public String getPrevStationIds() { return prevStationIds; }
    public void setPrevStationIds(String prevStationIds) { this.prevStationIds = prevStationIds; }
    public String getPrevStationNames() { return prevStationNames; }
    public void setPrevStationNames(String prevStationNames) { this.prevStationNames = prevStationNames; }
    public String getPrevStationDistances() { return prevStationDistances; }
    public void setPrevStationDistances(String prevStationDistances) { this.prevStationDistances = prevStationDistances; }
    public String getNextStationIds() { return nextStationIds; }
    public void setNextStationIds(String nextStationIds) { this.nextStationIds = nextStationIds; }
    public String getNextStationNames() { return nextStationNames; }
    public void setNextStationNames(String nextStationNames) { this.nextStationNames = nextStationNames; }
    public String getNextStationDistances() { return nextStationDistances; }
    public void setNextStationDistances(String nextStationDistances) { this.nextStationDistances = nextStationDistances; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public LocalDateTime getGeocodeTime() { return geocodeTime; }
    public void setGeocodeTime(LocalDateTime geocodeTime) { this.geocodeTime = geocodeTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
