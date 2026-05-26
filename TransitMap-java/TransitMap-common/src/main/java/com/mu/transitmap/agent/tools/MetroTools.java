package com.mu.transitmap.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.*;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.LocationVO;
import com.mu.transitmap.vo.RoutePlanVO;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 地铁 Agent 工具集
 *
 * 只暴露两个 @Tool 给 LLM：
 * 1. planMetroTrip — 查询路线（内部完成：地点解析→城市匹配→站点查找→路线规划）
 * 2. createOrder — 下单购票
 *
 * 减少工具数量避免 LLM 混淆，所有业务逻辑在 Java 代码中确定性执行。
 */
@Component
public class MetroTools {

    private static final Logger log = LoggerFactory.getLogger(MetroTools.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ===== ThreadLocal：引擎 ↔ @Tool 之间的数据传递 =====

    /** 路线规划结果 */
    private static final ThreadLocal<RoutePlanVO> LAST_ROUTE_PLAN = new ThreadLocal<>();
    public static RoutePlanVO consumeLastRoutePlan() {
        RoutePlanVO p = LAST_ROUTE_PLAN.get();
        LAST_ROUTE_PLAN.remove();
        return p;
    }

    /** 无地铁城市名 */
    private static final ThreadLocal<String> LAST_NO_METRO_CITY = new ThreadLocal<>();
    public static String consumeNoMetroCity() {
        String c = LAST_NO_METRO_CITY.get();
        LAST_NO_METRO_CITY.remove();
        return c;
    }

    /** 场景标识 */
    private static final ThreadLocal<String> LAST_SCENARIO = new ThreadLocal<>();
    public static String consumeScenario() {
        String s = LAST_SCENARIO.get();
        LAST_SCENARIO.remove();
        return s;
    }

    /** 当前用户 ID（引擎设置） */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    public static void setCurrentUserId(Long userId) { CURRENT_USER_ID.set(userId); }
    public static void clearCurrentUserId() { CURRENT_USER_ID.remove(); }

    /** 下单结果 */
    private static final ThreadLocal<List<Map<String, Object>>> LAST_ORDER_RESULT = new ThreadLocal<>();
    public static List<Map<String, Object>> consumeOrderResult() {
        List<Map<String, Object>> r = LAST_ORDER_RESULT.get();
        LAST_ORDER_RESULT.remove();
        return r;
    }

    // ===== 注入的服务 =====

    @Autowired private AmapClient amapClient;
    @Autowired private ICityService cityService;
    @Autowired private IMetroStationService stationService;
    @Autowired private PathPlanningService pathPlanningService;
    @Autowired private LlmLocationResolver llmLocationResolver;
    @Autowired private SystemConfigServiceImpl configService;
    @Autowired private ITicketOrderService ticketOrderService;

    // ================================================================
    //  Tool 1：查询地铁路线（步骤 1+2+3 一体化）
    // ================================================================

    @Tool("查询从出发地到目的地的地铁路线。传入出发地名称、目的地名称，可选传城市名。" +
          "返回路线详情（站名、站数、时间、票价）或错误信息。")
    public String planMetroTrip(String from, String to, String city) {
        if (isBlank(from) || isBlank(to)) {
            return json(Map.of("success", false, "error", "请告诉我出发地和目的地"));
        }

        try {
            // ── 步骤 1：解析地点坐标 ──
            String cityHint = isBlank(city) ? null : city.trim();
            LocationVO fromGeo = amapClient.geocode(from.trim(), cityHint);
            LocationVO toGeo = amapClient.geocode(to.trim(), cityHint);

            if (fromGeo == null && toGeo == null) {
                LAST_SCENARIO.set("LOCATION_NOT_FOUND");
                return json(Map.of("success", false, "scenario", "LOCATION_NOT_FOUND",
                        "error", "无法识别「" + from + "」和「" + to + "」，请换个说法"));
            }

            // ── 步骤 2：匹配城市 ──
            String resolvedCity = pickCity(fromGeo, toGeo, city);
            City dbCity = findCityInDb(resolvedCity);
            if (dbCity == null && fromGeo != null) dbCity = findCityInDb(fromGeo.getCity());
            if (dbCity == null && toGeo != null) dbCity = findCityInDb(toGeo.getCity());

            if (dbCity == null) {
                String cityName = resolvedCity != null ? resolvedCity.replaceAll("市$", "").trim() : "该城市";
                LAST_NO_METRO_CITY.set(cityName);
                LAST_SCENARIO.set("NO_METRO");
                return json(Map.of("success", false, "scenario", "NO_METRO",
                        "cityName", cityName,
                        "error", cityName + "暂未开通地铁，你可以通知管理员尽快添加"));
            }

            // 跨城检测
            if (fromGeo != null && toGeo != null) {
                City fc = findCityInDb(fromGeo.getCity());
                City tc = findCityInDb(toGeo.getCity());
                if (fc != null && tc != null && !fc.getId().equals(tc.getId())) {
                    LAST_SCENARIO.set("CROSS_CITY");
                    return json(Map.of("success", false, "scenario", "CROSS_CITY",
                            "error", "出发地在" + fc.getCityName() + "，目的地在" + tc.getCityName() + "，暂不支持跨城"));
                }
            }

            Long cityId = dbCity.getId();
            String cityName = dbCity.getCityName();

            // ── 步骤 3a：查找最近地铁站（通过大模型从真实站点列表中选取） ──
            List<String> allStationNames = getStationNames(cityId);
            if (allStationNames.isEmpty()) {
                LAST_SCENARIO.set("NO_STATIONS");
                return json(Map.of("success", false, "scenario", "NO_STATIONS",
                        "error", cityName + "暂无地铁站点数据"));
            }

            // 出发地最近站
            List<String> fromCandidates = llmLocationResolver.pickNearestFromStationList(from, cityName, allStationNames);
            if (fromCandidates.isEmpty()) fromCandidates = fallbackMatch(from, cityId);
            if (fromCandidates.isEmpty()) {
                LAST_SCENARIO.set("START_NOT_FOUND");
                return json(Map.of("success", false, "scenario", "START_NOT_FOUND",
                        "error", "找不到「" + from + "」附近的地铁站，请换个地点"));
            }

            // 目的地最近站
            List<String> toCandidates = llmLocationResolver.pickNearestFromStationList(to, cityName, allStationNames);
            if (toCandidates.isEmpty()) toCandidates = fallbackMatch(to, cityId);
            if (toCandidates.isEmpty()) {
                LAST_SCENARIO.set("END_NOT_FOUND");
                return json(Map.of("success", false, "scenario", "END_NOT_FOUND",
                        "error", "找不到「" + to + "」附近的地铁站，请换个地点"));
            }

            // ── 步骤 3b：N×N 最短路径规划 ──
            RoutePlanVO bestPlan = null;
            for (String fn : fromCandidates) {
                MetroStation fs = resolveStation(fn, cityId);
                if (fs == null) continue;
                for (String tn : toCandidates) {
                    MetroStation ts = resolveStation(tn, cityId);
                    if (ts == null || fs.getId().equals(ts.getId())) continue;
                    RoutePlanVO plan = pathPlanningService.planRoute(fs.getId(), ts.getId(), cityId);
                    if (plan != null && (bestPlan == null || plan.getStationCount() < bestPlan.getStationCount())) {
                        bestPlan = plan;
                    }
                }
            }

            if (bestPlan == null) {
                LAST_SCENARIO.set("NO_ROUTE");
                return json(Map.of("success", false, "scenario", "NO_ROUTE",
                        "error", "这两站之间没有可用地铁路线"));
            }

            // ── 成功：存入 ThreadLocal ──
            LAST_ROUTE_PLAN.set(bestPlan);
            LAST_SCENARIO.set("ROUTE_OK");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("scenario", "ROUTE_OK");
            result.put("cityName", cityName);
            result.put("startStationId", bestPlan.getStartStationId());
            result.put("startStationName", bestPlan.getStartStationName());
            result.put("endStationId", bestPlan.getEndStationId());
            result.put("endStationName", bestPlan.getEndStationName());
            result.put("stationCount", bestPlan.getStationCount());
            result.put("durationMinutes", bestPlan.getDurationMinutes());
            result.put("price", bestPlan.getPrice());
            result.put("distanceKm", bestPlan.getDistanceKm());
            result.put("stationNames", bestPlan.getStationNames());
            if (bestPlan.getTransfers() != null && !bestPlan.getTransfers().isEmpty()) {
                List<Map<String, String>> transfers = new ArrayList<>();
                for (RoutePlanVO.TransferInfo t : bestPlan.getTransfers()) {
                    transfers.add(Map.of("station", t.getStationName(),
                            "from", t.getFromLineName(), "to", t.getToLineName()));
                }
                result.put("transfers", transfers);
            }
            return json(result);

        } catch (Exception e) {
            log.error("planMetroTrip failed: {} -> {}", from, to, e);
            return json(Map.of("success", false, "error", "查询出错: " + e.getMessage()));
        }
    }

    // ================================================================
    //  Tool 2：下单购票（步骤 4）
    // ================================================================

    @Tool("为用户创建地铁车票订单。使用上一次路线查询返回的 startStationId 和 endStationId。quantity 默认为 1。")
    public String createOrder(long startStationId, long endStationId, int quantity) {
        Long userId = CURRENT_USER_ID.get();
        if (userId == null) {
            return json(Map.of("success", false, "error", "请先登录后再下单"));
        }
        if (startStationId == endStationId) {
            return json(Map.of("success", false, "error", "出发站和目的站相同，无需购票"));
        }
        if (quantity < 1 || quantity > 10) quantity = 1;

        try {
            List<Map<String, Object>> orders = ticketOrderService.createOrders(
                    userId, startStationId, endStationId, quantity);
            LAST_ORDER_RESULT.set(orders);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("orderCount", orders.size());
            result.put("orders", orders);
            return json(result);
        } catch (Exception e) {
            log.error("createOrder failed: {} -> {}", startStationId, endStationId, e);
            return json(Map.of("success", false, "error", "下单失败: " + e.getMessage()));
        }
    }

    // ================================================================
    //  内部辅助方法
    // ================================================================

    private String pickCity(LocationVO from, LocationVO to, String hint) {
        if (!isBlank(hint)) return hint.trim();
        if (from != null && !isBlank(from.getCity())) return from.getCity();
        if (to != null && !isBlank(to.getCity())) return to.getCity();
        return null;
    }

    private City findCityInDb(String name) {
        if (isBlank(name)) return null;
        String clean = name.replaceAll("市$", "").trim();
        List<City> list = cityService.list(new LambdaQueryWrapper<City>()
                .and(w -> w.like(City::getCityName, clean)
                        .or().like(City::getCityNameEn, clean)
                        .or().like(City::getCityAlias, clean))
                .eq(City::getStatusCode, 3));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<String> getStationNames(Long cityId) {
        List<MetroStation> all = stationService.getStationsByCityId(cityId);
        if (all == null || all.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (MetroStation s : all) {
            if (s.getStationName() != null && !s.getStationName().isBlank()) set.add(s.getStationName().trim());
        }
        return new ArrayList<>(set);
    }

    private List<String> fallbackMatch(String name, Long cityId) {
        MetroStation s = exactMatch(name, cityId);
        return s != null ? List.of(s.getStationName()) : List.of();
    }

    private MetroStation resolveStation(String name, Long cityId) {
        MetroStation s = exactMatch(name, cityId);
        return s != null ? s : likeMatch(name, cityId);
    }

    private MetroStation exactMatch(String name, Long cityId) {
        if (isBlank(name) || cityId == null) return null;
        String raw = name.trim();
        String clean = raw.replaceAll("(地铁站|火车站|站)$", "").trim();
        List<MetroStation> list = stationService.list(new LambdaQueryWrapper<MetroStation>()
                .eq(MetroStation::getCityId, cityId).eq(MetroStation::getStatusCode, 1)
                .and(w -> w.eq(MetroStation::getStationName, raw)
                        .or().eq(MetroStation::getStationName, clean)
                        .or().eq(MetroStation::getStationNameEn, raw)
                        .or().eq(MetroStation::getStationAlias, raw))
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private MetroStation likeMatch(String name, Long cityId) {
        if (isBlank(name) || cityId == null) return null;
        String clean = name.trim().replaceAll("(地铁站|火车站|站)$", "").trim();
        if (clean.length() < 2) return null;
        List<MetroStation> list = stationService.list(new LambdaQueryWrapper<MetroStation>()
                .eq(MetroStation::getCityId, cityId).eq(MetroStation::getStatusCode, 1)
                .and(w -> w.like(MetroStation::getStationName, clean)
                        .or().like(MetroStation::getStationAlias, clean))
                .last("LIMIT 5"));
        if (list.isEmpty()) return null;
        MetroStation best = list.get(0);
        for (MetroStation x : list) {
            if (x.getStationName() != null && (best.getStationName() == null
                    || x.getStationName().length() < best.getStationName().length())) best = x;
        }
        return best;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private String json(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) { return "{\"success\":false,\"error\":\"JSON序列化失败\"}"; }
    }
}
