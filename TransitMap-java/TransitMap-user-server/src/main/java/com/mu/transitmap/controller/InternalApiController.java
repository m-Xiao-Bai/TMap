package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.entity.*;
import com.mu.transitmap.mapper.ChatMessageMapper;
import com.mu.transitmap.mapper.ChatSessionMapper;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.*;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.LocationVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 内部 API — 仅供 Python Agent 服务调用
 */
@RestController
@RequestMapping("/api/internal")
public class InternalApiController {

    @Autowired private AmapClient amapClient;
    @Autowired private ICityService cityService;
    @Autowired private IMetroStationService stationService;
    @Autowired private IMetroLineService lineService;
    @Autowired private PathPlanningService pathPlanningService;
    @Autowired private ITicketOrderService ticketOrderService;
    @Autowired private SystemConfigServiceImpl configService;
    @Autowired private ChatSessionMapper chatSessionMapper;
    @Autowired private ChatMessageMapper chatMessageMapper;

    // ══════════════════════════════════
    // 地理编码
    // ══════════════════════════════════

    @PostMapping("/geo/geocode")
    public Result<LocationVO> geocode(@RequestBody Map<String, String> body) {
        String address = body.get("address");
        String city = body.get("city");
        if (address == null || address.isEmpty()) {
            return Result.fail(400, "缺少 address 参数");
        }
        LocationVO loc = amapClient.geocode(address, city);
        if (loc == null) {
            return Result.fail(500, "地理编码失败");
        }
        return Result.success(loc);
    }

    @PostMapping("/geo/regeo")
    public Result<LocationVO> regeo(@RequestBody Map<String, Double> body) {
        Double lng = body.get("lng");
        Double lat = body.get("lat");
        if (lng == null || lat == null) {
            return Result.fail(400, "缺少 lng/lat 参数");
        }
        LocationVO loc = amapClient.regeo(lng, lat);
        if (loc == null) {
            return Result.fail(500, "逆地理编码失败");
        }
        return Result.success(loc);
    }

