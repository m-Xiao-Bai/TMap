package com.mu.transitmap.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GeoUtil {

    private static final Logger log = LoggerFactory.getLogger(GeoUtil.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Haversine 公式计算两点间距离（km）
     */
    public static BigDecimal haversineDistance(BigDecimal lat1, BigDecimal lng1,
                                               BigDecimal lat2, BigDecimal lng2) {
        double dLat = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double dLng = Math.toRadians(lng2.subtract(lng1).doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;
        return BigDecimal.valueOf(distance).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 调用高德地理编码 API
     *
     * @param apiKey  高德 API Key
     * @param address 地址/站名
     * @param city    城市（可选，提高精度）
     * @return Map 含 "longitude"(BigDecimal), "latitude"(BigDecimal), "formattedAddress"(String)
     *         如果编码失败返回 null
     */
    public static Map<String, Object> geocode(String apiKey, String address, String city) {
        return geocodeWithRetry(apiKey, address, city, 1); // 只重试1次，避免超时
    }

    /**
     * 地理编码 — 返回完整结构化地址信息
     *
     * @param apiKey  高德 API Key
     * @param address 地址
     * @param city    城市（可选）
     * @return Map 含 lng, lat, formattedAddress, province, city, district, street, streetNumber, level
     *         编码失败返回 null
     */
    public static Map<String, Object> geocodeDetailed(String apiKey, String address, String city) {
        try {
            String urlStr = "https://restapi.amap.com/v3/geocode/geo"
                    + "?key=" + apiKey
                    + "&address=" + java.net.URLEncoder.encode(address, "UTF-8")
                    + (city != null && !city.isEmpty() ? "&city=" + java.net.URLEncoder.encode(city, "UTF-8") : "")
                    + "&output=json";

            log.info("[高德地理编码-详细] 请求URL: {}", urlStr);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            conn.disconnect();

            JsonNode root = objectMapper.readTree(response.toString());
            if (!"1".equals(root.path("status").asText("0"))) {
                log.warn("[高德地理编码-详细] API错误: {}", root.path("info").asText(""));
                return null;
            }

            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) return null;

            JsonNode first = geocodes.get(0);
            String location = first.path("location").asText("");
            if (location.isEmpty() || "[]".equals(location)) return null;

            String[] parts = location.split(",");
            if (parts.length != 2) return null;

            JsonNode ac = first.path("addressComponent");

            Map<String, Object> result = new HashMap<>();
            result.put("lng", parts[0].trim());
            result.put("lat", parts[1].trim());
            result.put("formattedAddress", first.path("formatted_address").asText(""));
            result.put("province", ac.path("province").asText(""));
            result.put("city", ac.path("city").isTextual() ? ac.path("city").asText("") : "");
            result.put("district", ac.path("district").asText(""));
            result.put("street", ac.path("street").asText(""));
            result.put("streetNumber", ac.path("number").asText(""));
            result.put("level", first.path("level").asText(""));
            return result;
        } catch (Exception e) {
            log.error("[高德地理编码-详细] 异常: address={}, city={}", address, city, e);
            return null;
        }
    }

    /**
     * 带重试机制的地理编码
     */
    private static Map<String, Object> geocodeWithRetry(String apiKey, String address, String city, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String urlStr = "https://restapi.amap.com/v3/geocode/geo"
                        + "?key=" + apiKey
                        + "&address=" + java.net.URLEncoder.encode(address, "UTF-8")
                        + (city != null && !city.isEmpty() ? "&city=" + java.net.URLEncoder.encode(city, "UTF-8") : "")
                        + "&output=json";

                log.info("[高德地理编码] 请求URL (第{}次): {}", attempt, urlStr);

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                log.info("[高德地理编码] HTTP响应码: {}", responseCode);

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                conn.disconnect();

                log.info("[高德地理编码] 响应体: {}", response.toString());

                JsonNode root = objectMapper.readTree(response.toString());

                String status = root.path("status").asText("0");
                String info = root.path("info").asText("");

                // 成功
                if ("1".equals(status)) {
                    JsonNode geocodes = root.path("geocodes");
                    if (!geocodes.isArray() || geocodes.isEmpty()) {
                        log.warn("[高德地理编码] 未找到地理编码结果");
                        return null;
                    }

                    JsonNode first = geocodes.get(0);
                    String location = first.path("location").asText("");
                    if (location.isEmpty() || "[]".equals(location)) {
                        log.warn("[高德地理编码] location为空或无效: {}", location);
                        return null;
                    }

                    // 高德返回格式: "经度,纬度"
                    String[] parts = location.split(",");
                    if (parts.length != 2) {
                        log.warn("[高德地理编码] location格式错误: {}", location);
                        return null;
                    }

                    BigDecimal longitude = new BigDecimal(parts[0].trim());
                    BigDecimal latitude = new BigDecimal(parts[1].trim());
                    String formattedAddress = first.path("formatted_address").asText("");

                    log.info("[高德地理编码] 成功: lng={}, lat={}, address={}", longitude, latitude, formattedAddress);

                    Map<String, Object> result = new HashMap<>();
                    result.put("longitude", longitude);
                    result.put("latitude", latitude);
                    result.put("formattedAddress", formattedAddress);
                    return result;
                }

                // 临时错误，可以重试
                log.warn("[高德地理编码] API返回错误 (第{}次): status={}, info={}", attempt, status, info);
                if (attempt < maxRetries) {
                    long sleepMs = attempt * 500L; // 递增延迟
                    log.info("[高德地理编码] {}ms后重试...", sleepMs);
                    Thread.sleep(sleepMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[高德地理编码] 被中断");
                return null;
            } catch (Exception e) {
                log.error("[高德地理编码] 异常 (第{}次): address={}, city={}", attempt, address, city, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(attempt * 500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        log.error("[高德地理编码] 所有重试都失败: address={}, city={}", address, city);
        return null;
    }
}
