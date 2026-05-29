package com.mu.transitmap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 对话质量管理 Controller
 *
 * 转发请求到 Python 质量评估服务。
 * 仅超级管理员（role >= 3）可访问。
 */
@RestController
@RequestMapping("/manage/quality")
public class QualityManageController {

    private static final int MIN_ROLE = 3;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SystemConfigServiceImpl configService;
    @Autowired
    private JwtUtil jwtUtil;
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
     * 获取质量统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request) {
        ensureRole(request);
        try {
            String url = getBaseUrl() + "/api/quality/stats?days=" + days;
            HttpEntity<Void> req = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, req, String.class);
            JsonNode json = mapper.readTree(resp.getBody());
            if (json.has("data")) {
                return Result.success(mapper.convertValue(json.get("data"), Map.class));
            }
        } catch (Exception e) {
            // 返回空数据
        }
        return Result.success(Map.of(
                "total_conversations", 0,
                "positive_rate", 0,
                "avg_quality_score", 0,
                "hallucination_rate", 0,
                "by_intent", Map.of()
        ));
    }

    /**
     * 获取低分对话列表
     */
    @GetMapping("/low-score")
    public Result<Map<String, Object>> getLowScore(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        ensureRole(request);
        try {
            String url = getBaseUrl() + "/api/quality/low-score?page=" + page + "&size=" + size;
            HttpEntity<Void> req = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, req, String.class);
            JsonNode json = mapper.readTree(resp.getBody());
            if (json.has("data")) {
                return Result.success(mapper.convertValue(json.get("data"), Map.class));
            }
        } catch (Exception e) {
            // 返回空数据
        }
        return Result.success(Map.of("items", List.of(), "total", 0, "page", page, "size", size));
    }

    // ===== 辅助 =====

    private void ensureRole(HttpServletRequest request) {
        int role = getRoleCode(request);
        if (role < MIN_ROLE) {
            throw new RuntimeException("权限不足");
        }
    }

    private int getRoleCode(HttpServletRequest request) {
        Object roleObj = request.getAttribute("roleCode");
        if (roleObj instanceof Integer) return (Integer) roleObj;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getRoleCodeFromToken(auth.substring(7));
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
