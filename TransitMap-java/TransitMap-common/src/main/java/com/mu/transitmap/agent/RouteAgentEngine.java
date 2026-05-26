package com.mu.transitmap.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.agent.node.*;
import com.mu.transitmap.entity.AgentTokenUsage;
import com.mu.transitmap.entity.ChatIntentLog;
import com.mu.transitmap.entity.ChatMessage;
import com.mu.transitmap.entity.ChatSession;
import com.mu.transitmap.mapper.AgentTokenUsageMapper;
import com.mu.transitmap.mapper.ChatIntentLogMapper;
import com.mu.transitmap.mapper.ChatMessageMapper;
import com.mu.transitmap.mapper.ChatSessionMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Agent 引擎核心：编排 5 个节点的执行流程
 */
@Service
public class RouteAgentEngine {

    private static final Logger log = LoggerFactory.getLogger(RouteAgentEngine.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IntentExtractNode intentExtractNode;
    @Autowired
    private ResolveLocationNode resolveLocationNode;
    @Autowired
    private MatchCityNode matchCityNode;
    @Autowired
    private PathPlanNode pathPlanNode;
    @Autowired
    private ReplyGenerateNode replyGenerateNode;

    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatIntentLogMapper chatIntentLogMapper;
    @Autowired
    private AgentTokenUsageMapper agentTokenUsageMapper;
    @Autowired
    private SystemConfigServiceImpl configService;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-worker");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<String, Future<?>> running = new ConcurrentHashMap<>();

    /**
     * 异步执行 Agent pipeline
     */
    public String runAsync(AgentContext ctx, Consumer<Object> push) {
        // 加载历史上下文（最近 N 条）
        loadContextMessages(ctx);

        // 保存用户消息
        Long userMsgId = saveUserMessage(ctx);
        ctx.setUserMessageId(userMsgId);

        Future<?> future = executor.submit(() -> {
            // MDC：把 traceId 塞到日志上下文，便于全链路排查
            MDC.put("traceId", ctx.getRunId());
            MDC.put("sessionId", String.valueOf(ctx.getChatSessionId()));
            long startMs = System.currentTimeMillis();
            try {
                runPipeline(ctx, push);
                ctx.setLatencyMs((int) (System.currentTimeMillis() - startMs));
                ChatMessage saved = saveAssistantMessage(ctx);
                upsertTokenUsage(ctx);

                push.accept(Map.of("type", "done", "messageId",
                        saved != null ? saved.getId() : 0,
                        "tokensIn", ctx.getTokensIn(),
                        "tokensOut", ctx.getTokensOut()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Agent pipeline error", e);
                push.accept(Map.of("type", "error", "code", "INTERNAL", "message", "处理出错，请稍后再试"));
            } finally {
                running.remove(ctx.getRunId());
                MDC.remove("traceId");
                MDC.remove("sessionId");
            }
        });

        running.put(ctx.getRunId(), future);
        return ctx.getRunId();
    }

    /**
     * 重新生成上一条回复：读取目标 messageId 之前最后一条 user 消息重跑
     */
    public String regenerate(AgentContext ctx, Consumer<Object> push) {
        ChatMessage assistantMsg = chatMessageMapper.selectById(ctx.getRegenerateMessageId());
        if (assistantMsg == null) {
            push.accept(Map.of("type", "error", "code", "NOT_FOUND", "message", "目标消息不存在"));
            push.accept(Map.of("type", "done", "messageId", 0, "tokensIn", 0, "tokensOut", 0));
            return ctx.getRunId();
        }

        // 找该 assistant 消息之前最近的一条 user 消息
        ChatMessage userMsg = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, assistantMsg.getSessionId())
                .eq(ChatMessage::getRole, "user")
                .lt(ChatMessage::getCreateTime, assistantMsg.getCreateTime())
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT 1"));

        if (userMsg == null) {
            push.accept(Map.of("type", "error", "code", "NOT_FOUND", "message", "找不到对应的用户消息"));
            push.accept(Map.of("type", "done", "messageId", 0, "tokensIn", 0, "tokensOut", 0));
            return ctx.getRunId();
        }

        ctx.setUserMessage(userMsg.getContent());
        ctx.setInputMethod(userMsg.getInputMethod());
        return runAsync(ctx, push);
    }

    /**
     * 取消正在执行的任务
     */
    public void cancel(String runId) {
        Future<?> future = running.remove(runId);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void runPipeline(AgentContext ctx, Consumer<Object> push) throws Exception {
        AgentNode[] nodes = {
                intentExtractNode, resolveLocationNode, matchCityNode, pathPlanNode, replyGenerateNode
        };

        for (AgentNode node : nodes) {
            long t0 = System.currentTimeMillis();
            try {
                node.execute(ctx, push);
                logNode(ctx, node, t0, true, null);
                if (ctx.shouldShortCircuit()) {
                    // 短路时如果还没有回复，让 ReplyGenerateNode 走降级模板
                    if (ctx.getAssistantReply() == null || ctx.getAssistantReply().isEmpty()) {
                        if (node != replyGenerateNode) {
                            long t1 = System.currentTimeMillis();
                            try {
                                replyGenerateNode.execute(ctx, push);
                                logNode(ctx, replyGenerateNode, t1, true, null);
                            } catch (Exception e) {
                                logNode(ctx, replyGenerateNode, t1, false, e.getMessage());
                                String fallback = "抱歉，暂时无法处理你的请求。";
                                ctx.setAssistantReply(fallback);
                                push.accept(Map.of("type", "delta", "text", fallback));
                            }
                        }
                    }
                    break;
                }
            } catch (Exception e) {
                logNode(ctx, node, t0, false, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * 加载历史上下文（最近 N 条 user/assistant 消息），写入 ctx.contextMessages
     */
    private void loadContextMessages(AgentContext ctx) {
        if (ctx.getChatSessionId() == null) return;
        int maxMsgs = configService.getConfigInt("agent.history.max_context_msgs", 10);
        if (maxMsgs <= 0) return;
        try {
            List<ChatMessage> recent = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, ctx.getChatSessionId())
                    .in(ChatMessage::getRole, "user", "assistant")
                    .orderByDesc(ChatMessage::getCreateTime)
                    .last("LIMIT " + maxMsgs));
            Collections.reverse(recent);
            List<Map<String, String>> ctxMsgs = new ArrayList<>(recent.size());
            for (ChatMessage m : recent) {
                if (m.getContent() == null) continue;
                ctxMsgs.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            ctx.setContextMessages(ctxMsgs);
        } catch (Exception e) {
            log.warn("Failed to load context messages", e);
        }
    }

    private Long saveUserMessage(AgentContext ctx) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(ctx.getChatSessionId());
            msg.setRole("user");
            msg.setContent(ctx.getUserMessage());
            msg.setInputMethod(ctx.getInputMethod() != null ? ctx.getInputMethod() : "text");
            chatMessageMapper.insert(msg);
            updateSessionAfterMessage(ctx, msg.getContent());
            return msg.getId();
        } catch (Exception e) {
            log.warn("Failed to save user message", e);
            return null;
        }
    }

    private ChatMessage saveAssistantMessage(AgentContext ctx) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(ctx.getChatSessionId());
            msg.setRole("assistant");
            msg.setContent(ctx.getAssistantReply() != null ? ctx.getAssistantReply() : "");
            msg.setIntent(ctx.getIntent());
            msg.setInputMethod("text");
            msg.setTokensIn(ctx.getTokensIn());
            msg.setTokensOut(ctx.getTokensOut());
            msg.setLatencyMs(ctx.getLatencyMs());
            msg.setLlmModel(configService.getConfigValue("agent.llm.model"));

            // extras：路线卡片 + chips 一并存
            Map<String, Object> extras = new LinkedHashMap<>();
            if (ctx.getRoutePlan() != null) {
                extras.put("kind", "ROUTE_CARD");
                extras.put("payload", ctx.getRoutePlan());
            }
            if (ctx.getChips() != null && !ctx.getChips().isEmpty()) {
                extras.put("chips", ctx.getChips());
            }
            if (ctx.getScenario() != null) {
                extras.put("scenario", ctx.getScenario());
            }
            if (!extras.isEmpty()) {
                msg.setExtras(objectMapper.writeValueAsString(extras));
            }

            chatMessageMapper.insert(msg);
            updateSessionAfterMessage(ctx, null);
            return msg;
        } catch (Exception e) {
            log.warn("Failed to save assistant message", e);
            return null;
        }
    }

    private void updateSessionAfterMessage(AgentContext ctx, String maybeUserContent) {
        try {
            ChatSession session = chatSessionMapper.selectById(ctx.getChatSessionId());
            if (session == null) return;
            session.setMsgCount((session.getMsgCount() == null ? 0 : session.getMsgCount()) + 1);
            session.setLastMsgAt(LocalDateTime.now());
            if (maybeUserContent != null
                    && (session.getTitle() == null || "新对话".equals(session.getTitle()))) {
                String title = maybeUserContent.length() > 30
                        ? maybeUserContent.substring(0, 30) + "..." : maybeUserContent;
                session.setTitle(title);
            }
            chatSessionMapper.updateById(session);
        } catch (Exception e) {
            log.warn("Failed to update session", e);
        }
    }

    /**
     * 写入/累加当日的 token 用量统计
     */
    private void upsertTokenUsage(AgentContext ctx) {
        try {
            if (ctx.getTokensIn() == 0 && ctx.getTokensOut() == 0) return;

            String model = configService.getConfigValue("agent.llm.model");
            if (model == null || model.isEmpty()) model = "unknown";
            LocalDate today = LocalDate.now();
            Long userId = ctx.getUserId();

            int costIn = configService.getConfigInt("agent.llm.cost_per_1k_input_cents", 1);
            int costOut = configService.getConfigInt("agent.llm.cost_per_1k_output_cents", 2);
            int costCents = (int) Math.ceil(
                    ctx.getTokensIn() * costIn / 1000.0 + ctx.getTokensOut() * costOut / 1000.0);

            LambdaQueryWrapper<AgentTokenUsage> wrapper = new LambdaQueryWrapper<AgentTokenUsage>()
                    .eq(AgentTokenUsage::getStatDate, today)
                    .eq(AgentTokenUsage::getLlmModel, model);
            if (userId != null) {
                wrapper.eq(AgentTokenUsage::getUserId, userId);
            } else {
                wrapper.isNull(AgentTokenUsage::getUserId);
            }
            AgentTokenUsage existing = agentTokenUsageMapper.selectOne(wrapper);
            if (existing == null) {
                AgentTokenUsage row = new AgentTokenUsage();
                row.setStatDate(today);
                row.setUserId(userId);
                row.setLlmModel(model);
                row.setRequestCount(1);
                row.setTokensIn((long) ctx.getTokensIn());
                row.setTokensOut((long) ctx.getTokensOut());
                row.setCostCents(costCents);
                agentTokenUsageMapper.insert(row);
            } else {
                existing.setRequestCount(existing.getRequestCount() + 1);
                existing.setTokensIn(existing.getTokensIn() + ctx.getTokensIn());
                existing.setTokensOut(existing.getTokensOut() + ctx.getTokensOut());
                existing.setCostCents(existing.getCostCents() + costCents);
                agentTokenUsageMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("Failed to upsert token usage", e);
        }
    }

    private void logNode(AgentContext ctx, AgentNode node, long t0, boolean success, String errorMsg) {
        try {
            ChatIntentLog logEntry = new ChatIntentLog();
            logEntry.setSessionId(ctx.getChatSessionId());
            logEntry.setMessageId(ctx.getUserMessageId() != null ? ctx.getUserMessageId() : 0L);
            logEntry.setNodeName(node.name());
            logEntry.setSuccess(success ? 1 : 0);
            logEntry.setErrorMsg(errorMsg);
            logEntry.setLatencyMs((int) (System.currentTimeMillis() - t0));
            logEntry.setTraceId(ctx.getRunId());
            chatIntentLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to log node execution", e);
        }
    }
}
