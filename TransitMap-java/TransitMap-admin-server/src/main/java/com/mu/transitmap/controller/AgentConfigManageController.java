package com.mu.transitmap.controller;

import com.mu.transitmap.entity.SystemConfig;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.AgentEngineRouter;
import com.mu.transitmap.service.HealthCheckScheduler;
import com.mu.transitmap.service.LlmClient;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 配置管理（路线助手专用）
 *
 * - 仅允许操作 agent.* 前缀的配置项
 * - 权限：超级管理员（3）+ 最高级管理员（4）
 * - 与通用 SystemConfigManageController 解耦：高级管理员可调 agent，但不能改其他系统配置
 */
@RestController
@RequestMapping("/manage/agent-config")
public class AgentConfigManageController {

    private static final String AGENT_PREFIX = "agent.";
    private static final int MIN_ROLE = 3;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private com.mu.transitmap.service.AmapClient amapClient;

    @Autowired
    private AgentEngineRouter engineRouter;

    @Autowired
    private HealthCheckScheduler healthCheckScheduler;

    /**
     * 拉取所有 agent.* 配置
     */
    @GetMapping("/all")
    public Result<List<SystemConfig>> all(HttpServletRequest request) {
        ensureRole(request);
        Map<String, List<SystemConfig>> grouped = systemConfigService.getAllConfigsGrouped();
        List<SystemConfig> agentConfigs = grouped.getOrDefault("agent", Collections.emptyList())
                .stream()
                .filter(c -> c.getConfigKey() != null && c.getConfigKey().startsWith(AGENT_PREFIX))
                .map(this::maskIfSecret)
                .collect(Collectors.toList());
        return Result.success(agentConfigs);
    }

    /**
     * 批量更新 agent.* 配置
     * - 任何非 agent.* 的项都会被拒绝
     * - secret 类型空值不覆盖
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody List<SystemConfig> configs,
                               HttpServletRequest request) {
        ensureRole(request);
        if (configs == null || configs.isEmpty()) {
            return Result.success(null);
        }
        for (SystemConfig c : configs) {
            if (c.getConfigKey() == null || !c.getConfigKey().startsWith(AGENT_PREFIX)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只允许修改 agent.* 前缀的配置");
            }
        }
        systemConfigService.updateConfigs(configs);
        return Result.success(null);
    }

    /**
     * 测试 LLM 连通性：调一次极简对话
     */
    @PostMapping("/test-llm")
    public Result<Map<String, Object>> testLlm(HttpServletRequest request) {
        ensureRole(request);
        Map<String, Object> result = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();
        try {
            String model = systemConfigService.getConfigValue("agent.llm.model");
            int timeoutMs = systemConfigService.getConfigInt("agent.llm.timeout_ms", 30000);
            LlmClient.LlmRequest req = new LlmClient.LlmRequest(
                    model,
                    "你是一个测试助手，只回复 OK。",
                    List.of(Map.of("role", "user", "content", "ping")),
                    0.1,
                    10,
                    Math.min(timeoutMs, 15000)
            );
            LlmClient.LlmReply reply = llmClient.complete(req);
            result.put("ok", true);
            result.put("model", model);
            result.put("latencyMs", System.currentTimeMillis() - t0);
            result.put("reply", reply.content());
            result.put("tokensIn", reply.usage().inputTokens());
            result.put("tokensOut", reply.usage().outputTokens());
            return Result.success(result);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
            result.put("latencyMs", System.currentTimeMillis() - t0);
            return Result.success(result);
        }
    }

    /**
     * 测试高德地图 API 连通性：跑多个变体定位真正的问题
     */
    @PostMapping("/test-amap")
    public Result<Map<String, Object>> testAmap(HttpServletRequest request) {
        ensureRole(request);
        Map<String, Object> result = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();

        // 1. 诊断信息：原始 DB 值（前缀）+ 解密后的 key 掩码
        Map<String, Object> diag = new LinkedHashMap<>();
        try {
            String stored = systemConfigService.getConfigValue("agent.map.api_key");
            diag.put("storedPrefix", stored == null ? "(null)"
                    : (stored.length() > 8 ? stored.substring(0, 8) + "..." : stored));
            diag.put("isEncrypted", stored != null && stored.startsWith("enc:"));
            String raw = systemConfigService.getRaw("agent.map.api_key");
            diag.put("decryptedKeyMask", raw == null ? "(空)"
                    : (raw.length() < 6 ? raw : raw.substring(0, 3) + "****" + raw.substring(raw.length() - 4)));
            diag.put("decryptedKeyLength", raw == null ? 0 : raw.length());
        } catch (Exception e) {
            diag.put("diagError", e.getMessage());
            result.put("ok", false);
            result.put("error", "读取/解密 api_key 失败：" + e.getMessage());
            result.put("latencyMs", System.currentTimeMillis() - t0);
            result.put("diag", diag);
            return Result.success(result);
        }
        result.put("diag", diag);

        // 2. 跑 3 个测试，逐一报告
        List<Map<String, Object>> probes = new ArrayList<>();
        Map<String, Object> firstOk = null;

        // 测试 A：geocode 不带 city
        probes.add(runProbe("geocode(无 city)", () -> {
            var loc = amapClient.geocode("北京西站", null);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("city", loc != null ? loc.getCity() : null);
            r.put("address", loc != null ? loc.getFormattedAddress() : null);
            return r;
        }));

        // 测试 B：geocode 带 city
        probes.add(runProbe("geocode(city=北京)", () -> {
            var loc = amapClient.geocode("北京西站", "北京");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("city", loc != null ? loc.getCity() : null);
            r.put("address", loc != null ? loc.getFormattedAddress() : null);
            return r;
        }));

        // 测试 C：IP 定位（更简单，没有 address 参数）
        probes.add(runProbe("ipLocate", () -> {
            var loc = amapClient.ipLocate("");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("city", loc != null ? loc.getCity() : null);
            return r;
        }));

        result.put("probes", probes);

        // 任何一个成功就算整体 OK
        boolean anyOk = probes.stream().anyMatch(p -> Boolean.TRUE.equals(p.get("ok")));
        result.put("ok", anyOk);
        result.put("latencyMs", System.currentTimeMillis() - t0);

        if (!anyOk) {
            // 收集所有 infocode
            Set<String> codes = new LinkedHashSet<>();
            for (Map<String, Object> p : probes) {
                Object ic = p.get("infocode");
                if (ic != null) codes.add(String.valueOf(ic));
            }
            result.put("error", "全部测试失败，infocode=" + codes
                    + "，请对照下方诊断建议");
            result.put("suggest", suggestForInfocode(codes));
        }

        return Result.success(result);
    }