    @GetMapping("/geo/ip-locate")
    public Result<LocationVO> ipLocate(@RequestParam(required = false) String ip,
                                        HttpServletRequest request) {
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
        }
        LocationVO loc = amapClient.ipLocate(ip);
        if (loc == null) {
            return Result.fail(500, "IP 定位失败");
        }
        return Result.success(loc);
    }

    @PostMapping("/geo/poi-search")
    public Result<List<Map<String, Object>>> poiSearch(@RequestBody Map<String, Object> body) {
        String keywords = (String) body.get("keywords");
        String city = (String) body.get("city");
        if (keywords == null || keywords.isEmpty()) {
            return Result.fail(400, "缺少 keywords 参数");
        }
        List<LocationVO> results = amapClient.placeSearch(keywords, city);
        List<Map<String, Object>> list = new ArrayList<>();
        if (results != null) {
            for (LocationVO loc : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", loc.getAddress());
                item.put("address", loc.getFormattedAddress() != null ? loc.getFormattedAddress() : loc.getAddress());
                item.put("lat", loc.getLat());
                item.put("lng", loc.getLng());
                item.put("city", loc.getCity());
                list.add(item);
            }
        }
        return Result.success(list);
    }

    // ══════════════════════════════════
    // 城市
    // ══════════════════════════════════

    @GetMapping("/city/match")
    public Result<City> matchCity(@RequestParam String name) {
        if (name == null || name.isEmpty()) {
            return Result.fail(400, "缺少城市名");
        }
        // 模糊匹配城市名
        City city = cityService.getOne(new LambdaQueryWrapper<City>()
                .like(City::getCityName, name.replace("市", ""))
                .last("LIMIT 1"));
        return Result.success(city);
    }

    @GetMapping("/city/{id}")
    public Result<City> getCity(@PathVariable Long id) {
        City city = cityService.getById(id);
        if (city == null) {
            return Result.fail(404, "城市不存在");
        }
        return Result.success(city);
    }

    // ══════════════════════════════════
    // 站点
    // ══════════════════════════════════

    @GetMapping("/station/nearest")
    public Result<List<Map<String, Object>>> findNearestStations(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam Long cityId,
            @RequestParam(defaultValue = "5") int limit) {
        // 获取城市所有站点，按距离排序
        List<MetroStation> allStations = stationService.getStationsByCityId(cityId);
        if (allStations == null || allStations.isEmpty()) {
            return Result.success(List.of());
        }

        // 计算距离并排序
        List<Map<String, Object>> stationDists = new ArrayList<>();
        for (MetroStation s : allStations) {
            if (s.getLatitude() == null || s.getLongitude() == null) continue;
            if (s.getLatitude().doubleValue() == 0 || s.getLongitude().doubleValue() == 0) continue;
            double dist = haversine(lat, lng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("stationName", s.getStationName());
            item.put("stationNameEn", s.getStationNameEn());
            item.put("lat", s.getLatitude() != null ? s.getLatitude().doubleValue() : 0);
            item.put("lng", s.getLongitude() != null ? s.getLongitude().doubleValue() : 0);
            item.put("lineNames", s.getLineNames());
            item.put("cityId", s.getCityId());
            item.put("distance", Math.round(dist));
            stationDists.add(item);
        }

        // 按距离排序
        stationDists.sort(Comparator.comparingDouble(d -> (Integer) d.get("distance")));
        if (stationDists.size() > limit) {
            stationDists = stationDists.subList(0, limit);
        }

        return Result.success(stationDists);
    }

    @GetMapping("/station/by-city")
    public Result<List<MetroStation>> getStationsByCity(@RequestParam Long cityId) {
        List<MetroStation> stations = stationService.getStationsByCityId(cityId);
        return Result.success(stations != null ? stations : List.of());
    }

    @GetMapping("/station/{id}")
    public Result<MetroStation> getStation(@PathVariable Long id) {
        MetroStation station = stationService.getById(id);
        if (station == null) {
            return Result.fail(404, "站点不存在");
        }
        return Result.success(station);
    }

    // ══════════════════════════════════
    // 路径规划
    // ══════════════════════════════════

    @PostMapping("/route/plan")
    public Result<?> planRoute(@RequestBody Map<String, Long> body) {
        Long fromId = body.get("fromStationId");
        Long toId = body.get("toStationId");
        if (fromId == null || toId == null) {
            return Result.fail(400, "缺少 fromStationId/toStationId");
        }
        try {
            MetroStation fromStation = stationService.getById(fromId);
            if (fromStation == null) {
                return Result.fail(404, "出发站不存在");
            }
            Long cityId = fromStation.getCityId();
            var route = pathPlanningService.planRoute(fromId, toId, cityId);
            return Result.success(route);
        } catch (Exception e) {
            return Result.fail(500, "路径规划失败: " + e.getMessage());
        }
    }

    /**
     * 多方案路径规划（最快 / 最少换乘 / 最短距离）
     */
    @PostMapping("/route/plan-multiple")
    public Result<?> planMultipleRoutes(@RequestBody Map<String, Long> body) {
        Long fromId = body.get("fromStationId");
        Long toId = body.get("toStationId");
        if (fromId == null || toId == null) {
            return Result.fail(400, "缺少 fromStationId/toStationId");
        }
        try {
            MetroStation fromStation = stationService.getById(fromId);
            if (fromStation == null) {
                return Result.fail(404, "出发站不存在");
            }
            Long cityId = fromStation.getCityId();
            var routes = pathPlanningService.planMultipleRoutes(fromId, toId, cityId);
            return Result.success(routes);
        } catch (Exception e) {
            return Result.fail(500, "路径规划失败: " + e.getMessage());
        }
    }

    // ══════════════════════════════════
    // 订单
    // ══════════════════════════════════

    @PostMapping("/order/create")
    public Result<?> createOrder(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        Long startStationId = body.get("startStationId") != null ? Long.valueOf(body.get("startStationId").toString()) : null;
        Long endStationId = body.get("endStationId") != null ? Long.valueOf(body.get("endStationId").toString()) : null;
        int quantity = body.get("quantity") != null ? Integer.parseInt(body.get("quantity").toString()) : 1;

        if (userId == null || startStationId == null || endStationId == null) {
            return Result.fail(400, "缺少必要参数");
        }
        try {
            var order = ticketOrderService.createOrders(userId, startStationId, endStationId, quantity);
            return Result.success(order);
        } catch (Exception e) {
            return Result.fail(500, "创建订单失败: " + e.getMessage());
        }
    }

    // ══════════════════════════════════
    // 会话历史
    // ══════════════════════════════════

    @GetMapping("/session/{id}/history")
    public Result<List<ChatMessage>> getSessionHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, id)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT " + Math.min(limit, 50)));
        return Result.success(messages);
    }

    // ══════════════════════════════════
    // 配置
    // ══════════════════════════════════

    @GetMapping("/config/agent-prompt")
    public Result<Map<String, String>> getAgentPrompt(@RequestParam String key) {
        String value = configService.getConfigValue(key);
        if (value == null) value = "";
        Map<String, String> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        return Result.success(result);
    }

    // ══════════════════════════════════
    // 健康检查
    // ══════════════════════════════════

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("service", "TransitMap Java Backend");
        return Result.success(data);
    }

    // ══════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
