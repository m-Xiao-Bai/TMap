package com.mu.transitmap.controller;

import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.TicketOrder;
import com.mu.transitmap.entity.User;
import com.mu.transitmap.enums.CountryStatusEnum;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.enums.UserStatusEnum;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.CityServiceImpl;
import com.mu.transitmap.service.impl.CountryServiceImpl;
import com.mu.transitmap.service.impl.MetroLineServiceImpl;
import com.mu.transitmap.service.impl.MetroStationServiceImpl;
import com.mu.transitmap.service.impl.TicketOrderServiceImpl;
import com.mu.transitmap.service.impl.UserServiceImpl;
import com.mu.transitmap.vo.DashboardStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private CountryServiceImpl countryService;

    @Autowired
    private CityServiceImpl cityService;

    @Autowired
    private MetroLineServiceImpl metroLineService;

    @Autowired
    private MetroStationServiceImpl metroStationService;

    @Autowired
    private TicketOrderServiceImpl ticketOrderService;

    @GetMapping("/stats")
    public Result<DashboardStatsVO> getStats() {
        DashboardStatsVO vo = new DashboardStatsVO();

        List<User> allUsers = userService.list();
        List<Country> allCountries = countryService.list();
        List<TicketOrder> allOrders = ticketOrderService.list();

        // ═══ 基础统计 ═══
        vo.setTotalUsers(allUsers.size());
        vo.setTotalCountries(allCountries.size());
        vo.setTotalOrders(allOrders.size());

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        long onlineUsers = allUsers.stream()
                .filter(u -> UserStatusEnum.ONLINE.getCode() == u.getStatusCode())
                .count();
        long disabledUsers = allUsers.stream()
                .filter(u -> UserStatusEnum.DISABLED.getCode() == u.getStatusCode())
                .count();
        vo.setOnlineUsers(onlineUsers);
        vo.setDisabledUsers(disabledUsers);

        long todayNew = allUsers.stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(todayStart))
                .count();
        vo.setTodayNewUsers(todayNew);

        long onlineCountries = allCountries.stream()
                .filter(c -> CountryStatusEnum.ONLINE.getCode() == c.getStatusCode())
                .count();
        vo.setOnlineCountries(onlineCountries);

        vo.setTotalCities(cityService.count());
        vo.setTotalMetroLines(metroLineService.count());
        vo.setTotalMetroStations(metroStationService.count());

        // ═══ 订单统计 ═══
        long todayOrders = allOrders.stream()
                .filter(o -> o.getOrderTime() != null && o.getOrderTime().isAfter(todayStart))
                .count();
        vo.setTodayOrders(todayOrders);

        long totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == 1 || o.getStatus() == 2)
                .mapToLong(o -> o.getPrice() != null ? o.getPrice() : 0)
                .sum();
        vo.setTotalRevenue(totalRevenue);

        long todayRevenue = allOrders.stream()
                .filter(o -> (o.getStatus() == 1 || o.getStatus() == 2)
                        && o.getPayTime() != null && o.getPayTime().isAfter(todayStart))
                .mapToLong(o -> o.getPrice() != null ? o.getPrice() : 0)
                .sum();
        vo.setTodayRevenue(todayRevenue);

        // ═══ 近 7 天趋势 ═══
        LocalDate today = LocalDate.now();

        // 用户注册趋势
        List<Map<String, Object>> userTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            long count = allUsers.stream()
                    .filter(u -> u.getCreatedAt() != null
                            && !u.getCreatedAt().isBefore(dayStart)
                            && u.getCreatedAt().isBefore(dayEnd))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("value", count);
            userTrend.add(item);
        }
        vo.setUserRegistrationTrend(userTrend);

        // 订单趋势
        List<Map<String, Object>> orderTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            long count = allOrders.stream()
                    .filter(o -> o.getOrderTime() != null
                            && !o.getOrderTime().isBefore(dayStart)
                            && o.getOrderTime().isBefore(dayEnd))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("value", count);
            orderTrend.add(item);
        }
        vo.setOrderTrend(orderTrend);

        // ═══ 用户角色分布 ═══
        Map<Integer, Long> roleCountMap = new LinkedHashMap<>();
        for (UserRoleEnum role : UserRoleEnum.values()) {
            long count = allUsers.stream().filter(u -> role.getCode() == u.getRoleCode()).count();
            roleCountMap.put(role.getCode(), count);
        }
        List<Map<String, Object>> usersByRole = new ArrayList<>();
        for (UserRoleEnum role : UserRoleEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", role.getDesc());
            item.put("value", roleCountMap.getOrDefault(role.getCode(), 0L));
            usersByRole.add(item);
        }
        vo.setUsersByRole(usersByRole);

        // ═══ 用户在线状态分布 ═══
        Map<Integer, Long> statusCountMap = new LinkedHashMap<>();
        for (UserStatusEnum status : UserStatusEnum.values()) {
            long count = allUsers.stream().filter(u -> status.getCode() == u.getStatusCode()).count();
            statusCountMap.put(status.getCode(), count);
        }

        long onlineCount = statusCountMap.getOrDefault(UserStatusEnum.ONLINE.getCode(), 0L);
        long offlineCount = allUsers.size() - onlineCount;

        List<Map<String, Object>> usersByOnlineStatus = new ArrayList<>();
        Map<String, Object> onlineItem = new LinkedHashMap<>();
        onlineItem.put("name", "在线");
        onlineItem.put("value", onlineCount);
        usersByOnlineStatus.add(onlineItem);
        Map<String, Object> offlineItem = new LinkedHashMap<>();
        offlineItem.put("name", "离线");
        offlineItem.put("value", offlineCount);
        usersByOnlineStatus.add(offlineItem);
        vo.setUsersByOnlineStatus(usersByOnlineStatus);

        // ═══ 用户账号状态分布 ═══
        long enabledCount = allUsers.size()
                - statusCountMap.getOrDefault(UserStatusEnum.DISABLED.getCode(), 0L)
                - statusCountMap.getOrDefault(UserStatusEnum.EXCEPTION.getCode(), 0L);
        long disabledCount = statusCountMap.getOrDefault(UserStatusEnum.DISABLED.getCode(), 0L);

        List<Map<String, Object>> usersByAccountStatus = new ArrayList<>();
        Map<String, Object> enabledItem = new LinkedHashMap<>();
        enabledItem.put("name", "启用");
        enabledItem.put("value", enabledCount);
        usersByAccountStatus.add(enabledItem);
        Map<String, Object> disabledItem = new LinkedHashMap<>();
        disabledItem.put("name", "禁用");
        disabledItem.put("value", disabledCount);
        usersByAccountStatus.add(disabledItem);
        vo.setUsersByAccountStatus(usersByAccountStatus);

        // ═══ 国家状态分布 ═══
        List<Map<String, Object>> countriesByStatus = new ArrayList<>();
        for (CountryStatusEnum status : CountryStatusEnum.values()) {
            long count = allCountries.stream().filter(c -> status.getCode() == c.getStatusCode()).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", status.getDescription());
            item.put("value", count);
            countriesByStatus.add(item);
        }
        vo.setCountriesByStatus(countriesByStatus);

        // ═══ 订单状态分布 ═══
        String[] statusNames = {"待支付", "已支付", "已使用", "已过期", "已退票", "审核中"};
        List<Map<String, Object>> ordersByStatus = new ArrayList<>();
        for (int s = 0; s <= 5; s++) {
            final int status = s;
            long count = allOrders.stream().filter(o -> status == o.getStatus()).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", statusNames[s]);
            item.put("value", count);
            ordersByStatus.add(item);
        }
        vo.setOrdersByStatus(ordersByStatus);

        // ═══ 最近注册用户 ═══
        List<Map<String, Object>> recentRegistrations = new ArrayList<>();
        allUsers.stream()
                .filter(u -> u.getCreatedAt() != null)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .forEach(u -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("username", u.getUsername());
                    item.put("role", u.getRole());
                    item.put("createdAt", u.getCreatedAt().toString());
                    recentRegistrations.add(item);
                });
        vo.setRecentRegistrations(recentRegistrations);

        return Result.success(vo);
    }
}
