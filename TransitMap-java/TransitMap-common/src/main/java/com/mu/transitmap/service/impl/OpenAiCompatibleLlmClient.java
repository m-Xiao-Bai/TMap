package com.mu.transitmap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mu.transitmap.service.LlmClient;
import com.mu.transitmap.service.LlmClient.LlmRequest;
import com.mu.transitmap.service.LlmClient.LlmReply;
import com.mu.transitmap.service.LlmClient.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 LLM 客户端（支持 DeepSeek、通义、Kimi 等）
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SystemConfigServiceImpl configService;

    @Override
    public LlmReply complete(LlmRequest request) {
        try {
            String baseUrl = configService.getConfigValue("agent.llm.base_url");
            String apiKey = getApiKey();
            String url = baseUrl + "/chat/completions";

            ObjectNode body = buildRequestBody(request, false);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);

            JsonNode respBody = response.getBody();
            String content = respBody.path("choices").path(0).path("message").path("content").asText("");
            int inputTokens = respBody.path("usage").path("prompt_tokens").asInt(0);
            int outputTokens = respBody.path("usage").path("completion_tokens").asInt(0);

            return new LlmReply(content, new LlmUsage(inputTokens, outputTokens));
        } catch (Exception e) {
            log.error("LLM complete error", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void completeStream(LlmRequest request, Consumer<String> onDelta, Consumer<LlmUsage> onDone) {
        try {
            String baseUrl = configService.getConfigValue("agent.llm.base_url");
            String apiKey = getApiKey();
            String urlStr = baseUrl + "/chat/completions";

            ObjectNode body = buildRequestBody(request, true);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(request.timeoutMs());
            conn.setReadTimeout(request.timeoutMs());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyJson.getBytes(StandardCharsets.UTF_8));
            }

            int totalInput = 0, totalOutput = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;

                    JsonNode chunk = objectMapper.readTree(data);
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    if (delta.has("content")) {
                        String text = delta.get("content").asText("");
                        if (!text.isEmpty()) {
                            onDelta.accept(text);
                            totalOutput += estimateTokens(text);
                        }
                    }
                    JsonNode usage = chunk.path("usage");
                    if (!usage.isMissingNode()) {
                        totalInput = usage.path("prompt_tokens").asInt(0);
                        totalOutput = usage.path("completion_tokens").asInt(totalOutput);
                    }
                }
            }

            onDone.accept(new LlmUsage(totalInput, totalOutput));
        } catch (Exception e) {
            log.error("LLM stream error", e);
            throw new RuntimeException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequestBody(LlmRequest request, boolean stream) {
        String model = request.model();
        if (model == null || model.isEmpty()) {
            model = configService.getConfigValue("agent.llm.model");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());

        ArrayNode messages = body.putArray("messages");
        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", request.systemPrompt());
        }
        if (request.messages() != null) {
            for (Map<String, String> msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.get("role"));
                msgNode.put("content", msg.get("content"));
            }
        }
        return body;
    }

    private String getApiKey() {
        String key;
        try {
            key = configService.getRaw("agent.llm.api_key");
        } catch (Exception e) {
            throw new RuntimeException("LLM API Key 解密失败：" + e.getMessage()
                    + "。请在管理后台「Agent 配置 → 大模型」重新保存一次 api_key（密钥已变更）", e);
        }
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("LLM API Key 未配置，请到管理后台「Agent 配置 → 大模型」填入");
        }
        if (key.startsWith("enc:")) {
            throw new RuntimeException("LLM API Key 仍是密文（解密链路异常），请重新保存一次 api_key");
        }
        return key;
    }

    private int estimateTokens(String text) {
        // 粗略估算：中文 1 字 ≈ 2 token，英文 1 词 ≈ 1.3 token
        return (int) (text.length() * 1.5);
    }

    /**
     * 文本向量化。优先使用 agent.embedding.* 独立配置，回退到 agent.llm.*
     * 大多数 LLM provider（DeepSeek、Kimi 等）不支持 embedding，需要单独配 OpenAI / 本地模型。
     */
    @Override
    public List<float[]> embed(List<String> texts, String model) {
        if (texts == null || texts.isEmpty()) return java.util.Collections.emptyList();
        try {
            String baseUrl = configService.getConfigValue("agent.embedding.base_url");
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = configService.getConfigValue("agent.llm.base_url");
            }
            String apiKey;
            try {
                apiKey = configService.getRaw("agent.embedding.api_key");
            } catch (Exception e) {
                apiKey = null;
            }
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = getApiKey();
            }
            String useModel = (model != null && !model.isEmpty())
                    ? model
                    : configService.getConfigValue("agent.embedding.model");
            if (useModel == null || useModel.isEmpty()) useModel = "text-embedding-3-small";

            String url = baseUrl + "/embeddings";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", useModel);
            ArrayNode inputArr = body.putArray("input");
            for (String t : texts) {
                inputArr.add(t == null ? "" : t);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            RestTemplate rt = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<JsonNode> response = rt.exchange(
                    java.net.URI.create(url), HttpMethod.POST, entity, JsonNode.class);

            JsonNode data = response.getBody() == null ? null : response.getBody().path("data");
            if (data == null || !data.isArray()) {
                throw new RuntimeException("Embedding 接口返回格式异常: " + response.getBody());
            }
            List<float[]> result = new java.util.ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray()) {
                    throw new RuntimeException("Embedding 字段缺失");
                }
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = (float) emb.get(i).asDouble();
                }
                result.add(vec);
            }
            return result;
        } catch (Exception e) {
            log.error("Embedding 调用失败", e);
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}
