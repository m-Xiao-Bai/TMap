package com.mu.transitmap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.RoutePlanVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 路径规划服务：BFS 最短路径 + 路线详情构建
 */
@Service
public class PathPlanningService {

    private static final Logger log = LoggerFactory.getLogger(PathPlanningService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TIER_LIST = new TypeReference<>() {};

    @Autowired
    private IMetroStationService stationService;

    @Autowired
    private IMetroLineService lineService;

    @Autowired
    private SystemConfigServiceImpl configService;

    /**
     * 规划从 startStation 到 endStation 的地铁路线
     */
    public RoutePlanVO planRoute(Long startStationId, Long endStationId, Long cityId) {
        // 1. 获取城市所有运营站点
        List<MetroStation> stations = stationService.getStationsByCityId(cityId);
        if (stations == null || stations.isEmpty()) return null;

        Map<Long, MetroStation> stationMap = new HashMap<>();
        Map<Long, List<Long>> graph = new HashMap<>();
        for (MetroStation s : stations) {
            stationMap.put(s.getId(), s);
            List<Long> neighbors = new ArrayList<>();
            neighbors.addAll(parseIdList(s.getPrevStationIds()));
            neighbors.addAll(parseIdList(s.getNextStationIds()));
            graph.put(s.getId(), neighbors);
        }

        // 2. BFS 最短路径
        List<Long> path = bfsShortestPath(startStationId, endStationId, graph);
        if (path == null || path.size() < 2) return null;

        // 3. 构建路线详情
        return buildRoutePlan(path, stationMap);
    }

    /**
     * 找到离经纬度最近的站点
     */
    public MetroStation findNearestStation(double lat, double lng, Long cityId) {
        List<NearbyStation> top = findNearbyStations(lat, lng, cityId, Double.MAX_VALUE, 1);
        return top.isEmpty() ? null : top.get(0).station;
    }

    /**
     * 找到离经纬度最近的 N 个站点（半径上限 maxKm）
     * 返回按距离升序的列表，每条包含站点和距离
     */
    public List<NearbyStation> findNearbyStations(double lat, double lng, Long cityId,
                                                  double maxKm, int topN) {
        List<MetroStation> stations = stationService.getStationsByCityId(cityId);
        if (stations == null || stations.isEmpty() || topN <= 0) {
            return new ArrayList<>();
        }
        // 用最小堆 / 简化为全排序
        List<NearbyStation> all = new ArrayList<>();
        for (MetroStation s : stations) {
            if (s.getLatitude() == null || s.getLongitude() == null) continue;
            double dist = haversine(lat, lng,
                    s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
            if (dist <= maxKm) {
                all.add(new NearbyStation(s, dist));
            }
        }
        all.sort(Comparator.comparingDouble(n -> n.distanceKm));
        return all.subList(0, Math.min(topN, all.size()));
    }

    public static class NearbyStation {
        public final MetroStation station;
        public final double distanceKm;
        public NearbyStation(MetroStation station, double distanceKm) {
            this.station = station;
            this.distanceKm = distanceKm;
        }
    }

    private RoutePlanVO buildRoutePlan(List<Long> path, Map<Long, MetroStation> stationMap) {
        RoutePlanVO plan = new RoutePlanVO();
        plan.setStationCount(path.size() - 1);

        // 收集站点信息
        List<RoutePlanVO.StationStop> stops = new ArrayList<>();
        List<Long> stationIds = new ArrayList<>();
        List<String> stationNames = new ArrayList<>();
        List<Long> lineIds = new ArrayList<>();
        List<String> lineNames = new ArrayList<>();
        List<RoutePlanVO.TransferInfo> transfers = new ArrayList<>();

        Long currentLineId = null;
        String currentLineName = null;
        String currentLineColor = null;

        for (int i = 0; i < path.size(); i++) {
            MetroStation station = stationMap.get(path.get(i));
            if (station == null) continue;

            stationIds.add(station.getId());
            stationNames.add(station.getStationName());

            // 确定当前线路
            Long lineId = determineLine(path, i, stationMap, currentLineId);
            if (lineId != null && !lineId.equals(currentLineId)) {
                if (currentLineId != null && i > 0) {
                    // 换乘记录
                    RoutePlanVO.TransferInfo transfer = new RoutePlanVO.TransferInfo();
                    transfer.setStationId(station.getId());
                    transfer.setStationName(station.getStationName());
                    transfer.setFromLineId(currentLineId);
                    transfer.setFromLineName(currentLineName);
                    transfer.setToLineId(lineId);
                    MetroLine newLine = lineService.getById(lineId);
                    transfer.setToLineName(newLine != null ? newLine.getLineName() : "");
                    transfers.add(transfer);
                }
                currentLineId = lineId;
                MetroLine line = lineService.getById(lineId);
                currentLineName = line != null ? line.getLineName() : "";
                currentLineColor = line != null ? line.getLineColor() : "#999";
                if (!lineIds.contains(lineId)) {
                    lineIds.add(lineId);
                    lineNames.add(currentLineName);
                }
            }

            RoutePlanVO.StationStop stop = new RoutePlanVO.StationStop();
            stop.setStationId(station.getId());
            stop.setStationName(station.getStationName());
            stop.setLineId(currentLineId);
            stop.setLineName(currentLineName);
            stop.setLineColor(currentLineColor);
            stop.setIsTransfer(station.getIsTransfer() != null && station.getIsTransfer() == 1);
            stops.add(stop);
        }

        plan.setStations(stops);
        plan.setTransfers(transfers);
        plan.setStationIds(stationIds);
        plan.setStationNames(stationNames);
        plan.setLineIds(lineIds);
        plan.setLineNames(lineNames);

        if (!stationIds.isEmpty()) {
            plan.setStartStationId(stationIds.get(0));
            plan.setStartStationName(stationNames.get(0));
            plan.setEndStationId(stationIds.get(stationIds.size() - 1));
            plan.setEndStationName(stationNames.get(stationNames.size() - 1));
        }

        // 计算距离、时间、票价
        double totalKm = calculateDistance(path, stationMap);
        plan.setDistanceKm(BigDecimal.valueOf(totalKm).setScale(1, RoundingMode.HALF_UP));

        Map<String, Object> params = configService.getConfigObject(
                "ticket.estimate_params", new TypeReference<Map<String, Object>>() {});
        double minutesPerStop = getDouble(params, "minutesPerStop", 3);
        int minMinutes = (int) getDouble(params, "minMinutes", 2);
        int duration = Math.max(minMinutes, (int) ((path.size() - 1) * minutesPerStop));
        plan.setDurationMinutes(duration);

        plan.setPrice(calculatePrice(path.size() - 1));

        return plan;
    }

    private Long determineLine(List<Long> path, int index, Map<Long, MetroStation> stationMap, Long currentLineId) {
        MetroStation station = stationMap.get(path.get(index));
        if (station == null) return currentLineId;

        List<Long> stLineIds = parseIdList(station.getLineIds());
        if (stLineIds.isEmpty()) return currentLineId;

        // 如果当前线路仍在可用线路上，继续使用
        if (currentLineId != null && stLineIds.contains(currentLineId)) {
            return currentLineId;
        }

        // 如果有下一个站点，找共同线路
        if (index < path.size() - 1) {
            MetroStation next = stationMap.get(path.get(index + 1));
            if (next != null) {
                List<Long> nextLineIds = parseIdList(next.getLineIds());
                for (Long lid : stLineIds) {
                    if (nextLineIds.contains(lid)) return lid;
                }
            }
        }

        return stLineIds.get(0);
    }

    private List<Long> bfsShortestPath(Long startId, Long endId, Map<Long, List<Long>> graph) {
        if (!graph.containsKey(endId)) return null;

        Queue<Long> queue = new LinkedList<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        queue.offer(startId);
        visited.add(startId);
        parent.put(startId, null);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(endId)) {
                List<Long> path = new ArrayList<>();
                Long node = endId;
                while (node != null) {
                    path.add(node);
                    node = parent.get(node);
                }
                Collections.reverse(path);
                return path;
            }
            for (Long neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.offer(neighbor);
                }
            }
        }
        return null;
    }

    private double calculateDistance(List<Long> path, Map<Long, MetroStation> stationMap) {
        Map<String, Object> params = configService.getConfigObject(
                "ticket.estimate_params", new TypeReference<Map<String, Object>>() {});
        double kmPerStop = getDouble(params, "kmPerStop", 1.8);

        double totalKm = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            MetroStation cur = stationMap.get(path.get(i));
            MetroStation next = stationMap.get(path.get(i + 1));
            if (cur == null || next == null) continue;

            Double dist = getNeighborDistance(cur, next.getId());
            if (dist == null) dist = getNeighborDistance(next, cur.getId());
            totalKm += (dist != null && dist > 0) ? dist : kmPerStop;
        }
        return totalKm;
    }

    private Double getNeighborDistance(MetroStation station, Long neighborId) {
        String neighborIdStr = String.valueOf(neighborId);
        List<String> nextIds = parseJsonArray(station.getNextStationIds());
        List<String> prevIds = parseJsonArray(station.getPrevStationIds());
        List<String> nextDists = parseJsonArray(station.getNextStationDistances());
        List<String> prevDists = parseJsonArray(station.getPrevStationDistances());

        int idx = nextIds.indexOf(neighborIdStr);
        if (idx >= 0 && idx < nextDists.size()) {
            return parseDouble(nextDists.get(idx));
        }
        idx = prevIds.indexOf(neighborIdStr);
        if (idx >= 0 && idx < prevDists.size()) {
            return parseDouble(prevDists.get(idx));
        }
        return null;
    }

    private int calculatePrice(int stationCount) {
        List<Map<String, Object>> tiers = configService.getConfigObject("ticket.price_tiers", TIER_LIST);
        if (tiers == null || tiers.isEmpty()) return 2;
        for (Map<String, Object> tier : tiers) {
            int maxStops = ((Number) tier.get("maxStops")).intValue();
            if (stationCount <= maxStops) {
                return ((Number) tier.get("price")).intValue();
            }
        }
        return ((Number) tiers.get(tiers.size() - 1).get("price")).intValue();
    }

    private List<Long> parseIdList(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            List<String> strs = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            List<Long> ids = new ArrayList<>();
            for (String s : strs) {
                try { ids.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try { return new ArrayList<>(MAPPER.readValue(json, new TypeReference<List<String>>() {})); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        if (map == null) return defaultVal;
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return defaultVal;
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
}
