package com.mu.transitmap.agent;

import java.util.function.Consumer;

/**
 * Agent 节点接口
 */
public interface AgentNode {

    /**
     * 执行节点逻辑
     *
     * @param ctx  执行上下文
     * @param push 消息推送回调（发送 delta/card/chips/error 等给前端）
     */
    void execute(AgentContext ctx, Consumer<Object> push) throws Exception;

    /**
     * 节点名称（用于日志和 tracing）
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
