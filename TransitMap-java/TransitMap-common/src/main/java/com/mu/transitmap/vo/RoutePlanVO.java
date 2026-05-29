package com.mu.transitmap.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoutePlanVO {
    private Long startStationId;
    private String startStationName;
    private Long endStationId;
    private String endStationName;
    private List<StationStop> stations;
    private List<TransferInfo> transfers;
    private Integer stationCount;
    private Integer price;
    private BigDecimal distanceKm;
    private Integer durationMinutes;
    private List<Long> stationIds;
    private List<String> stationNames;
    private List<Long> lineIds;
    private List<String> lineNames;

    /** 路线类型: fastest / fewest_transfers / shortest_distance */
    private String routeType;
    /** 路线标签: 最快路线 / 最少换乘 / 最短距离 */
    private String routeLabel;
    /** 是否推荐路线 */
    private Boolean recommended;

    @Data
    public static class StationStop {
        private Long stationId;
        private String stationName;
        private Long lineId;
        private String lineName;
        private String lineColor;
        private Boolean isTransfer;
    }

    @Data
    public static class TransferInfo {
        private Long stationId;
        private String stationName;
        private Long fromLineId;
        private String fromLineName;
        private Long toLineId;
        private String toLineName;
    }
}
