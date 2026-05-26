package com.mu.transitmap.dto;

public class MetroStationManageQueryDTO {

    private String keyword;
    private Long countryId;
    private Long cityId;
    private Integer isTransfer;
    private Integer stationType;
    private Integer statusCode;
    private Long lineId;
    private String sortField;
    private String sortOrder;
    private Integer pageNum;
    private Integer pageSize;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getCountryId() { return countryId; }
    public void setCountryId(Long countryId) { this.countryId = countryId; }
    public Long getCityId() { return cityId; }
    public void setCityId(Long cityId) { this.cityId = cityId; }
    public Integer getIsTransfer() { return isTransfer; }
    public void setIsTransfer(Integer isTransfer) { this.isTransfer = isTransfer; }
    public Integer getStationType() { return stationType; }
    public void setStationType(Integer stationType) { this.stationType = stationType; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }
    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
