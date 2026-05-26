package com.mu.transitmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 附近地铁站服务
 *
 * 灵感来自 geospatial-mcp-server 的 search_nearby 思路：
 *   - 给定经纬度 → 返回半径内的若干个地铁站候选
 *   - 优先用本地 metro_station 表
 *   - 若本地为空且 osm_enabled=1 → 调 OSM Overpass API 兜底（注意：OSM 数据没有本地的 station_id，
 *     无法直接参与 BFS 路径规划，但可以给用户提示「OSM 看到附近有 XX 站，但本系统还未录入」）
 */
@Service
public class NearbyStationService {

    private static final Logger log = LoggerFactory.getLogger(NearbyStationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PathPlanningService pathPlanningService;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired(required = false)
    private RestTemplate agentRestTemplate;

    /**
     * 本地候选：从 metro_station 表找 TopN
     */
    public List<PathPlanningService.NearbyStation> findLocalCandidates(
            double lat, double lng, Long cityId) {
        if (cityId == null) return new ArrayList<>();
        int topN = configService.getConfigInt("agent.nearby.max_candidates", 3);
        double maxKm = parseDouble(configService.getConfigValue("agent.nearby.max_radius_km"), 5.0);
        return pathPlanningService.findNearbyStations(lat, lng, cityId, maxKm, topN);
    }

    /**
     * OSM 兜底：当本地候选为空 / 距离都过远时，从 OpenStreetMap 查附近的 subway/station POI
     * 仅作信息提示用，无法直接拿来规划路径（因为没有本地 station_id 关联）
     */
    public List<OsmStation> findOsmCandidates(double lat, double lng) {
        if (configService.getConfigInt("agent.nearby.osm_enabled", 0) != 1) {
            return new ArrayList<>();
        }
        int radiusM = configService.getConfigInt("agent.nearby.osm_radius_m", 1500);
        String endpoint = configService.getConfigValue("agent.nearby.osm_endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://overpass-api.de/api/interpreter";
        }

        // Overpass QL：查附近 subway / metro station
        // node["railway"="station"]["station"="subway"]
        // node["public_transport"="station"]["subway"="yes"]
        // node["railway"="subway_entrance"]
        String ql = String.format(
                "[out:json][timeout:10];" +
                "(" +
                "node(around:%d,%f,%f)[\"railway\"=\"station\"][\"station\"=\"subway\"];" +
                "node(around:%d,%f,%f)[\"public_transport\"=\"station\"][\"subway\"=\"yes\"];" +
                "node(around:%d,%f,%f)[\"railway\"=\"subway_entrance\"];" +
                ");out body 20;",
                radiusM, lat, lng, radiusM, lat, lng, radiusM, lat, lng);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "TransitMap-Agent/1.0");
            HttpEntity<String> entity = new HttpEntity<>("data=" + java.net.URLEncoder.encode(ql, StandardCharsets.UTF_8), headers);
            RestTemplate rt = (agentRestTemplate != null ? agentRestTemplate : new RestTemplate());
            ResponseEntity<JsonNode> resp = rt.exchange(
                    URI.create(endpoint), HttpMethod.POST, entity, JsonNode.class);
            JsonNode elements = resp.getBody() == null ? null : resp.getBody().path("elements");
            if (elements == null || !elements.isArray()) return new ArrayList<>();

            List<OsmStation> out = new ArrayList<>();
            for (JsonNode el : elements) {
                double sLat = el.path("lat").asDouble(0);
                double sLng = el.path("lon").asDouble(0);
                if (sLat == 0 || sLng == 0) continue;
                JsonNode tags = el.path("tags");
                String name = tags.path("name").asText("");
                if (name.isBlank()) name = tags.path("name:zh").asText("");
                if (name.isBlank()) name = tags.path("ref").asText("");
                if (name.isBlank()) continue;
                String type = tags.path("railway").asText("");
                if (type.isBlank()) type = tags.path("public_transport").asText("");
                double dist = haversine(lat, lng, sLat, sLng);
                out.add(new OsmStation(name, sLat, sLng, type, dist));
            }
            out.sort((a, b) -> Double.compare(a.distanceKm, b.distanceKm));
            return out;
        } catch (Exception e) {
            log.warn("OSM Overpass 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public static class OsmStation {
        public final String name;
        public final double lat;
        public final double lng;
        public final String type;
        public final double distanceKm;

        public OsmStation(String name, double lat, double lng, String type, double distanceKm) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.type = type;
            this.distanceKm = distanceKm;
        }
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null) return fallback;
        try { return Double.parseDouble(s); } catch (Exception e) { return fallback; }
    }
}
