package com.mu.transitmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python Agent 健康检查调度器
 *
 * 每 30 秒检查一次 Python Agent 服务的健康状态。
 * 超过 90 秒未更新则视为不健康。
 */
@Component
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SystemConfigServiceImpl configService;

    private final RestTemplate restTemplate = new RestTemplate();

    /** Python 服务是否健康 */
    private volatile boolean pythonHealthy = false;

    /** 最后一次检查时间 */
    private volatile Instant lastCheckTime = Instant.MIN;

    /** 最后一次健康检查结果详情 */
    private volatile Map<String, Object> lastCheckDetails = new LinkedHashMap<>();

    /**
     * 每 30 秒执行一次健康检查
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void checkPythonHealth() {
        String pythonUrl = getConfigWithDefault("agent.python.url", "http://localhost:8000");
        String apiKey = getConfigWithDefault("agent.python.api_key", "");

        try {
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("X-API-Key", apiKey);
            }

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(
                    pythonUrl + "/health",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode json = mapper.readTree(resp.getBody());
                String status = json.path("status").asText("unknown");
                pythonHealthy = "ok".equals(status) || "degraded".equals(status);

                lastCheckDetails = new LinkedHashMap<>();
                lastCheckDetails.put("status", status);
                lastCheckDetails.put("healthy", pythonHealthy);
                if (json.has("checks")) {
                    lastCheckDetails.put("checks", mapper.convertValue(json.get("checks"), Map.class));
                }
                lastCheckDetails.put("lastCheck", Instant.now().toString());
            } else {
                pythonHealthy = false;
            }

            lastCheckTime = Instant.now();

            if (!pythonHealthy) {
                log.warn("Python Agent 健康检查: 状态异常 — {}", lastCheckDetails);
            }

        } catch (Exception e) {
            pythonHealthy = false;
            lastCheckTime = Instant.now();
            lastCheckDetails = Map.of(
                    "status", "unreachable",
                    "healthy", false,
                    "error", e.getMessage(),
                    "lastCheck", Instant.now().toString()
            );
            log.debug("Python Agent 健康检查失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 Python Agent 是否健康
     *
     * 超过 90 秒未更新则视为不健康（可能是调度器卡住）
     */
    public boolean isPythonHealthy() {
        // 超过 90 秒未更新，视为不健康
        if (Instant.now().isAfter(lastCheckTime.plusSeconds(90))) {
            return false;
        }
        return pythonHealthy;
    }

    /**
     * 获取最后一次健康检查详情
     */
    public Map<String, Object> getLastCheckDetails() {
        return new LinkedHashMap<>(lastCheckDetails);
    }

    /**
     * 手动触发一次健康检查
     */
    public Map<String, Object> forceCheck() {
        checkPythonHealth();
        return getLastCheckDetails();
    }

    private String getConfigWithDefault(String key, String defaultValue) {
        String val = configService.getConfigValue(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
