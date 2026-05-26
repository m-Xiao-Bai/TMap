package com.mu.transitmap.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DashboardStatsVO {

    private long totalUsers;
    private long onlineUsers;
    private long disabledUsers;
    private long todayNewUsers;
    private long totalCountries;
    private long onlineCountries;

    private long totalCities;
    private long totalMetroLines;
    private long totalMetroStations;

    private List<Map<String, Object>> usersByRole;
    private List<Map<String, Object>> usersByOnlineStatus;
    private List<Map<String, Object>> usersByAccountStatus;
    private List<Map<String, Object>> countriesByStatus;
    private List<Map<String, Object>> recentRegistrations;

    // 订单统计
    private long totalOrders;
    private long todayOrders;
    private long totalRevenue;
    private long todayRevenue;

    // 趋势数据（近 7 天）
    private List<Map<String, Object>> userRegistrationTrend;
    private List<Map<String, Object>> orderTrend;

    // 订单状态分布
    private List<Map<String, Object>> ordersByStatus;
}
