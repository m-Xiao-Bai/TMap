package com.mu.transitmap.service;

import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.LangChain4jAgentEngine;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 引擎路由器
 *
 * 根据配置和健康状态自动选择 Java 或 Python Agent：
 * - agent.engine = "java" → 始终使用 Java Agent
 * - agent.engine = "python" + Python 健康 → 使用 Python Agent
 * - agent.engine = "python" + Python 不健康 → 自动降级到 Java Agent
 */
@Service
public class AgentEngineRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentEngineRouter.class);

    @Autowired
    private LangChain4jAgentEngine javaAgent;

    @Autowired
    private PythonAgentClient pythonClient;

    @Autowired
    private HealthCheckScheduler healthCheck;

    @Autowired
    private SystemConfigServiceImpl configService;

    /**
     * 异步执行 Agent 对话
     *
     * @return runId
     */
    public String runAsync(AgentContext ctx, Consumer<Object> push) {
        String engine = getConfigWithDefault("agent.engine", "java");

        if ("python".equalsIgnoreCase(engine)) {
            if (healthCheck.isPythonHealthy()) {
                log.info("使用 Python Agent 处理请求");
                return pythonClient.streamChat(ctx, push);
            } else {
                // 自动降级
                log.warn("Python Agent 不可用，自动降级到 Java Agent");
                push.accept(Map.of(
                        "type", "status",
                        "text", "系统繁忙，已切换到备用引擎"
                ));
                return javaAgent.runAsync(ctx, push);
            }
        }

        // 默认使用 Java Agent
        log.debug("使用 Java Agent 处理请求");
        return javaAgent.runAsync(ctx, push);
    }

    /**
     * 重新生成回复
     */
    public String regenerate(AgentContext ctx, Consumer<Object> push) {
        String engine = getConfigWithDefault("agent.engine", "java");

        if ("python".equalsIgnoreCase(engine) && healthCheck.isPythonHealthy()) {
            log.info("使用 Python Agent 重新生成");
            return pythonClient.streamChat(ctx, push);
        }

        return javaAgent.regenerate(ctx, push);
    }

    /**
     * 取消运行中的任务
     */
    public void cancel(String runId) {
        // 两个引擎都尝试取消
        try {
            javaAgent.cancel(runId);
        } catch (Exception ignored) {}
    }

    /**
     * 获取当前使用的引擎名称
     */
    public String getActiveEngine() {
        String configured = getConfigWithDefault("agent.engine", "java");
        if ("python".equalsIgnoreCase(configured)) {
            return healthCheck.isPythonHealthy() ? "python" : "java (fallback)";
        }
        return "java";
    }

    /**
     * 检查 Python Agent 是否可用
     */
    public boolean isPythonAvailable() {
        return healthCheck.isPythonHealthy();
    }

    private String getConfigWithDefault(String key, String defaultValue) {
        String val = configService.getConfigValue(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }
}
