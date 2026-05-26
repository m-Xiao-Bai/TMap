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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/manage/geocode")
public class GeocodeQueryController {

    @Value("${gaode.api-key}")
    private String gaodeApiKey;

    @Autowired
    private MetroStationServiceImpl metroStationService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/query")
    public Result<Map<String, Object>> query(@RequestBody Map<String, String> body,
                                              HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String address = body.get("address");
        if (address == null || address.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }

        String city = body.get("city");
        Map<String, Object> result = GeoUtil.geocodeDetailed(gaodeApiKey, address.trim(),
                (city != null && !city.trim().isEmpty()) ? city.trim() : null);

        if (result == null) {
            return Result.fail(404, "未找到匹配的地理编码结果");
        }
        return Result.success(result);
    }

    @PostMapping("/replace-coordinates")
    public Result<Map<String, Object>> replaceCoordinates(@RequestBody Map<String, String> body,
                                                           HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String stationIdStr = body.get("stationId");
        String lngStr = body.get("lng");
        String latStr = body.get("lat");
        if (stationIdStr == null || lngStr == null || latStr == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }

        Long stationId = Long.valueOf(stationIdStr);
        MetroStation station = metroStationService.getById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        }

        BigDecimal newLng = new BigDecimal(lngStr);
        BigDecimal newLat = new BigDecimal(latStr);
        BigDecimal oldLng = station.getLongitude();
        BigDecimal oldLat = station.getLatitude();

        station.setLongitude(newLng);
        station.setLatitude(newLat);
        station.setGeocodeTime(LocalDateTime.now());
        station.setUpdatedAt(LocalDateTime.now());
        metroStationService.updateById(station);

        // recalculate distances to neighbors
        recalcDirection(station, "prev");
        recalcDirection(station, "next");

        Map<String, Object> resp = new HashMap<>();
        resp.put("stationId", stationId);
        resp.put("stationName", station.getStationName());
        resp.put("oldLng", oldLng != null ? oldLng.toPlainString() : null);
        resp.put("oldLat", oldLat != null ? oldLat.toPlainString() : null);
        resp.put("newLng", newLng.toPlainString());
        resp.put("newLat", newLat.toPlainString());
        return Result.success(resp);
    }

    private void recalcDirection(MetroStation station, String direction) {
        String idsJson = "prev".equals(direction) ? station.getPrevStationIds() : station.getNextStationIds();
        if (idsJson == null || idsJson.isEmpty() || "[]".equals(idsJson.trim())) return;

        List<Long> neighborIds;
        try {
            neighborIds = objectMapper.readValue(idsJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return;
        }
        if (neighborIds.isEmpty()) return;

        List<MetroStation> neighbors = metroStationService.listByIds(neighborIds);
        Map<Long, MetroStation> neighborMap = new HashMap<>();
        for (MetroStation s : neighbors) neighborMap.put(s.getId(), s);

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

        try {
            String distancesJson = objectMapper.writeValueAsString(distances);
            if ("prev".equals(direction)) {
                station.setPrevStationDistances(distancesJson);
            } else {
                station.setNextStationDistances(distancesJson);
            }
            metroStationService.updateById(station);
        } catch (Exception ignored) {
        }
    }
}
