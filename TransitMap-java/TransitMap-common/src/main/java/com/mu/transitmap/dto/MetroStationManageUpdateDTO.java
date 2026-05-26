package com.mu.transitmap.dto;

import java.math.BigDecimal;

public class MetroStationManageUpdateDTO {

    private Long countryId;
    private String stationName;
    private Long cityId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String stationNameEn;
    private String stationAlias;
    private Integer isTransfer;
    private String lineIds;
    private String lineNames;
    private Integer exitCount;
    private Integer hasToilet;
    private Integer stationType;
    private String openDate;
    private String firstTime;
    private String lastTime;
    private String prevStationIds;
    private String prevStationNames;
    private String prevStationDistances;
    private String nextStationIds;
    private String nextStationNames;
    private String nextStationDistances;
    private Integer statusCode;
    private String extra;

    public Long getCountryId() { return countryId; }
    public void setCountryId(Long countryId) { this.countryId = countryId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public Long getCityId() { return cityId; }
    public void setCityId(Long cityId) { this.cityId = cityId; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public String getStationNameEn() { return stationNameEn; }
    public void setStationNameEn(String stationNameEn) { this.stationNameEn = stationNameEn; }
    public String getStationAlias() { return stationAlias; }
    public void setStationAlias(String stationAlias) { this.stationAlias = stationAlias; }
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
    public String getOpenDate() { return openDate; }
    public void setOpenDate(String openDate) { this.openDate = openDate; }
    public String getFirstTime() { return firstTime; }
    public void setFirstTime(String firstTime) { this.firstTime = firstTime; }
    public String getLastTime() { return lastTime; }
    public void setLastTime(String lastTime) { this.lastTime = lastTime; }
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
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
