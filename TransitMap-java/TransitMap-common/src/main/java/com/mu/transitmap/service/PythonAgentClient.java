package com.mu.transitmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Python Agent 服务客户端
 *
 * 通过 HTTP SSE 流式调用 Python Agent 服务，将结果转发到 WebSocket。
 */
@Service
public class PythonAgentClient {

    private static final Logger log = LoggerFactory.getLogger(PythonAgentClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired private SystemConfigServiceImpl configService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "python-agent-client");
        t.setDaemon(true);
        return t;
    });

    /**
     * 流式调用 Python Agent，将 SSE 事件转发到 push 回调。
     *
     * @return runId
     */
    public String streamChat(AgentContext ctx, Consumer<Object> push) {
        String runId = ctx.getRunId();
        String pythonUrl = getConfigWithDefault("agent.python.url", "http://localhost:8000");
        String apiKey = getConfigWithDefault("agent.python.api_key", "");

        executor.submit(() -> {
            try {
                // 构建请求体
                Map<String, Object> body = Map.of(
                        "user_message", ctx.getUserMessage(),
                        "session_id", ctx.getChatSessionId() != null ? ctx.getChatSessionId().toString() : "",
                        "lat", ctx.getLat(),
                        "lng", ctx.getLng(),
                        "chat_history", getRecentHistory(ctx)
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-API-Key", apiKey);
                headers.set("Accept", "text/event-stream");

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                // 使用 RestTemplate 流式读取 SSE
                // 注意：RestTemplate 不原生支持 SSE 流式读取，使用 HttpUrlConnection 替代
                String url = pythonUrl + "/api/agent/chat";
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000);

                // 写入请求体
                byte[] bodyBytes = mapper.writeValueAsBytes(body);
                conn.getOutputStream().write(bodyBytes);

                // 读取 SSE 流
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        String jsonStr = line.substring(6).trim();
                        if (jsonStr.isEmpty()) continue;

                        try {
                            JsonNode node = mapper.readTree(jsonStr);
                            String type = node.has("type") ? node.get("type").asText() : "";

                            switch (type) {
                                case "status" -> push.accept(Map.of(
                                        "type", "status",
                                        "text", node.get("text").asText()
                                ));
                                case "delta" -> push.accept(Map.of(
                                        "type", "delta",
                                        "text", node.get("text").asText()
                                ));
                                case "card" -> push.accept(Map.of(
                                        "type", "card",
                                        "data", mapper.convertValue(node.get("data"), Map.class)
                                ));
                                case "chips" -> {
                                    java.util.List<String> items = new java.util.ArrayList<>();
                                    node.get("items").forEach(item -> items.add(item.asText()));
                                    push.accept(Map.of("type", "chips", "items", items));
                                }
                                case "done" -> {
                                    Map<String, Object> doneMsg = new java.util.LinkedHashMap<>();
                                    doneMsg.put("type", "done");
                                    doneMsg.put("runId", runId);
                                    if (node.has("messageId")) {
                                        doneMsg.put("messageId", node.get("messageId").asLong());
                                    }
                                    push.accept(doneMsg);
                                }
                                case "error" -> push.accept(Map.of(
                                        "type", "error",
                                        "code", node.has("code") ? node.get("code").asText() : "PYTHON_ERROR",
                                        "message", node.has("message") ? node.get("message").asText() : "未知错误"
                                ));
                                default -> log.debug("未知 SSE 事件类型: {}", type);
                            }
                        } catch (Exception e) {
                            log.warn("解析 SSE 数据失败: {}", jsonStr, e);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Python Agent 调用失败: {}", e.getMessage(), e);
                push.accept(Map.of(
                        "type", "error",
                        "code", "PYTHON_AGENT_ERROR",
                        "message", "Agent 服务暂时不可用，请稍后再试"
                ));
            }
        });

        return runId;
    }

    /**
     * 获取最近对话历史（用于传递给 Python Agent）
     */
    private java.util.List<Map<String, String>> getRecentHistory(AgentContext ctx) {
        // 简化实现：返回空列表，Python 端自行查询
        return java.util.List.of();
    }

    /**
     * 检查 Python Agent 是否健康
     */
    public boolean isHealthy() {
        try {
            String pythonUrl = getConfigWithDefault("agent.python.url", "http://localhost:8000");
            String apiKey = getConfigWithDefault("agent.python.api_key", "");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey);

            ResponseEntity<String> resp = restTemplate.exchange(
                    pythonUrl + "/health",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private String getConfigWithDefault(String key, String defaultValue) {
        String val = configService.getConfigValue(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
