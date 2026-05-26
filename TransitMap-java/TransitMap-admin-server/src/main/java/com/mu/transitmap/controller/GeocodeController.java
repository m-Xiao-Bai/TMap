package com.mu.transitmap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.MetroStationServiceImpl;
import com.mu.transitmap.util.GeoUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/manage/geocode")
public class GeocodeController {

    private static final Logger log = LoggerFactory.getLogger(GeocodeController.class);

    @Autowired
    private MetroStationServiceImpl metroStationService;

    @Value("${gaode.api-key}")
    private String gaodeApiKey;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 单个站点地理编码
     */
    @PostMapping("/single/{stationId}")
    public Result<Map<String, Object>> geocodeSingle(@PathVariable Long stationId,
                                                      HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        MetroStation station = metroStationService.getById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        }

        Map<String, Object> result = geocodeStation(station);
        return Result.success(result);
    }

    /**
     * 批量地理编码
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> geocodeBatch(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long cityId = body.get("cityId") != null ? Long.valueOf(body.get("cityId").toString()) : null;
        @SuppressWarnings("unchecked")
        List<String> stationIdStrs = (List<String>) body.get("stationIds");
        Boolean skipRecent = body.get("skipRecent") != null ? (Boolean) body.get("skipRecent") : true;

        List<MetroStation> stations;
        if (stationIdStrs != null && !stationIdStrs.isEmpty()) {
            List<Long> ids = stationIdStrs.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
            stations = metroStationService.listByIds(ids);
        } else if (cityId != null) {
            stations = metroStationService.lambdaQuery()
                    .eq(MetroStation::getCityId, cityId)
                    .eq(MetroStation::getStatusCode, 1)
                    .list();
        } else {
            stations = metroStationService.lambdaQuery()
                    .eq(MetroStation::getStatusCode, 1)
                    .list();
        }

        int total = stations.size();
        int encoded = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        log.info("[批量编码] 开始: total={}, cityId={}, skipRecent={}", total, cityId, skipRecent);

        for (MetroStation station : stations) {
            // 跳过7天内已编码的
            if (skipRecent && station.getGeocodeTime() != null) {
                LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
                if (station.getGeocodeTime().isAfter(weekAgo)) {
                    skipped++;
                    continue;
                }
            }

            Map<String, Object> result = geocodeStation(station);
            String status = (String) result.get("status");
            if ("success".equals(status)) {
                encoded++;
                details.add(result);
            } else {
                failed++;
                Map<String, Object> err = new HashMap<>();
                err.put("stationId", station.getId());
                err.put("stationName", station.getStationName());
                err.put("reason", result.get("message"));
                errors.add(err);
            }

            // 限速：200ms间隔
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[批量编码] 完成: encoded={}, skipped={}, failed={}", encoded, skipped, failed);

        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("encoded", encoded);
        response.put("skipped", skipped);
        response.put("failed", failed);
        response.put("errors", errors);
        response.put("details", details);
        return Result.success(response);
    }

    /**
     * 查询编码状态/统计
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus(@RequestParam(required = false) Long cityId) {
        List<MetroStation> stations;
        if (cityId != null) {
            stations = metroStationService.lambdaQuery()
                    .eq(MetroStation::getCityId, cityId)
                    .eq(MetroStation::getStatusCode, 1)
                    .list();
        } else {
            stations = metroStationService.lambdaQuery()
                    .eq(MetroStation::getStatusCode, 1)
                    .list();
        }

        int total = stations.size();
        int geocoded = 0;
        int needsUpdate = 0;
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        for (MetroStation s : stations) {
            if (s.getGeocodeTime() != null) {
                geocoded++;
                if (s.getGeocodeTime().isBefore(weekAgo)) {
                    needsUpdate++;
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("geocoded", geocoded);
        response.put("notGeocoded", total - geocoded);
        response.put("needsUpdate", needsUpdate);
        return Result.success(response);
    }

    /**
     * 对单个站点执行地理编码并更新数据库
     */
    private Map<String, Object> geocodeStation(MetroStation station) {
        Map<String, Object> result = new HashMap<>();

        // 记录原始坐标
        BigDecimal oldLng = station.getLongitude();
        BigDecimal oldLat = station.getLatitude();

        String stationName = station.getStationName();
        String city = station.getCityName();

        // 构建搜索地址：去掉"站"后缀，加上"地铁站"
        String baseName = stationName;
        if (baseName.endsWith("站")) {
            baseName = baseName.substring(0, baseName.length() - 1);
        }
        String address = baseName + "地铁站";

        // 城市名：去掉"市"后缀（有些城市名可能不匹配）
        String cityShort = city;
        if (cityShort != null && cityShort.endsWith("市")) {
            cityShort = cityShort.substring(0, cityShort.length() - 1);
        }

        log.info("[地理编码] 开始编码: stationId={}, stationName={}, city={}, address={}",
                station.getId(), stationName, city, address);

        // 尝试1：站名 + "地铁站" + 城市
        Map<String, Object> geoResult = GeoUtil.geocode(gaodeApiKey, address, city);

        // 尝试2：原始站名 + 城市
        if (geoResult == null) {
            log.info("[地理编码] 第一次尝试失败，尝试原始站名: {}", stationName);
            geoResult = GeoUtil.geocode(gaodeApiKey, stationName, city);
        }

        // 尝试3：不限制城市
        if (geoResult == null) {
            log.info("[地理编码] 第二次尝试失败，尝试不限制城市");
            geoResult = GeoUtil.geocode(gaodeApiKey, address, null);
        }

        if (geoResult == null) {
            log.warn("[地理编码] 高德API返回失败: stationId={}, stationName={}", station.getId(), stationName);
            result.put("status", "failed");
            result.put("stationId", station.getId());
            result.put("stationName", stationName);
            result.put("message", "高德地理编码失败，未找到匹配位置");
            return result;
        }

        BigDecimal newLng = (BigDecimal) geoResult.get("longitude");
        BigDecimal newLat = (BigDecimal) geoResult.get("latitude");
        String formattedAddress = (String) geoResult.get("formattedAddress");

        log.info("[地理编码] 高德返回: newLng={}, newLat={}, address={}", newLng, newLat, formattedAddress);

        // 更新站点坐标和编码时间
        station.setLongitude(newLng);
        station.setLatitude(newLat);
        station.setGeocodeTime(LocalDateTime.now());
        station.setUpdatedAt(LocalDateTime.now());

        try {
            boolean updateResult = metroStationService.updateById(station);
            log.info("[地理编码] 数据库更新结果: stationId={}, updateResult={}", station.getId(), updateResult);

            if (!updateResult) {
                result.put("status", "failed");
                result.put("message", "数据库更新失败");
                return result;
            }
        } catch (Exception e) {
            log.error("[地理编码] 数据库更新异常: stationId={}", station.getId(), e);
            result.put("status", "failed");
            result.put("message", "数据库更新异常: " + e.getMessage());
            return result;
        }

        // 重算相邻站距离
        try {
            recalculateDistances(station);
        } catch (Exception e) {
            log.warn("[地理编码] 距离重算失败，不影响编码结果: stationId={}", station.getId(), e);
        }

        result.put("status", "success");
        result.put("stationId", station.getId());
        result.put("stationName", station.getStationName());
        result.put("oldLongitude", oldLng);
        result.put("oldLatitude", oldLat);
        result.put("newLongitude", newLng);
        result.put("newLatitude", newLat);
        result.put("formattedAddress", formattedAddress);
        return result;
    }

    /**
     * 重算站点与相邻站的距离
     */
    private void recalculateDistances(MetroStation station) {
        // 重算 prev 方向
        recalcDirection(station, "prev");
        // 重算 next 方向
        recalcDirection(station, "next");
    }

    private void recalcDirection(MetroStation station, String direction) {
        String idsJson = "prev".equals(direction) ? station.getPrevStationIds() : station.getNextStationIds();
        if (idsJson == null || idsJson.isEmpty() || "[]".equals(idsJson.trim())) {
            return;
        }

        List<Long> neighborIds;
        try {
            neighborIds = objectMapper.readValue(idsJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return;
        }

        if (neighborIds.isEmpty()) {
            return;
        }

        List<MetroStation> neighbors = metroStationService.listByIds(neighborIds);
        Map<Long, MetroStation> neighborMap = neighbors.stream()
                .collect(Collectors.toMap(MetroStation::getId, s -> s));

        List<String> distances = new ArrayList<>();
        for (Long nid : neighborIds) {
            MetroStation neighbor = neighborMap.get(nid);
            if (neighbor != null
                    && station.getLongitude() != null && station.getLatitude() != null
                    && neighbor.getLongitude() != null && neighbor.getLatitude() != null) {
                BigDecimal dist = GeoUtil.haversineDistance(
                        station.getLatitude(), station.getLongitude(),
                        neighbor.getLatitude(), neighbor.getLongitude());
                distances.add(dist.toPlainString());
            } else {
                distances.add("0");
            }
        }

        String distancesJson;
        try {
            distancesJson = objectMapper.writeValueAsString(distances);
        } catch (Exception e) {
            return;
        }

        if ("prev".equals(direction)) {
            station.setPrevStationDistances(distancesJson);
        } else {
            station.setNextStationDistances(distancesJson);
        }
        metroStationService.updateById(station);
    }
}
