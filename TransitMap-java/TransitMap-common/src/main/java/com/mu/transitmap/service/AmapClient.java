package com.mu.transitmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.LocationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图 API 客户端
 *
 * 注意：所有 RestTemplate 调用必须传 URI 对象（不能传 String）！
 * RestTemplate.getForObject(String, ...) 会把 String 当作 URI 模板处理，
 * 对已编码的 % 字符再次编码（%E5 → %25E5），导致高德返回 30001 ENGINE_RESPONSE_DATA_ERROR。
 */
@Component
public class AmapClient {

    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String AMAP_BASE = "https://restapi.amap.com/v3";

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private RestTemplate agentRestTemplate;

    /**
     * 地理编码：地址 → 经纬度
     */
    public LocationVO geocode(String address, String city) {
        try {
            String key = getApiKey();
            String encodedAddr = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = AMAP_BASE + "/geocode/geo?key=" + key + "&address=" + encodedAddr;
            if (city != null && !city.isEmpty()) {
                url += "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
            }

            // 脱敏后打印完整 URL（便于排查"key 末尾带空格/换行"等隐形字符问题）
            String maskedUrl = url.replace(key,
                    key.length() < 8 ? "****" : key.substring(0, 3) + "****" + key.substring(key.length() - 4));
            log.info("Amap geocode URL = [{}], keyLen={}", maskedUrl, key.length());

            // 关键：用 URI 对象绕过 RestTemplate 的 URI 模板编码
            String resp = agentRestTemplate.getForObject(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(resp);

            String status = root.path("status").asText("0");
            if (!"1".equals(status)) {
                String info = root.path("info").asText("");
                String infocode = root.path("infocode").asText("");
                log.warn("Amap geocode failed: status={}, info={}, infocode={}, rawResp={}",
                        status, info, infocode, resp);
                throw new AmapException(status, info, infocode, resp);
            }

            JsonNode geocodes = root.path("geocodes");
            if (geocodes.isArray() && geocodes.size() > 0) {
                JsonNode first = geocodes.get(0);
                LocationVO vo = new LocationVO();
                String[] lnglat = first.path("location").asText("0,0").split(",");
                vo.setLng(Double.parseDouble(lnglat[0]));
                vo.setLat(Double.parseDouble(lnglat[1]));
                vo.setCity(first.path("city").asText(""));
                vo.setAddress(first.path("formatted_address").asText(""));
                vo.setFormattedAddress(first.path("formatted_address").asText(""));
                return vo;
            }
            return null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Geocode failed for address={}", address, e);
            throw new RuntimeException("Geocode 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 高德返回的业务错误
     */
    public static class AmapException extends RuntimeException {
        public final String status;
        public final String info;
        public final String infocode;
        public final String rawResponse;

        public AmapException(String status, String info, String infocode, String rawResponse) {
            super("高德 API 返回错误: status=" + status + ", info=" + info + ", infocode=" + infocode);
            this.status = status;
            this.info = info;
            this.infocode = infocode;
            this.rawResponse = rawResponse;
        }
    }

    /**
     * 逆地理编码：经纬度 → 地址
     */
    public LocationVO regeo(double lng, double lat) {
        try {
            String key = getApiKey();
            String url = AMAP_BASE + "/geocode/regeo?key=" + key
                    + "&location=" + lng + "," + lat + "&extensions=base";

            String resp = agentRestTemplate.getForObject(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(resp);

            String status = root.path("status").asText("0");
            if (!"1".equals(status)) {
                String info = root.path("info").asText("");
                String infocode = root.path("infocode").asText("");
                log.warn("Amap regeo failed: status={}, info={}, infocode={}", status, info, infocode);
                throw new AmapException(status, info, infocode, resp);
            }

            JsonNode regeocode = root.path("regeocode");
            if (regeocode.isObject()) {
                LocationVO vo = new LocationVO();
                vo.setLat(lat);
                vo.setLng(lng);
                vo.setCity(regeocode.path("addressComponent").path("city").asText(""));
                vo.setAddress(regeocode.path("formatted_address").asText(""));
                vo.setFormattedAddress(regeocode.path("formatted_address").asText(""));
                return vo;
            }
            return null;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Regeo failed for lng={}, lat={}", lng, lat, e);
            throw new RuntimeException("Regeo 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * IP 定位（ip 为空时高德会用请求方公网 IP）
     */
    public LocationVO ipLocate(String ip) {
        try {
            String key = getApiKey();
            String url = AMAP_BASE + "/ip?key=" + key;
            if (ip != null && !ip.isEmpty()) {
                url += "&ip=" + ip;
            }

            String resp = agentRestTemplate.getForObject(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(resp);

            String status = root.path("status").asText("0");
            if (!"1".equals(status)) {
                String info = root.path("info").asText("");
                String infocode = root.path("infocode").asText("");
                throw new AmapException(status, info, infocode, resp);
            }

            LocationVO vo = new LocationVO();
            vo.setCity(root.path("city").asText(""));
            String rect = root.path("rectangle").asText("");
            if (!rect.isEmpty() && rect.contains(";")) {
                String[] lnglat = rect.split(";")[0].split(",");
                if (lnglat.length == 2) {
                    vo.setLng(Double.parseDouble(lnglat[0]));
                    vo.setLat(Double.parseDouble(lnglat[1]));
                }
            }
            return vo;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("IP locate failed for ip={}", ip, e);
            throw new RuntimeException("IP 定位调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * POI 搜索
     */
    public List<LocationVO> placeSearch(String keyword, String city) {
        List<LocationVO> results = new ArrayList<>();
        try {
            String key = getApiKey();
            String encodedKw = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = AMAP_BASE + "/place/text?key=" + key + "&keywords=" + encodedKw;
            if (city != null && !city.isEmpty()) {
                url += "&city=" + URLEncoder.encode(city, StandardCharsets.UTF_8);
            }

            String resp = agentRestTemplate.getForObject(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode pois = root.path("pois");
            if (pois.isArray()) {
                for (JsonNode poi : pois) {
                    LocationVO vo = new LocationVO();
                    String[] lnglat = poi.path("location").asText("0,0").split(",");
                    vo.setLng(Double.parseDouble(lnglat[0]));
                    vo.setLat(Double.parseDouble(lnglat[1]));
                    vo.setCity(poi.path("cityname").asText(""));
                    vo.setAddress(poi.path("address").asText(""));
                    vo.setFormattedAddress(poi.path("name").asText(""));
                    results.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("Place search failed for keyword={}", keyword, e);
        }
        return results;
    }

    private String getApiKey() {
        String key;
        try {
            key = configService.getRaw("agent.map.api_key");
        } catch (Exception e) {
            throw new RuntimeException("高德 API Key 解密失败：" + e.getMessage()
                    + "。请在管理后台「Agent 配置 → 地图」重新保存一次 api_key（密钥已变更）", e);
        }
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("高德地图 API Key 未配置，请到管理后台「Agent 配置 → 地图」填入");
        }
        // 防御性检查：如果 key 仍带 enc: 前缀，说明解密链路异常
        if (key.startsWith("enc:")) {
            throw new RuntimeException("高德 API Key 仍是密文（解密链路异常），请重新保存一次 api_key");
        }
        return key;
    }
}
