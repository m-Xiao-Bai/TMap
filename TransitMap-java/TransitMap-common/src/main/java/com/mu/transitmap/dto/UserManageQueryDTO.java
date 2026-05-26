package com.mu.transitmap.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserManageQueryDTO {

    private String keyword;

    private Integer statusCode;

    private Integer roleCode;

    private Integer onlineStatus;

    private String sortField;

    private String sortOrder;

    @Min(1)
    @NotNull
    private Integer pageNum;

    @Min(1)
    @NotNull
    private Integer pageSize;
}
