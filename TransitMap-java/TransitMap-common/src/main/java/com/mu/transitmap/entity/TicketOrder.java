package com.mu.transitmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("ticket_order")
public class TicketOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long startStationId;
    private String startStationName;
    private Long endStationId;
    private String endStationName;
    private Integer stationCount;
    private String stationIds;
    private String stationNames;
    private String lineIds;
    private String lineNames;
    private Integer price;
    private BigDecimal distanceKm;
    private Integer durationMinutes;
    private Integer status;
    private String qrCode;
    private LocalDateTime qrExpireTime;
    private LocalDateTime orderTime;
    private LocalDateTime payTime;
    private Long entryStationId;
    private String entryStationName;
    private LocalDateTime entryTime;
    private Long exitStationId;
    private String exitStationName;
    private LocalDateTime exitTime;
    private LocalDateTime refundTime;
    private String refundReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
