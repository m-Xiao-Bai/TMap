package com.mu.transitmap.controller;

import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CrawlerService;
import com.mu.transitmap.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 爬虫管理 Controller
 *
 * 仅允许超级管理员（role >= 3）操作。
 * 转发请求到 Python 爬虫服务。
 */
@RestController
@RequestMapping("/manage/crawler")
public class CrawlerManageController {

    private static final int MIN_ROLE = 3;

    @Autowired
    private CrawlerService crawlerService;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 触发单城市爬取
     */
    @PostMapping("/trigger")
    public Result<Map<String, Object>> triggerCrawl(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        ensureRole(request);

        String cityName = (String) body.get("city_name");
        Long countryId = body.get("country_id") != null
                ? Long.valueOf(body.get("country_id").toString()) : 1L;

        if (cityName == null || cityName.trim().isEmpty()) {
            return Result.fail(400, "城市名称不能为空");
        }

        try {
            Long userId = getUserId(request);
            String taskId = crawlerService.triggerCrawl(cityName.trim(), countryId, "osm");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("city_name", cityName.trim());
            data.put("status", "pending");
            return Result.success(data);
        } catch (Exception e) {
            return Result.fail(500, "触发爬取失败: " + e.getMessage());
        }
    }

    /**
     * 批量爬取
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> triggerBatch(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        ensureRole(request);

        List<Map<String, Object>> cities = (List<Map<String, Object>>) body.get("cities");
        if (cities == null || cities.isEmpty()) {
            return Result.fail(400, "城市列表不能为空");
        }

        try {
            List<String> taskIds = crawlerService.triggerBatchCrawl(cities);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_ids", taskIds);
            data.put("count", taskIds.size());
            return Result.success(data);
        } catch (Exception e) {
            return Result.fail(500, "批量爬取失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> getTaskStatus(
            @PathVariable String taskId,
            HttpServletRequest request) {
        ensureRole(request);

        Map<String, Object> task = crawlerService.getTaskStatus(taskId);
        if (task == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 获取所有任务列表
     */
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> getAllTasks(HttpServletRequest request) {
        ensureRole(request);

        List<Map<String, Object>> tasks = crawlerService.getAllTasks();
        return Result.success(tasks);
    }

    /**
     * 取消任务
     */
    @DeleteMapping("/task/{taskId}")
    public Result<Void> cancelTask(
            @PathVariable String taskId,
            HttpServletRequest request) {
        ensureRole(request);

        boolean success = crawlerService.cancelTask(taskId);
        if (!success) {
            return Result.fail(400, "任务无法取消");
        }
        return Result.success(null);
    }

    /**
     * 获取待审核列表
     */
    @GetMapping("/review/pending")
    public Result<Map<String, Object>> getPendingReviews(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        ensureRole(request);

        Map<String, Object> data = crawlerService.getPendingReviews(city, page, size);
        return Result.success(data);
    }

    /**
     * 批准审核
     */
    @PostMapping("/review/approve/{reviewId}")
    public Result<Void> approveReview(
            @PathVariable int reviewId,
            HttpServletRequest request) {
        ensureRole(request);

        boolean success = crawlerService.approveReview(reviewId);
        if (!success) {
            return Result.fail(500, "批准失败");
        }
        return Result.success(null);
    }

    /**
     * 拒绝审核
     */
    @PostMapping("/review/reject/{reviewId}")
    public Result<Void> rejectReview(
            @PathVariable int reviewId,
            HttpServletRequest request) {
        ensureRole(request);

        boolean success = crawlerService.rejectReview(reviewId);
        if (!success) {
            return Result.fail(500, "拒绝失败");
        }
        return Result.success(null);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health(HttpServletRequest request) {
        ensureRole(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("healthy", crawlerService.isHealthy());
        return Result.success(data);
    }

    // ===== 辅助方法 =====

    private void ensureRole(HttpServletRequest request) {
        int role = getRoleCode(request);
        if (role < MIN_ROLE) {
            throw new RuntimeException("权限不足，需要超级管理员或最高级管理员权限");
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

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid instanceof Long) return (Long) uid;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserIdFromToken(auth.substring(7));
            } catch (Exception ignored) {}
        }
        return null;
    }
}
