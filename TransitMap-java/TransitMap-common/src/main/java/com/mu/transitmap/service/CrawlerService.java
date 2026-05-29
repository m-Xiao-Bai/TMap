package com.mu.transitmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 爬虫服务客户端
 *
 * 调用 Python 爬虫服务的 API，触发爬取、查询任务状态、取消任务。
 */
@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SystemConfigServiceImpl configService;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getBaseUrl() {
        String val = configService.getConfigValue("crawler.service.url");
        return (val != null && !val.isEmpty()) ? val : "http://localhost:8000";
    }

    private String getApiKey() {
        String val = configService.getConfigValue("crawler.api_key");
        return (val != null) ? val : "";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", getApiKey());
        return headers;
    }

    /**
     * 触发单城市爬取
     *
     * @return 任务 ID
     */
    public String triggerCrawl(String cityName, Long countryId, String sources) {
        String url = getBaseUrl() + "/api/crawler/trigger";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("city_name", cityName);
        body.put("country_id", countryId);
        body.put("sources", sources);

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, buildHeaders());
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            JsonNode json = mapper.readTree(resp.getBody());

            if (json.has("data") && json.get("data").has("task_id")) {
                String taskId = json.get("data").get("task_id").asText();
                log.info("爬取任务已触发: {} -> {}", cityName, taskId);
                return taskId;
            }
            throw new RuntimeException("响应格式异常");
        } catch (Exception e) {
            log.error("触发爬取失败: {}", e.getMessage());
            throw new RuntimeException("触发爬取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量爬取
     *
     * @return 任务 ID 列表
     */
    public List<String> triggerBatchCrawl(List<Map<String, Object>> cities) {
        String url = getBaseUrl() + "/api/crawler/batch";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cities", cities);

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, buildHeaders());
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            JsonNode json = mapper.readTree(resp.getBody());

            List<String> taskIds = new ArrayList<>();
            if (json.has("data") && json.get("data").has("task_ids")) {
                for (JsonNode node : json.get("data").get("task_ids")) {
                    taskIds.add(node.asText());
                }
            }
            return taskIds;
        } catch (Exception e) {
            log.error("批量爬取失败: {}", e.getMessage());
            throw new RuntimeException("批量爬取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询任务状态
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        String url = getBaseUrl() + "/api/crawler/task/" + taskId;
        try {
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode json = mapper.readTree(resp.getBody());
            if (json.has("data")) {
                return mapper.convertValue(json.get("data"), Map.class);
            }
            return null;
        } catch (Exception e) {
            log.error("查询任务状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有任务列表
     */
    public List<Map<String, Object>> getAllTasks() {
        String url = getBaseUrl() + "/api/crawler/tasks";
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode json = mapper.readTree(resp.getBody());
            if (json.has("data")) {
                return mapper.convertValue(json.get("data"), List.class);
            }
            return List.of();
        } catch (Exception e) {
            log.error("获取任务列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        String url = getBaseUrl() + "/api/crawler/task/" + taskId;
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
            return true;
        } catch (Exception e) {
            log.error("取消任务失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取待审核列表
     */
    public Map<String, Object> getPendingReviews(String city, int page, int size) {
        String url = getBaseUrl() + "/api/review/pending";
        try {
            HttpHeaders headers = buildHeaders();
            String params = String.format("?page=%d&size=%d", page, size);
            if (city != null && !city.isEmpty()) {
                params += "&city=" + city;
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url + params, HttpMethod.GET, request, String.class);
            JsonNode json = mapper.readTree(resp.getBody());
            if (json.has("data")) {
                return mapper.convertValue(json.get("data"), Map.class);
            }
            return Map.of("items", List.of(), "total", 0);
        } catch (Exception e) {
            log.error("获取待审核列表失败: {}", e.getMessage());
            return Map.of("items", List.of(), "total", 0);
        }
    }

    /**
     * 批准审核
     */
    public boolean approveReview(int reviewId) {
        String url = getBaseUrl() + "/api/review/approve/" + reviewId;
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            restTemplate.postForEntity(url, request, String.class);
            return true;
        } catch (Exception e) {
            log.error("批准审核失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 拒绝审核
     */
    public boolean rejectReview(int reviewId) {
        String url = getBaseUrl() + "/api/review/reject/" + reviewId;
        try {
            HttpEntity<Void> request = new HttpEntity<>(buildHeaders());
            restTemplate.postForEntity(url, request, String.class);
            return true;
        } catch (Exception e) {
            log.error("拒绝审核失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Python 爬虫服务是否健康
     */
    public boolean isHealthy() {
        try {
            String url = getBaseUrl() + "/health";
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
