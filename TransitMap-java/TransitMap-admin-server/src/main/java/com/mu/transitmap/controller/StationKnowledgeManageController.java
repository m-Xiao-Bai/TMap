package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.entity.StationKnowledge;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.StationKnowledgeMapper;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 知识库管理（角色 ≥ 2 可读，≥ 3 可写）
 */
@RestController
@RequestMapping("/manage/rag-knowledge")
public class StationKnowledgeManageController {

    @Autowired
    private StationKnowledgeMapper knowledgeMapper;

    @Autowired
    private RagService ragService;

    @GetMapping("/page")
    public Result<Page<StationKnowledge>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String kw,
            HttpServletRequest request) {
        ensureRead(request);
        LambdaQueryWrapper<StationKnowledge> w = new LambdaQueryWrapper<StationKnowledge>()
                .orderByDesc(StationKnowledge::getPriority)
                .orderByDesc(StationKnowledge::getUpdateTime);
        if (cityId != null) w.eq(StationKnowledge::getCityId, cityId);
        if (stationId != null) w.eq(StationKnowledge::getStationId, stationId);
        if (category != null && !category.isEmpty()) w.eq(StationKnowledge::getCategory, category);
        if (kw != null && !kw.isEmpty()) {
            w.and(q -> q.like(StationKnowledge::getTitle, kw)
                    .or().like(StationKnowledge::getKeywords, kw)
                    .or().like(StationKnowledge::getContent, kw));
        }
        return Result.success(knowledgeMapper.selectPage(new Page<>(pageNum, pageSize), w));
    }

    @PostMapping("/create")
    public Result<StationKnowledge> create(@RequestBody StationKnowledge body, HttpServletRequest request) {
        ensureWrite(request);
        validate(body);
        body.setId(null);
        if (body.getStatus() == null) body.setStatus(1);
        if (body.getPriority() == null) body.setPriority(0);
        body.setCreateTime(LocalDateTime.now());
        body.setUpdateTime(LocalDateTime.now());
        body.setEmbedding(null); // 新建时清空，由「重建向量」批量写入
        knowledgeMapper.insert(body);
        // 异步尝试写一次 embedding（失败不影响主流程）
        tryComputeEmbedding(body);
        return Result.success(body);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StationKnowledge body, HttpServletRequest request) {
        ensureWrite(request);
        validate(body);
        body.setId(id);
        body.setUpdateTime(LocalDateTime.now());
        // 修改内容后清空旧 embedding，下次重建或下次召回时按需补
        StationKnowledge old = knowledgeMapper.selectById(id);
        if (old != null) {
            boolean textChanged = !equalsSafe(old.getTitle(), body.getTitle())
                    || !equalsSafe(old.getContent(), body.getContent())
                    || !equalsSafe(old.getKeywords(), body.getKeywords());
            if (textChanged) {
                body.setEmbedding(null);
            }
        }
        knowledgeMapper.updateById(body);
        if (body.getEmbedding() == null) {
            tryComputeEmbedding(body);
        }
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ensureWrite(request);
        knowledgeMapper.deleteById(id);
        return Result.success(null);
    }

    /**
     * 补齐缺失的 embedding（仅处理 embedding 为空的条目）
     */
    @PostMapping("/backfill-embedding")
    public Result<Map<String, Object>> backfillEmbedding(
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request) {
        ensureWrite(request);
        int ok = ragService.backfillMissingEmbeddings(limit);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", ok);
        r.put("limit", limit);
        return Result.success(r);
    }

    /**
     * 强制重建所有 embedding（耗时操作，建议 N 不大时使用）
     */
    @PostMapping("/rebuild-embedding")
    public Result<Map<String, Object>> rebuildEmbedding(HttpServletRequest request) {
        ensureWrite(request);
        int n = ragService.rebuildAll();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", n);
        return Result.success(r);
    }

    /**
     * 向量化状态：总条数 / 已向量化 / 待补
     */
    @GetMapping("/embedding-status")
    public Result<Map<String, Object>> embeddingStatus(HttpServletRequest request) {
        ensureRead(request);
        long total = knowledgeMapper.selectCount(
                new LambdaQueryWrapper<StationKnowledge>().eq(StationKnowledge::getStatus, 1));
        long missing = knowledgeMapper.selectCount(
                new LambdaQueryWrapper<StationKnowledge>()
                        .eq(StationKnowledge::getStatus, 1)
                        .and(w -> w.isNull(StationKnowledge::getEmbedding)
                                .or().eq(StationKnowledge::getEmbedding, "")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("embedded", total - missing);
        r.put("missing", missing);
        return Result.success(r);
    }

    private void tryComputeEmbedding(StationKnowledge k) {
        try {
            ragService.computeAndStoreEmbedding(k);
        } catch (Exception ignored) {
            // 静默失败：用户可以稍后用「重建向量」补
        }
    }

    private boolean equalsSafe(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private void validate(StationKnowledge k) {
        if (k.getTitle() == null || k.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        if (k.getContent() == null || k.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "正文不能为空");
        }
    }

    private void ensureRead(HttpServletRequest request) {
        Integer r = (Integer) request.getAttribute("roleCode");
        if (r == null || r < 2) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void ensureWrite(HttpServletRequest request) {
        Integer r = (Integer) request.getAttribute("roleCode");
        if (r == null || r < 3) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
