package com.mu.transitmap.agent.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.AgentNode;
import com.mu.transitmap.service.LlmClient;
import com.mu.transitmap.service.RagService;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.vo.RoutePlanVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Node 5: 回复生成（LLM 流式输出）
 */
@Component
public class ReplyGenerateNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(ReplyGenerateNode.class);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private RagService ragService;

    /** Spring 配置的 ObjectMapper（Long→String），避免推送 routePlan 时前端 JS 精度丢失 */
    @Autowired
    private ObjectMapper mapper;

    @Override
    public void execute(AgentContext ctx, Consumer<Object> push) throws Exception {
        // 失败场景直接走确定性模板，禁止 LLM 自由发挥（避免它幻觉说"系统只有X号线"等错误信息）
        if (isFixedTemplateScenario(ctx)) {
            String fixed = buildFallbackReply(ctx);
            ctx.setAssistantReply(fixed);
            push.accept(Map.of("type", "delta", "text", fixed));
            List<String> chips = buildChips(ctx);
            ctx.setChips(chips);
            push.accept(Map.of("type", "chips", "items", chips));
            return;
        }

        String systemPrompt = buildSystemPrompt(ctx);
        List<Map<String, String>> messages = buildMessages(ctx);

        int maxTokens = configService.getConfigInt("agent.llm.max_tokens", 1024);
        int timeoutMs = configService.getConfigInt("agent.llm.timeout_ms", 30000);

        LlmClient.LlmRequest request = new LlmClient.LlmRequest(
                null, systemPrompt, messages, 0.3, maxTokens, timeoutMs
        );

        StringBuilder reply = new StringBuilder();

        try {
            llmClient.completeStream(request,
                    delta -> {
                        reply.append(delta);
                        push.accept(Map.of("type", "delta", "text", delta));
                    },
                    usage -> ctx.addTokens(usage.inputTokens(), usage.outputTokens())
            );
        } catch (Exception e) {
            log.error("ReplyGenerate stream failed", e);
            String fallback = buildFallbackReply(ctx);
            reply.append(fallback);
            push.accept(Map.of("type", "delta", "text", fallback));
        }

        ctx.setAssistantReply(reply.toString());

        // 推送路线卡片
        if (ctx.getRoutePlan() != null) {
            push.accept(Map.of("type", "card", "data",
                    Map.of("kind", "ROUTE_CARD", "payload", ctx.getRoutePlan())));
        }

        // 推送快捷词
        List<String> chips = buildChips(ctx);
        ctx.setChips(chips);
        push.accept(Map.of("type", "chips", "items", chips));
    }

    /**
     * 这些场景的回复完全由模板决定，不让 LLM 介入（防止编造）
     */
    private boolean isFixedTemplateScenario(AgentContext ctx) {
        String s = ctx.getScenario();
        if (s == null) return false;
        return "CROSS_CITY".equals(s)
                || "NO_METRO".equals(s)
                || "SAME_STATION".equals(s)
                || "NO_ROUTE".equals(s)
                || "START_NOT_FOUND".equals(s)
                || "END_NOT_FOUND".equals(s)
                || "NO_STATIONS_FOUND".equals(s);
    }

    private String buildSystemPrompt(AgentContext ctx) {
        String template = configService.getConfigValue("agent.prompt.system");
        if (template == null) {
            template = "你是 TransitMap 的城内地铁路线助手。帮助用户规划地铁路线。";
        }

        template = template.replace("{{userLogged}}", ctx.getUserId() != null ? "是" : "否");
        template = template.replace("{{selectedCity}}",
                ctx.getFromCityName() != null ? ctx.getFromCityName() : "未确定");

        String routeSummary = "无";
        if (ctx.getRoutePlan() != null) {
            RoutePlanVO plan = ctx.getRoutePlan();
            routeSummary = String.format("从 %s 到 %s，共 %d 站，约 %d 分钟，%d 元",
                    plan.getStartStationName(), plan.getEndStationName(),
                    plan.getStationCount(), plan.getDurationMinutes(), plan.getPrice());
        }
        template = template.replace("{{routePlanSummary}}", routeSummary);

        return template;
    }

    private List<Map<String, String>> buildMessages(AgentContext ctx) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (ctx.getContextMessages() != null) {
            messages.addAll(ctx.getContextMessages());
        }

        // RAG 召回：拼到 system message 里
        List<Long> stationIds = ctx.getRoutePlan() != null ? ctx.getRoutePlan().getStationIds() : null;
        String rag = ragService.retrieveForContext(ctx.getUserMessage(), ctx.getFromCityId(), stationIds);
        if (rag != null && !rag.isEmpty()) {
            messages.add(Map.of("role", "system", "content", rag));
        }

        // 用 USER_INPUT 包裹，防 prompt 注入（设计 §9）
        String wrapped = "[USER_INPUT]" + safeUserInput(ctx.getUserMessage()) + "[/USER_INPUT]";
        messages.add(Map.of("role", "user", "content", wrapped));

        // 如果有路线数据，注入为系统消息（含接驳距离 → 让 LLM 提示用户先走/打车到地铁站）
        if (ctx.getRoutePlan() != null) {
            try {
                String routeJson = mapper.writeValueAsString(ctx.getRoutePlan());
                StringBuilder sys = new StringBuilder("【系统路线数据】").append(routeJson)
                        .append("\n请基于以上数据回复用户，不要编造其他数据。");
                String legHint = buildLegHint(ctx);
                if (!legHint.isEmpty()) {
                    sys.append("\n\n【起终点接驳信息（请在回复里友好地体现，让用户知道要先走/骑车/打车到地铁站）】")
                       .append(legHint);
                }
                messages.add(Map.of("role", "system", "content", sys.toString()));
            } catch (Exception ignored) {}
        }

        return messages;
    }

    /**
     * 根据接驳距离生成提示，给 LLM 参考
     * 距离阈值：
     *   < 0.5km：忽略（视为站点门口）
     *   0.5~1.5km：建议步行
     *   1.5~3km：建议骑共享单车
     *   3~5km：建议打车 / 公交
     *   > 5km：明确警告该位置不便利
     */
    private String buildLegHint(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        appendLegHint(sb, "起点", ctx.getFromDisplayName(),
                ctx.getRoutePlan() != null ? ctx.getRoutePlan().getStartStationName() : null,
                ctx.getFromStationDistKm(), true);
        appendLegHint(sb, "终点", ctx.getToDisplayName(),
                ctx.getRoutePlan() != null ? ctx.getRoutePlan().getEndStationName() : null,
                ctx.getToStationDistKm(), false);
        return sb.toString();
    }

    private void appendLegHint(StringBuilder sb, String label, String poi, String station,
                                Double distKm, boolean toStation) {
        if (distKm == null || station == null) return;
        if (distKm < 0.5) return;
        String dir = toStation ? "到地铁" + station : "从地铁" + station + "到" + (poi == null ? "目的地" : poi);
        String poiDesc = poi != null ? "「" + poi + "」" : "";
        String advice;
        if (distKm < 1.5) {
            advice = "建议步行（约 " + Math.round(distKm * 12) + " 分钟）";
        } else if (distKm < 3.0) {
            advice = "建议骑共享单车（约 " + Math.round(distKm * 4) + " 分钟）";
        } else if (distKm < 5.0) {
            advice = "建议打车或公交（约 " + Math.round(distKm * 3) + " 分钟）";
        } else {
            advice = "距离较远，建议打车直达可能比换乘地铁更省时";
        }
        sb.append("\n- ").append(label).append(poiDesc).append(" 距 ").append(station).append("站 ")
                .append(String.format("%.1f", distKm)).append("km，").append(advice).append("（")
                .append(dir).append("）");
    }

    private String safeUserInput(String s) {
        if (s == null) return "";
        return s.replace("[USER_INPUT]", "(USER_INPUT)")
                .replace("[/USER_INPUT]", "(/USER_INPUT)");
    }

    private String buildFallbackReply(AgentContext ctx) {
        if ("MISSING_DEST".equals(ctx.getScenario())) {
            return configService.getConfigValue("agent.prompt.missing_destination");
        }
        if ("CROSS_CITY".equals(ctx.getScenario())) {
            String from = ctx.getFromCityName() != null ? ctx.getFromCityName() : "出发城市";
            String to = ctx.getToCityName() != null ? ctx.getToCityName() : "目的城市";
            return String.format("你这是从 %s 到 %s，本系统只支持单城内地铁规划。想看哪一段？", from, to);
        }
        if ("NO_METRO".equals(ctx.getScenario())) {
            String nearest = ctx.getNearestSupportedCityName();
            String unknownCity = pickUnknownCityName(ctx);
            if (nearest != null && !nearest.isEmpty()) {
                return "抱歉，" + (unknownCity != null ? unknownCity : "该城市")
                        + " 暂未接入本系统。\n离你最近的已开通城市是 " + nearest + "。\n"
                        + "如果你希望尽快接入这座城市，可以一键通知管理员。";
            }
            return "抱歉，" + (unknownCity != null ? unknownCity : "该城市")
                    + " 暂未接入本系统。\n如果你希望尽快接入这座城市，可以一键通知管理员。";
        }
        if ("SAME_STATION".equals(ctx.getScenario())) {
            return "出发站和目的站是同一个站，不需要乘车哦 🚇";
        }
        if ("START_NOT_FOUND".equals(ctx.getScenario())) {
            String from = ctx.getSlotFrom() != null ? "「" + ctx.getSlotFrom() + "」" : "你说的出发地";
            String cityHint = ctx.getFromCityName() != null
                    ? "（" + ctx.getFromCityName() + " 地铁系统中没有匹配的站点）" : "";
            return "抱歉，我没找到 " + from + " 对应的地铁站" + cityHint + "。\n"
                    + "可以试试：用更标准的站名（如「东莞西」「北京西站」），或直接告诉我离你最近的地铁站名。";
        }
        if ("END_NOT_FOUND".equals(ctx.getScenario())) {
            String to = ctx.getSlotTo() != null ? "「" + ctx.getSlotTo() + "」" : "你说的目的地";
            String cityHint = ctx.getFromCityName() != null
                    ? "（" + ctx.getFromCityName() + " 地铁系统中没有匹配的站点）" : "";
            return "抱歉，我没找到 " + to + " 对应的地铁站" + cityHint + "。\n"
                    + "可以试试：换个更标准的站名，或先告诉我你目的地最近的地铁站。";
        }
        if ("NO_STATIONS_FOUND".equals(ctx.getScenario())) {
            return "抱歉，我没在 " + (ctx.getFromCityName() != null ? ctx.getFromCityName() : "当前城市")
                    + " 的地铁系统里找到你说的两个地点。请用更标准的站名再问一次（例如「东莞西 到 东莞火车站」）。";
        }
        if ("NO_ROUTE".equals(ctx.getScenario())) {
            // 起终站都找到了但 BFS 不连通 —— 罕见，多半是数据问题
            return "起点和终点我都找到了，但当前数据库里这两站之间没有直接连通的地铁线路，\n"
                    + "可能是新开通的线路尚未录入。你可以通知管理员补充。";
        }
        if (ctx.getRoutePlan() != null) {
            RoutePlanVO plan = ctx.getRoutePlan();
            return String.format("已为你规划路线 🚇\n从 %s 到 %s，共 %d 站，约 %d 分钟，票价 %d 元。",
                    plan.getStartStationName(), plan.getEndStationName(),
                    plan.getStationCount(), plan.getDurationMinutes(), plan.getPrice());
        }
        return "抱歉，我暂时无法规划路线，请稍后再试。";
    }

    private List<String> buildChips(AgentContext ctx) {
        if ("MISSING_DEST".equals(ctx.getScenario())) {
            return List.of("北京站", "人民广场", "天安门");
        }
        if ("CROSS_CITY".equals(ctx.getScenario())) {
            String from = ctx.getFromCityName() != null ? ctx.getFromCityName() : "出发城市";
            String to = ctx.getToCityName() != null ? ctx.getToCityName() : "目的城市";
            return List.of(from + " 站内换乘", to + " 站内换乘", "重新告诉我");
        }
        if ("START_NOT_FOUND".equals(ctx.getScenario()) || "END_NOT_FOUND".equals(ctx.getScenario())
                || "NO_STATIONS_FOUND".equals(ctx.getScenario())) {
            return List.of("换个站名重新告诉我", "算了，结束对话");
        }
        if ("NO_ROUTE".equals(ctx.getScenario())) {
            String unknownCity = ctx.getFromCityName() != null ? ctx.getFromCityName() : "该城市";
            return List.of(
                    "::cmd:notify_admin:" + unknownCity + "|通知管理员补充线路",
                    "重新告诉我",
                    "算了，结束对话"
            );
        }
        if ("SAME_STATION".equals(ctx.getScenario())) {
            return List.of("重新告诉我", "算了，结束对话");
        }
        if ("NO_METRO".equals(ctx.getScenario())) {
            String nearest = ctx.getNearestSupportedCityName();
            String unknownCity = pickUnknownCityName(ctx);
            List<String> chips = new java.util.ArrayList<>();
            chips.add("::cmd:notify_admin:" + (unknownCity != null ? unknownCity : "未知城市") + "|通知管理员添加");
            if (nearest != null && !nearest.isEmpty()) {
                chips.add("我要去 " + nearest);
            }
            chips.add("算了，结束对话");
            return chips;
        }
        if (ctx.getRoutePlan() != null) {
            return List.of("是的，帮我下单", "换个方案", "为什么这样换");
        }
        return List.of("我要去...", "附近地铁", "换乘建议");
    }

    /**
     * 从 fromGeo / toGeo 中挑出未匹配到的城市名（用户提到的、但 DB 没有的）
     */
    private String pickUnknownCityName(AgentContext ctx) {
        if (ctx.getToCityId() == null && ctx.getToGeo() != null) {
            return ctx.getToGeo().getCity();
        }
        if (ctx.getFromCityId() == null && ctx.getFromGeo() != null) {
            return ctx.getFromGeo().getCity();
        }
        return null;
    }
}
