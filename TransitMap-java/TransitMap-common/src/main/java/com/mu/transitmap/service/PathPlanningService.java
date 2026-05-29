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
 * 路径规划服务：Dijkstra 最短路径 + BFS 备选 + 多方案返回
 */
@Service
public class PathPlanningService {

    private static final Logger log = LoggerFactory.getLogger(PathPlanningService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TIER_LIST = new TypeReference<>() {};

    /** 换乘惩罚距离（约 3 分钟，等效 1.5km） */
    private static final double TRANSFER_PENALTY_KM = 1.5;

    @Autowired
    private IMetroStationService stationService;

    @Autowired
    private IMetroLineService lineService;

    @Autowired
    private SystemConfigServiceImpl configService;

    /**
     * 规划从 startStation 到 endStation 的地铁路线（Dijkstra 加权最短路径）
     */
    public RoutePlanVO planRoute(Long startStationId, Long endStationId, Long cityId) {
        // 1. 获取城市所有运营站点
        List<MetroStation> stations = stationService.getStationsByCityId(cityId);
        if (stations == null || stations.isEmpty()) return null;

        Map<Long, MetroStation> stationMap = new HashMap<>();
        for (MetroStation s : stations) {
            stationMap.put(s.getId(), s);
        }

        // 2. 构建加权图
        Map<Long, List<Edge>> weightedGraph = buildWeightedGraph(stations);

        // 3. Dijkstra 最短路径（综合距离 + 换乘惩罚）
        List<Long> path = dijkstraShortestPath(startStationId, endStationId, weightedGraph, stationMap);
        if (path == null || path.size() < 2) {
            // 降级到 BFS
            log.warn("Dijkstra 未找到路径，降级到 BFS");
            Map<Long, List<Long>> simpleGraph = buildSimpleGraph(stations);
            path = bfsShortestPath(startStationId, endStationId, simpleGraph);
        }
        if (path == null || path.size() < 2) return null;

        // 4. 构建路线详情
        return buildRoutePlan(path, stationMap);
    }

    /**
     * 返回多种路线方案
     *
     * @return {"fastest": RoutePlanVO, "fewest_transfers": RoutePlanVO, "shortest_distance": RoutePlanVO}
     */
    public Map<String, RoutePlanVO> planMultipleRoutes(Long startStationId, Long endStationId, Long cityId) {
        Map<String, RoutePlanVO> results = new LinkedHashMap<>();

        List<MetroStation> stations = stationService.getStationsByCityId(cityId);
        if (stations == null || stations.isEmpty()) return results;

        Map<Long, MetroStation> stationMap = new HashMap<>();
        for (MetroStation s : stations) {
            stationMap.put(s.getId(), s);
        }

        Map<Long, List<Edge>> weightedGraph = buildWeightedGraph(stations);

        // 方案 1: 最快路线（Dijkstra，权重 = 距离 + 换乘惩罚）
        List<Long> fastestPath = dijkstraShortestPath(startStationId, endStationId, weightedGraph, stationMap);
        if (fastestPath != null && fastestPath.size() >= 2) {
            RoutePlanVO plan = buildRoutePlan(fastestPath, stationMap);
            plan.setRouteType("fastest");
            plan.setRouteLabel("最快路线");
            results.put("fastest", plan);
        }

        // 方案 2: 最少换乘（权重 = 换乘惩罚为主，距离为辅）
        List<Long> fewestTransferPath = dijkstraWithWeight(startStationId, endStationId, weightedGraph, stationMap,
                0.1, 5.0); // 距离权重 0.1，换乘惩罚 5.0
        if (fewestTransferPath != null && fewestTransferPath.size() >= 2) {
            RoutePlanVO plan = buildRoutePlan(fewestTransferPath, stationMap);
            plan.setRouteType("fewest_transfers");
            plan.setRouteLabel("最少换乘");
            results.put("fewest_transfers", plan);
        }

        // 方案 3: 最短距离（权重 = 纯距离，无换乘惩罚）
        List<Long> shortestPath = dijkstraWithWeight(startStationId, endStationId, weightedGraph, stationMap,
                1.0, 0.0); // 距离权重 1.0，换乘惩罚 0.0
        if (shortestPath != null && shortestPath.size() >= 2) {
            RoutePlanVO plan = buildRoutePlan(shortestPath, stationMap);
            plan.setRouteType("shortest_distance");
            plan.setRouteLabel("最短距离");
            results.put("shortest_distance", plan);
        }

        // 如果某个方案和最快路线相同，标记为推荐
        if (results.containsKey("fastest")) {
            results.get("fastest").setRecommended(true);
        }

        return results;
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

    // ═══════════════════════════════════════════
    //  Dijkstra 加权最短路径
    // ═══════════════════════════════════════════

    /**
     * 加权边
     */
    private static class Edge {
        final Long to;
        final double distanceKm;
        final boolean isTransfer;

        Edge(Long to, double distanceKm, boolean isTransfer) {
            this.to = to;
            this.distanceKm = distanceKm;
            this.isTransfer = isTransfer;
        }
    }

    /**
     * 构建加权图（包含距离和换乘信息）
     */
    private Map<Long, List<Edge>> buildWeightedGraph(List<MetroStation> stations) {
        Map<Long, List<Edge>> graph = new HashMap<>();
        Map<Long, MetroStation> stationMap = new HashMap<>();
        for (MetroStation s : stations) {
            stationMap.put(s.getId(), s);
        }

        for (MetroStation s : stations) {
            List<Edge> edges = new ArrayList<>();
            List<Long> prevIds = parseIdList(s.getPrevStationIds());
            List<Long> nextIds = parseIdList(s.getNextStationIds());
            List<String> prevDists = parseJsonArray(s.getPrevStationDistances());
            List<String> nextDists = parseJsonArray(s.getNextStationDistances());

            // 前一站的边
            for (int i = 0; i < prevIds.size(); i++) {
                Long neighborId = prevIds.get(i);
                double dist = (i < prevDists.size()) ? parseDouble(prevDists.get(i)) : 1.8;
                if (dist <= 0) dist = 1.8;
                boolean isTransfer = isTransferEdge(s, stationMap.get(neighborId));
                edges.add(new Edge(neighborId, dist, isTransfer));
            }

            // 后一站的边
            for (int i = 0; i < nextIds.size(); i++) {
                Long neighborId = nextIds.get(i);
                double dist = (i < nextDists.size()) ? parseDouble(nextDists.get(i)) : 1.8;
                if (dist <= 0) dist = 1.8;
                boolean isTransfer = isTransferEdge(s, stationMap.get(neighborId));
                edges.add(new Edge(neighborId, dist, isTransfer));
            }

            graph.put(s.getId(), edges);
        }
        return graph;
    }

    /**
     * 判断两个站点之间是否是换乘（不同线路）
     */
    private boolean isTransferEdge(MetroStation from, MetroStation to) {
        if (from == null || to == null) return false;
        List<Long> fromLines = parseIdList(from.getLineIds());
        List<Long> toLines = parseIdList(to.getLineIds());
        // 没有共同线路 = 换乘
        for (Long lid : fromLines) {
            if (toLines.contains(lid)) return false;
        }
        return true;
    }

    /**
     * Dijkstra 最短路径（默认权重：距离 + 换乘惩罚）
     */
    private List<Long> dijkstraShortestPath(Long startId, Long endId,
                                             Map<Long, List<Edge>> graph,
                                             Map<Long, MetroStation> stationMap) {
        return dijkstraWithWeight(startId, endId, graph, stationMap, 1.0, TRANSFER_PENALTY_KM);
    }

    /**
     * Dijkstra 最短路径（自定义权重）
     *
     * @param distanceWeight   距离权重
     * @param transferPenaltyKm 换乘惩罚（等效公里数）
     */
    private List<Long> dijkstraWithWeight(Long startId, Long endId,
                                           Map<Long, List<Edge>> graph,
                                           Map<Long, MetroStation> stationMap,
                                           double distanceWeight,
                                           double transferPenaltyKm) {
        if (!graph.containsKey(endId)) return null;

        // 距离表
        Map<Long, Double> dist = new HashMap<>();
        // 父节点表
        Map<Long, Long> parent = new HashMap<>();
        // 已访问集合
        Set<Long> visited = new HashSet<>();
        // 优先队列（距离, 节点）
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist.getOrDefault(a[0], Double.MAX_VALUE)));

        dist.put(startId, 0.0);
        pq.offer(new long[]{startId});

        while (!pq.isEmpty()) {
            long[] current = pq.poll();
            Long u = current[0];

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(endId)) {
                // 找到终点，回溯路径
                return reconstructPath(endId, parent);
            }

            for (Edge edge : graph.getOrDefault(u, Collections.emptyList())) {
                if (visited.contains(edge.to)) continue;

                // 计算边权重
                double weight = edge.distanceKm * distanceWeight;
                if (edge.isTransfer) {
                    weight += transferPenaltyKm;
                }

                double newDist = dist.getOrDefault(u, Double.MAX_VALUE) + weight;
                if (newDist < dist.getOrDefault(edge.to, Double.MAX_VALUE)) {
                    dist.put(edge.to, newDist);
                    parent.put(edge.to, u);
                    pq.offer(new long[]{edge.to});
                }
            }
        }

        return null; // 无法到达
    }

    /**
     * 回溯路径
     */
    private List<Long> reconstructPath(Long endId, Map<Long, Long> parent) {
        List<Long> path = new ArrayList<>();
        Long node = endId;
        while (node != null) {
            path.add(node);
            node = parent.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * 构建简单图（BFS 用）
     */
    private Map<Long, List<Long>> buildSimpleGraph(List<MetroStation> stations) {
        Map<Long, List<Long>> graph = new HashMap<>();
        for (MetroStation s : stations) {
            List<Long> neighbors = new ArrayList<>();
            neighbors.addAll(parseIdList(s.getPrevStationIds()));
            neighbors.addAll(parseIdList(s.getNextStationIds()));
            graph.put(s.getId(), neighbors);
        }
        return graph;
    }

    // ═══════════════════════════════════════════
    //  BFS 备选方案
    // ═══════════════════════════════════════════

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
