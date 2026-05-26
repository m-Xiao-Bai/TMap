package com.mu.transitmap.config;

import com.mu.transitmap.agent.TransitMapAssistant;
import com.mu.transitmap.agent.tools.MetroTools;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置：从 DB 动态读取 agent.llm.* 构建 Model 和 AI Service
 *
 * 使用 @Bean(initMethod = "") 避免启动时失败，改为懒加载模式
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired(required = false)
    private MetroTools metroTools;

    /**
     * 同步 ChatModel（用于 @Tool 内部调用等非流式场景）
     * 当 API Key 未配置时返回 null，避免启动失败
     */
    @Bean
    public ChatModel chatModel() {
        try {
            String provider = readProvider();
            String baseUrl = resolveBaseUrl(configService.getConfigValue("agent.llm.base_url"), provider);
            String model = resolveModel(configService.getConfigValue("agent.llm.model"), provider);
            String apiKey = getApiKeyOrNull("agent.llm.api_key");

            // API Key 未配置时不创建 bean
            if (apiKey == null) {
                log.info("agent.llm.api_key 未配置，ChatModel bean 不会创建（管理后台配置后重启生效）");
                return null;
            }

            int timeoutMs = configService.getConfigInt("agent.llm.timeout_ms", 30000);
            int maxTokens = configService.getConfigInt("agent.llm.max_tokens", 1024);
            double temperature = readTemperature();

            log.info("Init ChatModel: provider={}, baseUrl={}, model={}, timeout={}ms, temperature={}, maxTokens={}",
                    provider, baseUrl, model, timeoutMs, temperature, maxTokens);
            return OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
        } catch (Exception e) {
            log.warn("创建 ChatModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式 StreamingChatModel（用于 AI Service 流式回复）
     */
    @Bean
    public StreamingChatModel streamingChatModel() {
        try {
            String provider = readProvider();
            String baseUrl = resolveBaseUrl(configService.getConfigValue("agent.llm.base_url"), provider);
            String model = resolveModel(configService.getConfigValue("agent.llm.model"), provider);
            String apiKey = getApiKeyOrNull("agent.llm.api_key");

            if (apiKey == null) {
                log.info("agent.llm.api_key 未配置，StreamingChatModel bean 不会创建");
                return null;
            }

            int timeoutMs = configService.getConfigInt("agent.llm.timeout_ms", 30000);
            int maxTokens = configService.getConfigInt("agent.llm.max_tokens", 1024);
            double temperature = readTemperature();

            log.info("Init StreamingChatModel: provider={}, baseUrl={}, model={}, temperature={}, maxTokens={}",
                    provider, baseUrl, model, temperature, maxTokens);
            return OpenAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
        } catch (Exception e) {
            log.warn("创建 StreamingChatModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 读温度配置（DB 里存的是 string 或 number，统一转 double） */
    private double readTemperature() {
        try {
            String raw = configService.getConfigValue("agent.llm.temperature");
            if (raw != null && !raw.isBlank()) {
                return Double.parseDouble(raw.trim());
            }
        } catch (NumberFormatException ignored) {}
        return 0.3;
    }

    /** 读 provider 配置，全小写规范化 */
    private String readProvider() {
        String raw = configService.getConfigValue("agent.llm.provider");
        if (raw == null || raw.isBlank()) return "openai-compatible";
        return raw.trim().toLowerCase();
    }

    /**
     * 根据 provider 决定 baseUrl 默认值（DB 已配置 base_url 时直接用 DB 值，否则走 provider 默认）
     * 所有 provider 都走 OpenAI 兼容协议，不需要额外依赖
     */
    private String resolveBaseUrl(String dbValue, String provider) {
        if (dbValue != null && !dbValue.isBlank()) return dbValue.trim();
        switch (provider) {
            case "deepseek":
                return "https://api.deepseek.com/v1";
            case "qwen":
            case "dashscope":
            case "tongyi":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "kimi":
            case "moonshot":
                return "https://api.moonshot.cn/v1";
            case "doubao":
            case "volcengine":
            case "ark":
                return "https://ark.cn-beijing.volces.com/api/v3";
            case "zhipu":
            case "glm":
                return "https://open.bigmodel.cn/api/paas/v4";
            case "siliconflow":
                return "https://api.siliconflow.cn/v1";
            case "openai":
            case "openai-compatible":
            default:
                return "https://api.openai.com/v1";
        }
    }

    /** 根据 provider 决定模型默认值 */
    private String resolveModel(String dbValue, String provider) {
        if (dbValue != null && !dbValue.isBlank()) return dbValue.trim();
        switch (provider) {
            case "deepseek": return "deepseek-chat";
            case "qwen":
            case "dashscope":
            case "tongyi": return "qwen-plus";
            case "kimi":
            case "moonshot": return "moonshot-v1-8k";
            case "doubao":
            case "volcengine":
            case "ark": return "doubao-pro-32k";
            case "zhipu":
            case "glm": return "glm-4-flash";
            case "siliconflow": return "Qwen/Qwen2.5-7B-Instruct";
            case "openai":
            case "openai-compatible":
            default: return "gpt-3.5-turbo";
        }
    }

    /**
     * EmbeddingModel（用于 RAG 向量化）
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        try {
            String baseUrl = configService.getConfigValue("agent.embedding.base_url");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = configService.getConfigValue("agent.llm.base_url");
            }
            String model = configService.getConfigValue("agent.embedding.model");
            if (model == null || model.isBlank()) {
                model = "text-embedding-3-small";
            }

            String apiKey = getApiKeyOrNull("agent.embedding.api_key");
            if (apiKey == null) {
                apiKey = getApiKeyOrNull("agent.llm.api_key");
            }
            if (apiKey == null) {
                log.info("Embedding API Key 未配置，EmbeddingModel bean 不会创建");
                return null;
            }

            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.openai.com/v1";
            }

            log.info("Init EmbeddingModel: baseUrl={}, model={}", baseUrl, model);
            return OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(model)
                    .build();
        } catch (Exception e) {
            log.warn("创建 EmbeddingModel 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * TransitMapAssistant AI Service：LLM 自主编排 @Tool 调用
     * 仅当 ChatModel 和 StreamingChatModel 都可用时才创建
     */
    @Bean
    public TransitMapAssistant transitMapAssistant(
            ChatModel chatModel,
            StreamingChatModel streamingModel) {

        // 如果模型未配置，不创建 AI Service
        if (chatModel == null || streamingModel == null) {
            log.info("ChatModel 或 StreamingChatModel 未就绪，TransitMapAssistant bean 不会创建");
            return null;
        }

        if (metroTools == null) {
            log.info("MetroTools 未注入，TransitMapAssistant bean 不会创建");
            return null;
        }

        int maxMemory = configService.getConfigInt("agent.history.max_context_msgs", 10);

        log.info("Init TransitMapAssistant with maxMemory={}", maxMemory);
        return AiServices.builder(TransitMapAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .maxMessages(maxMemory)
                        .id(memoryId)
                        .build())
                .tools(metroTools)
                .build();
    }

    /**
     * 获取 API Key，未配置时返回 null（不抛异常）
     */
    private String getApiKeyOrNull(String configKey) {
        try {
            String key = configService.getRaw(configKey);
            if (key == null || key.isEmpty()) {
                return null;
            }
            if (key.startsWith("enc:")) {
                log.warn("API Key 仍是密文（{}），请重新保存一次", configKey);
                return null;
            }
            return key;
        } catch (Exception e) {
            log.warn("获取 API Key 失败（{}）: {}", configKey, e.getMessage());
            return null;
        }
    }
}
