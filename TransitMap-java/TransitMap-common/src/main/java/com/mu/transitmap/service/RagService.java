package com.mu.transitmap.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.StationKnowledge;
import com.mu.transitmap.mapper.StationKnowledgeMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识检索服务
 *
 * 两种模式（agent.rag.use_embedding 控制）：
 * - 关键词模式：用户输入分词 → title/keywords 包含计数 + priority 加权
 * - 向量模式：用户输入 embed → 与候选 embedding 算余弦相似度 → 取相似度 ≥ agent.rag.min_similarity 的 topK
 *
 * 候选范围：站点/线路精确 ∪ 城市匹配 ∪ 全局通用，最多 200 条防全表扫
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Double>> VEC_TYPE = new TypeReference<List<Double>>() {};

    @Autowired
    private StationKnowledgeMapper knowledgeMapper;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 检索召回。返回拼接好的知识片段字符串（可直接注入 LLM system message）。
     * 关闭 RAG 时返回空字符串。
     */
    public String retrieveForContext(String userMessage, Long cityId, List<Long> stationIds) {
        if (configService.getConfigInt("agent.rag.enabled", 0) != 1) return "";
        if (userMessage == null || userMessage.isBlank()) return "";

        int topK = configService.getConfigInt("agent.rag.top_k", 3);
        if (topK <= 0) return "";

        List<StationKnowledge> candidates = loadCandidates(cityId, stationIds);
        if (candidates.isEmpty()) return "";

        boolean useEmbedding = configService.getConfigInt("agent.rag.use_embedding", 0) == 1;
        List<Scored> top;
        if (useEmbedding) {
            top = retrieveByEmbedding(userMessage, candidates, topK);
            if (top.isEmpty()) {
                log.debug("Embedding 召回为空，降级到关键词");
                top = retrieveByKeyword(userMessage, candidates, topK);
            }
        } else {
            top = retrieveByKeyword(userMessage, candidates, topK);
        }
        if (top.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("【可参考的知识库内容（请优先基于以下事实回答）】\n");
        int i = 1;
        for (Scored s : top) {
            sb.append(i++).append(". ").append(s.k.getTitle()).append("\n");
            sb.append(s.k.getContent()).append("\n\n");
        }
        log.debug("RAG hit {} entries for query={}", top.size(), userMessage);
        return sb.toString();
    }

    // ===== 向量召回 =====

    private List<Scored> retrieveByEmbedding(String userMessage, List<StationKnowledge> candidates, int topK) {
        try {
            // 1. 用户输入向量化（使用 LangChain4j EmbeddingModel）
            Response<Embedding> response = embeddingModel.embed(userMessage);
            float[] q = response.content().vector();

            // 2. 与候选逐一计算余弦
            double minSim = parseDouble(configService.getConfigValue("agent.rag.min_similarity"), 0.3);
            List<Scored> scored = new ArrayList<>();
            for (StationKnowledge k : candidates) {
                float[] v = parseEmbedding(k.getEmbedding());
                if (v == null || v.length != q.length) continue;
                double sim = cosine(q, v);
                if (sim < minSim) continue;
                // 与 priority 加权（priority 每 +1 等价于 +0.01 相似度）
                int pr = k.getPriority() == null ? 0 : k.getPriority();
                scored.add(new Scored(k, sim + pr * 0.01));
            }
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            return scored.subList(0, Math.min(topK, scored.size()));
        } catch (Exception e) {
            log.warn("向量召回失败，降级到关键词: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ===== 关键词召回 =====

    private List<Scored> retrieveByKeyword(String userMessage, List<StationKnowledge> candidates, int topK) {
        Set<String> tokens = tokenize(userMessage);
        if (tokens.isEmpty()) return Collections.emptyList();
        List<Scored> scored = new ArrayList<>();
        for (StationKnowledge k : candidates) {
            int score = scoreKeyword(k, tokens);
            if (score > 0) {
                scored.add(new Scored(k, score + (k.getPriority() == null ? 0 : k.getPriority())));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    // ===== 公开：embedding 写入/重建 =====

    /**
     * 为单条知识计算并写入 embedding。若 RAG 未启用 / Embedding 未配置，返回 false。
     */
    public boolean computeAndStoreEmbedding(StationKnowledge k) {
        try {
            String text = embeddingText(k);
            if (text.isBlank()) return false;
            Response<Embedding> response = embeddingModel.embed(text);
            float[] vec = response.content().vector();
            k.setEmbedding(serializeEmbedding(vec));
            knowledgeMapper.updateById(k);
            return true;
        } catch (Exception e) {
            log.warn("computeAndStoreEmbedding failed for id={}", k.getId(), e);
            return false;
        }
    }

    /**
     * 批量补齐缺失的 embedding，最多处理 limit 条
     * @return 成功条数
     */
    public int backfillMissingEmbeddings(int limit) {
        if (limit <= 0) limit = 100;
        List<StationKnowledge> rows = knowledgeMapper.selectList(
                new LambdaQueryWrapper<StationKnowledge>()
                        .eq(StationKnowledge::getStatus, 1)
                        .and(w -> w.isNull(StationKnowledge::getEmbedding)
                                .or().eq(StationKnowledge::getEmbedding, ""))
                        .last("LIMIT " + limit));
        if (rows.isEmpty()) return 0;

        // 批量调（使用 LangChain4j EmbeddingModel）
        List<String> texts = rows.stream().map(this::embeddingText).collect(Collectors.toList());
        List<float[]> vecs;
        try {
            List<TextSegment> segments = texts.stream()
                    .map(t -> TextSegment.from(t == null ? "" : t))
                    .collect(Collectors.toList());
            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            vecs = response.content().stream()
                    .map(Embedding::vector)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("批量 embed 失败", e);
            return 0;
        }
        int ok = 0;
        for (int i = 0; i < rows.size() && i < vecs.size(); i++) {
            try {
                rows.get(i).setEmbedding(serializeEmbedding(vecs.get(i)));
                knowledgeMapper.updateById(rows.get(i));
                ok++;
            } catch (Exception ignored) {}
        }
        return ok;
    }

    /**
     * 强制重建所有 embedding（先清空再批量补；分批处理）
     */
    public int rebuildAll() {
        // 清空所有 embedding
        List<StationKnowledge> all = knowledgeMapper.selectList(
                new LambdaQueryWrapper<StationKnowledge>().eq(StationKnowledge::getStatus, 1));
        for (StationKnowledge k : all) {
            k.setEmbedding(null);
            knowledgeMapper.updateById(k);
        }
        // 分批 32 条一组
        int total = 0;
        int batchSize = 32;
        while (true) {
            int n = backfillMissingEmbeddings(batchSize);
            if (n == 0) break;
            total += n;
        }
        return total;
    }

    // ===== 辅助 =====

    private String embeddingText(StationKnowledge k) {
        StringBuilder sb = new StringBuilder();
        if (k.getTitle() != null) sb.append(k.getTitle()).append('\n');
        if (k.getKeywords() != null && !k.getKeywords().isBlank()) sb.append(k.getKeywords()).append('\n');
        if (k.getContent() != null) sb.append(k.getContent());
        String text = sb.toString().trim();
        // OpenAI embedding 单次 8192 tokens，约 3 万字符。截断防御
        if (text.length() > 8000) text = text.substring(0, 8000);
        return text;
    }

    private List<StationKnowledge> loadCandidates(Long cityId, List<Long> stationIds) {
        LambdaQueryWrapper<StationKnowledge> w = new LambdaQueryWrapper<StationKnowledge>()
                .eq(StationKnowledge::getStatus, 1);
        if (cityId != null && stationIds != null && !stationIds.isEmpty()) {
            w.and(q -> q.eq(StationKnowledge::getCityId, cityId)
                    .or().isNull(StationKnowledge::getCityId)
                    .or().in(StationKnowledge::getStationId, stationIds));
        } else if (cityId != null) {
            w.and(q -> q.eq(StationKnowledge::getCityId, cityId)
                    .or().isNull(StationKnowledge::getCityId));
        }
        w.last("LIMIT 200");
        return knowledgeMapper.selectList(w);
    }

    private int scoreKeyword(StationKnowledge k, Set<String> tokens) {
        String haystack = ((k.getTitle() == null ? "" : k.getTitle()) + " "
                + (k.getKeywords() == null ? "" : k.getKeywords())).toLowerCase();
        if (haystack.isBlank()) return 0;
        int score = 0;
        for (String t : tokens) {
            if (haystack.contains(t)) score++;
        }
        return score;
    }

    private Set<String> tokenize(String s) {
        Set<String> out = new HashSet<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isChinese(c)) {
                if (buf.length() > 0) { out.add(buf.toString().toLowerCase()); buf.setLength(0); }
                out.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                buf.append(c);
            } else {
                if (buf.length() > 0) { out.add(buf.toString().toLowerCase()); buf.setLength(0); }
            }
        }
        if (buf.length() > 0) out.add(buf.toString().toLowerCase());
        return out.stream().filter(t -> !t.isEmpty()).collect(Collectors.toSet());
    }

    private boolean isChinese(char c) { return c >= 0x4E00 && c <= 0x9FFF; }

    private double parseDouble(String s, double fallback) {
        if (s == null) return fallback;
        try { return Double.parseDouble(s); } catch (Exception e) { return fallback; }
    }

    private String serializeEmbedding(float[] v) {
        // 存为 JSON 数组（数据库列是 JSON）
        try {
            return MAPPER.writeValueAsString(v);
        } catch (Exception e) {
            return null;
        }
    }

    private float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Double> list = MAPPER.readValue(json, VEC_TYPE);
            float[] v = new float[list.size()];
            for (int i = 0; i < list.size(); i++) v[i] = list.get(i).floatValue();
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static class Scored {
        final StationKnowledge k;
        final double score;
        Scored(StationKnowledge k, double score) { this.k = k; this.score = score; }
    }
}