    private Map<String, Object> runProbe(String name, ProbeFn fn) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        long t = System.currentTimeMillis();
        try {
            Object out = fn.run();
            p.put("ok", true);
            p.put("data", out);
        } catch (com.mu.transitmap.service.AmapClient.AmapException ae) {
            p.put("ok", false);
            p.put("status", ae.status);
            p.put("info", ae.info);
            p.put("infocode", ae.infocode);
            // 截断长响应
            String raw = ae.rawResponse == null ? "" : ae.rawResponse;
            p.put("rawResponse", raw.length() > 300 ? raw.substring(0, 300) + "..." : raw);
        } catch (Exception e) {
            p.put("ok", false);
            p.put("error", e.getMessage());
        }
        p.put("latencyMs", System.currentTimeMillis() - t);
        return p;
    }

    @FunctionalInterface
    private interface ProbeFn {
        Object run() throws Exception;
    }

    /**
     * 根据 infocode 给出更准确的诊断建议
     */
    private String suggestForInfocode(Set<String> codes) {
        if (codes.contains("10001")) {
            return "key 无效或被回收：请到高德开放平台确认 key 状态";
        }
        if (codes.contains("10009") || codes.contains("10010")) {
            return "key 类型与请求不匹配：必须使用「Web服务」类型 key，不能用「Web端(JS API)」类型";
        }
        if (codes.contains("10002")) {
            return "服务未授权：到高德开放平台「应用管理 → 我的应用」给该 key 添加「Web服务 API」服务";
        }
        if (codes.contains("10003") || codes.contains("10014")) {
            return "调用量超限：日免费 5000 次已用完，需等次日恢复或升级配额";
        }
        if (codes.contains("10005") || codes.contains("10006")) {
            return "IP/域名白名单限制：到高德控制台移除 IP/域名白名单，或加入服务器 IP";
        }
        if (codes.contains("30001")) {
            return "ENGINE_RESPONSE_DATA_ERROR：通常是 key 类型不对（必须是「Web服务」类型，"
                    + "不是 JS API/iOS/Android/小程序 类型）。请到高德开放平台 console.amap.com "
                    + "「应用管理」检查 key 类型，或新建一个「Web服务」类型的 key。";
        }
        return "未知错误，建议直接 curl 测试：https://restapi.amap.com/v3/geocode/geo?key=YOUR_KEY&address=北京";
    }

    /**
     * 获取引擎状态
     */
    @GetMapping("/engine-status")
    public Result<Map<String, Object>> engineStatus(HttpServletRequest request) {
        ensureRole(request);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeEngine", engineRouter.getActiveEngine());
        String engineConfig = systemConfigService.getConfigValue("agent.engine");
        data.put("configuredEngine", (engineConfig != null && !engineConfig.isEmpty()) ? engineConfig : "java");
        data.put("pythonHealthy", engineRouter.isPythonAvailable());
        data.put("pythonHealthDetails", healthCheckScheduler.getLastCheckDetails());
        return Result.success(data);
    }

    /**
     * 手动触发 Python 健康检查
     */
    @PostMapping("/check-python-health")
    public Result<Map<String, Object>> checkPythonHealth(HttpServletRequest request) {
        ensureRole(request);
        Map<String, Object> result = healthCheckScheduler.forceCheck();
        return Result.success(result);
    }

    // ===== 辅助 =====

    private void ensureRole(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode < MIN_ROLE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * secret 类型对外只显示掩码，不暴露密文/明文
     */
    private SystemConfig maskIfSecret(SystemConfig c) {
        if ("secret".equals(c.getConfigType())) {
            String mask = systemConfigService.getSecretMask(c.getConfigKey());
            // 不直接修改原对象：复制一份
            SystemConfig copy = new SystemConfig();
            copy.setId(c.getId());
            copy.setConfigKey(c.getConfigKey());
            copy.setConfigValue(mask);
            copy.setConfigType(c.getConfigType());
            copy.setConfigGroup(c.getConfigGroup());
            copy.setDescription(c.getDescription());
            copy.setIsPublic(c.getIsPublic());
            return copy;
        }
        return c;
    }
}
