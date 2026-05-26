package com.mu.transitmap.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 统一抽象接口
 */
public interface LlmClient {

    /**
     * 同步调用 LLM
     */
    LlmReply complete(LlmRequest request);

    /**
     * 流式调用 LLM
     */
    void completeStream(LlmRequest request, Consumer<String> onDelta, Consumer<LlmUsage> onDone);

    /**
     * 文本向量化（embedding）
     * 单个/批量文本 → 等长 float 向量列表
     */
    List<float[]> embed(List<String> texts, String model);

    record LlmRequest(
            String model,
            String systemPrompt,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens,
            int timeoutMs
    ) {}

    record LlmReply(
            String content,
            LlmUsage usage
    ) {}

    record LlmUsage(
            int inputTokens,
            int outputTokens
    ) {}
}
