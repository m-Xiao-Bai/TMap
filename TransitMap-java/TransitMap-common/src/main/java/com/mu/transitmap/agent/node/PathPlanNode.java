package com.mu.transitmap.agent.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.AgentNode;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.IMetroStationService;
import com.mu.transitmap.service.LlmLocationResolver;
import com.mu.transitmap.service.NearbyStationService;
import com.mu.transitmap.service.PathPlanningService;
import com.mu.transitmap.service.AmapClient;
import com.mu.transitmap.service.PathPlanningService.NearbyStation;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.LocationVO;
import com.mu.transitmap.vo.RoutePlanVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Node 4: 路径规划（多对多组合最优）
 *
 * 改进版思路（参考 geospatial-mcp-server 的 search_nearby 思路）：
 *   1. 起/终点各找 TopN 候选站（不只是最近 1 个）
 *   2. 名字精确命中时优先用名字结果（高确信度）
 *   3. 否则做 N × N 组合 BFS，按「总成本」选最优：
 *        总成本 = 接驳步行/骑车/打车时间 + 地铁站数 × 平均换乘时间
 *   4. 全部失败时 → 调 OSM Overpass API 看是否附近真有地铁站（本系统未录入）
 */
@Component
public class PathPlanNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(PathPlanNode.class);

    /** 名字匹配距 geo 超过此值时视为「名字匹配错了」，改用经纬度结果 */
    private static final double NAME_GEO_MAX_KM = 10.0;
    /** 接驳步行步速 km/min（5 km/h ≈ 0.083） */
    private static final double WALK_SPEED = 0.083;
    /** 共享单车速度 km/min（15 km/h ≈ 0.25） */
    private static final double BIKE_SPEED = 0.25;
    /** 打车/公交速度 km/min（30 km/h ≈ 0.5） */
    private static final double TAXI_SPEED = 0.5;
    /** 地铁每站平均耗时（分钟） */
    private static final double MIN_PER_STATION = 2.5;

    @Autowired
    private PathPlanningService pathPlanningService;

    @Autowired
    private NearbyStationService nearbyStationService;

    @Autowired
    private IMetroStationService stationService;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private LlmLocationResolver llmLocationResolver;

    @Autowired
    private AmapClient amapClient;

    @Override
    public void execute(AgentContext ctx, Consumer<Object> push) throws Exception {
        if (ctx.shouldShortCircuit()) return;

        Long cityId = ctx.getFromCityId() != null ? ctx.getFromCityId() : ctx.getToCityId();
        if (cityId == null) {
            ctx.setScenario("NO_METRO");
            ctx.setShortCircuit(true);
            return;
        }

        ctx.setFromDisplayName(pickDisplayName(ctx.getSlotFrom(), ctx.getFromGeo()));
        ctx.setToDisplayName(pickDisplayName(ctx.getSlotTo(), ctx.getToGeo()));

        String cityName = ctx.getFromCityName() != null ? ctx.getFromCityName() : ctx.getToCityName();

        // 1. 收集起/终点候选站
        List<NearbyStation> startCandidates = collectCandidates(ctx.getSlotFrom(), ctx.getFromGeo(), cityId, cityName, "起点");
        List<NearbyStation> endCandidates = collectCandidates(ctx.getSlotTo(), ctx.getToGeo(), cityId, cityName, "终点");

        // 2. 候选缺失时的细分场景 + OSM 兜底提示
        if (startCandidates.isEmpty() && endCandidates.isEmpty()) {
            ctx.setScenario("NO_STATIONS_FOUND");
            ctx.setShortCircuit(true);
            tryOsmHint(ctx, ctx.getFromGeo(), "起点");
            tryOsmHint(ctx, ctx.getToGeo(), "终点");
            return;
        }
        if (startCandidates.isEmpty()) {
            ctx.setScenario("START_NOT_FOUND");
            ctx.setShortCircuit(true);
            tryOsmHint(ctx, ctx.getFromGeo(), "起点");
            return;
        }
        if (endCandidates.isEmpty()) {
            ctx.setScenario("END_NOT_FOUND");
            ctx.setShortCircuit(true);
            tryOsmHint(ctx, ctx.getToGeo(), "终点");
            return;
        }

        // 3. 多对多组合规划：选总成本最小
        Combo best = pickBestCombination(startCandidates, endCandidates, cityId);
        if (best == null) {
            ctx.setScenario("NO_ROUTE");
            ctx.setShortCircuit(true);
            return;
        }

        if (best.start.station.getId().equals(best.end.station.getId())) {
            ctx.setScenario("SAME_STATION");
            ctx.setShortCircuit(true);
            return;
        }

        ctx.setRoutePlan(best.plan);
        ctx.setFromStationDistKm(best.start.distanceKm);
        ctx.setToStationDistKm(best.end.distanceKm);

        log.info("路径规划成功（多对多 best）: {} -> {}, 共 {} 站, 起接驳 {}km, 终接驳 {}km, 总成本 {}min",
                best.start.station.getStationName(), best.end.station.getStationName(),
                best.plan.getStationCount(), fmt(best.start.distanceKm), fmt(best.end.distanceKm),
                fmt(best.totalMinutes));
    }

    // ===== 候选收集 =====

    /**
     * 收集站点候选：LLM-first 工作流
     *
     * 优先级：
     *   1. LLM 直接询问最近地铁站（最高优先级，命中即返回）
     *   2. 站名精确匹配 metro_station
     *   3. geo TopN
     *   4. 站名模糊匹配
     *   5. LLM 标准名翻译（处理「小蛮腰」这种）
     */
    private List<NearbyStation> collectCandidates(String slotName, LocationVO geo, Long cityId,
                                                   String cityName, String label) {
        // ===== Step 1: LLM 询问最近地铁站（最高优先级）=====
        List<NearbyStation> llmFirst = collectFromLlmDirect(slotName, geo, cityId, cityName, label);
        if (!llmFirst.isEmpty()) {
            log.info("{} '{}': LLM-first 命中 {} 个候选", label, slotName, llmFirst.size());
            return llmFirst;
        }

        // ===== Step 2-4: 确定性算法兜底 =====
        List<NearbyStation> basic = collectCandidatesBasic(slotName, geo, cityId, label);
        if (!basic.isEmpty()) return basic;

        // ===== Step 5: 名字翻译兜底（处理俗称、方言）=====
        if (slotName == null || slotName.isBlank()) return basic;
        log.info("{} '{}': 常规匹配全空，尝试 LLM 标准名翻译", label, slotName);
        List<String> alternatives = llmLocationResolver.suggestStandardNames(slotName, cityName);
        for (String alt : alternatives) {
            if (alt.equalsIgnoreCase(slotName)) continue;
            LocationVO altGeo = null;
            try {
                altGeo = amapClient.geocode(alt, cityName);
            } catch (Exception ignored) {}
            List<NearbyStation> altResult = collectCandidatesBasic(alt, altGeo, cityId, label + "(LLM-name:" + alt + ")");
            if (!altResult.isEmpty()) {
                log.info("{} LLM 翻译 '{}' 命中 → {}", label, alt, altResult.get(0).station.getStationName());
                return altResult;
            }
        }
        return basic;
    }

    /**
     * Step 1: 【RAG 增强】把本城所有真实存在的站点列表喂给 LLM，让它从中挑选最近的 1~3 个
     *
     * 关键改进：
     *   - 旧版本只问 LLM「最近的站是？」，LLM 可能编造或拼写不一致（如「万寿宫站」vs DB 里「万寿宫」）
     *   - 新版本把 DB 真实站名列表作为上下文给 LLM，LLM 的回答严格在白名单内
     *   - 无须事后做 exactMatch 验证（但仍做防御性验证）
     */
    private List<NearbyStation> collectFromLlmDirect(String slotName, LocationVO geo, Long cityId,
                                                      String cityName, String label) {
        if (slotName == null || slotName.isBlank() || cityId == null) {
            return new ArrayList<>();
        }

        // 拉本城所有运营中站点（来自缓存，O(1)）
        List<MetroStation> allStations = stationService.getStationsByCityId(cityId);
        if (allStations == null || allStations.isEmpty()) return new ArrayList<>();

        // 提取站名列表（去重）
        java.util.LinkedHashSet<String> nameSet = new java.util.LinkedHashSet<>();
        for (MetroStation s : allStations) {
            if (s.getStationName() != null && !s.getStationName().isBlank()) {
                nameSet.add(s.getStationName().trim());
            }
        }
        List<String> stationNames = new ArrayList<>(nameSet);

        // 让 LLM 从列表中挑选（RAG 思路，禁止幻觉）
        List<String> picked = llmLocationResolver.pickNearestFromStationList(slotName, cityName, stationNames);
        if (picked.isEmpty()) return new ArrayList<>();

        List<NearbyStation> result = new ArrayList<>();
        for (String name : picked) {
            // 防御性验证（理论上 LLM 必然从列表选，但兜底）
            MetroStation s = exactMatch(name, cityId);
            if (s == null) s = likeMatch(name, cityId);
            if (s == null) {
                log.warn("{} LLM 返回站名 '{}' 不在 DB（理论不应发生），跳过", label, name);
                continue;
            }
            final Long sid = s.getId();
            if (result.stream().anyMatch(n -> n.station.getId().equals(sid))) continue;

            double dist = 0;
            if (geo != null && geo.getLat() != null && geo.getLng() != null
                    && s.getLatitude() != null && s.getLongitude() != null) {
                dist = haversine(s.getLatitude().doubleValue(), s.getLongitude().doubleValue(),
                        geo.getLat(), geo.getLng());
            }
            log.info("{} LLM-RAG 命中: '{}' → {} (dist={}km)",
                    label, name, s.getStationName(), fmt(dist));
            result.add(new NearbyStation(s, dist));
        }
        return result;
    }

    /**
     * 基础确定性匹配（无 LLM）：站名精确 + geo TopN + 名字模糊
     */
    private List<NearbyStation> collectCandidatesBasic(String slotName, LocationVO geo, Long cityId, String label) {
        List<NearbyStation> result = new ArrayList<>();

        // 名字精确匹配
        MetroStation exactByName = exactMatch(slotName, cityId);
        if (exactByName != null) {
            double dist = (geo != null && geo.getLat() != null && geo.getLng() != null)
                    ? haversine(exactByName.getLatitude().doubleValue(),
                                exactByName.getLongitude().doubleValue(),
                                geo.getLat(), geo.getLng())
                    : 0;
            log.info("{} '{}': 名字精确命中 → {} (dist={}km)",
                    label, slotName, exactByName.getStationName(), fmt(dist));
            result.add(new NearbyStation(exactByName, dist));
            return result;
        }

        // geo TopN
        if (geo != null && geo.getLat() != null && geo.getLng() != null) {
            List<NearbyStation> topN = nearbyStationService.findLocalCandidates(
                    geo.getLat(), geo.getLng(), cityId);
            if (!topN.isEmpty()) {
                log.info("{} '{}': geo TopN 候选 {} 个，最近 {} ({}km)",
                        label, slotName, topN.size(),
                        topN.get(0).station.getStationName(), fmt(topN.get(0).distanceKm));
                result.addAll(topN);
            }
        }

        // 名字模糊匹配补充
        MetroStation byName = likeMatch(slotName, cityId);
        if (byName != null) {
            boolean already = result.stream().anyMatch(n -> n.station.getId().equals(byName.getId()));
            if (!already) {
                double dist = (geo != null && geo.getLat() != null && geo.getLng() != null
                        && byName.getLatitude() != null && byName.getLongitude() != null)
                        ? haversine(byName.getLatitude().doubleValue(),
                                    byName.getLongitude().doubleValue(),
                                    geo.getLat(), geo.getLng())
                        : 0;
                if (dist <= NAME_GEO_MAX_KM) {
                    result.add(new NearbyStation(byName, dist));
                    log.info("{} '{}': 名字模糊匹配补充 → {}", label, slotName, byName.getStationName());
                }
            }
        }
        return result;
    }

    /**
     * 多对多组合：N × N 次 BFS，按总成本（接驳时间 + 地铁时间）选最优
     */
    private Combo pickBestCombination(List<NearbyStation> starts, List<NearbyStation> ends, Long cityId) {
        Combo best = null;
        for (NearbyStation s : starts) {
            for (NearbyStation e : ends) {
                if (s.station.getId().equals(e.station.getId())) continue;
                RoutePlanVO plan;
                try {
                    plan = pathPlanningService.planRoute(s.station.getId(), e.station.getId(), cityId);
                } catch (Exception ex) {
                    continue;
                }
                if (plan == null) continue;
                double total = totalCostMin(s.distanceKm, plan.getStationCount(), e.distanceKm);
                if (best == null || total < best.totalMinutes) {
                    best = new Combo(s, e, plan, total);
                }
            }
        }
        return best;
    }

    /**
     * 总成本 = 起接驳时间 + 地铁时间（站数 × 平均每站耗时）+ 终接驳时间
     * 接驳交通方式根据距离自动选最快的
     */
    private double totalCostMin(double startKm, int stationCount, double endKm) {
        return legMinutes(startKm) + stationCount * MIN_PER_STATION + legMinutes(endKm);
    }

    private double legMinutes(double km) {
        if (km < 0.5) return 0;
        if (km < 1.5) return km / WALK_SPEED;
        if (km < 3.0) return km / BIKE_SPEED;
        return km / TAXI_SPEED;
    }

    private static class Combo {
        final NearbyStation start;
        final NearbyStation end;
        final RoutePlanVO plan;
        final double totalMinutes;
        Combo(NearbyStation s, NearbyStation e, RoutePlanVO p, double t) {
            this.start = s; this.end = e; this.plan = p; this.totalMinutes = t;
        }
    }

    // ===== OSM 兜底提示 =====

    /**
     * 本地完全找不到时，调 OSM 看看附近是否有地铁站
     * 找到也只是写到日志/scenario hint，不参与 BFS（因为没有本地 station_id）
     */
    private void tryOsmHint(AgentContext ctx, LocationVO geo, String label) {
        if (geo == null || geo.getLat() == null || geo.getLng() == null) return;
        try {
            List<NearbyStationService.OsmStation> osm = nearbyStationService.findOsmCandidates(
                    geo.getLat(), geo.getLng());
            if (osm.isEmpty()) return;
            log.info("{} OSM 兜底找到 {} 个候选: {}", label, osm.size(),
                    osm.stream().limit(3).map(o -> o.name + "(" + fmt(o.distanceKm) + "km)").toList());
            // 把 OSM 结果存到 AgentContext.extras 也行，先用日志记录
        } catch (Exception ignored) {}
    }

    // ===== 辅助 =====

    private MetroStation exactMatch(String name, Long cityId) {
        if (name == null || name.isBlank() || cityId == null) return null;
        String raw = name.trim();
        String clean = raw.replaceAll("(地铁站|火车站|站)$", "").trim();
        List<MetroStation> list = stationService.list(new LambdaQueryWrapper<MetroStation>()
                .eq(MetroStation::getCityId, cityId)
                .eq(MetroStation::getStatusCode, 1)
                .and(w -> w.eq(MetroStation::getStationName, raw)
                        .or().eq(MetroStation::getStationName, clean)
                        .or().eq(MetroStation::getStationNameEn, raw)
                        .or().eq(MetroStation::getStationAlias, raw))
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private MetroStation likeMatch(String name, Long cityId) {
        if (name == null || name.isBlank() || cityId == null) return null;
        String raw = name.trim();
        String clean = raw.replaceAll("(地铁站|火车站|站)$", "").trim();
        if (clean.length() < 2) return null;
        List<MetroStation> list = stationService.list(new LambdaQueryWrapper<MetroStation>()
                .eq(MetroStation::getCityId, cityId)
                .eq(MetroStation::getStatusCode, 1)
                .and(w -> w.like(MetroStation::getStationName, clean)
                        .or().like(MetroStation::getStationAlias, clean))
                .last("LIMIT 5"));
        if (list.isEmpty()) return null;
        MetroStation best = list.get(0);
        for (MetroStation x : list) {
            if (x.getStationName() != null
                    && (best.getStationName() == null
                        || x.getStationName().length() < best.getStationName().length())) {
                best = x;
            }
        }
        return best;
    }

    private String pickDisplayName(String slot, LocationVO geo) {
        if (slot != null && !slot.isBlank()) return slot.trim();
        if (geo != null && geo.getFormattedAddress() != null && !geo.getFormattedAddress().isBlank()) {
            return geo.getFormattedAddress();
        }
        return null;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String fmt(Double d) {
        if (d == null) return "?";
        return String.format("%.2f", d);
    }
}
