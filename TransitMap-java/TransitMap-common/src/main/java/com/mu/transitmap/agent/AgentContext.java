package com.mu.transitmap.agent;

import com.mu.transitmap.entity.ChatMessage;
import com.mu.transitmap.vo.LocationVO;
import com.mu.transitmap.vo.RoutePlanVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行上下文：贯穿整个 pipeline
 */
@Data
@Builder
public class AgentContext {

    // WebSocket 信息
    private String wsSessionId;
    private Long userId;
    private String anonToken;
    private Long chatSessionId;
    private String clientIp;

    // 用户输入
    private String userMessage;
    private double lat;
    private double lng;
    private String inputMethod;

    // 重新生成
    private Long regenerateMessageId;

    // 已保存的 user 消息 ID（节点日志关联用）
    private Long userMessageId;

    // 本轮总耗时（毫秒）
    @Builder.Default
    private int latencyMs = 0;

    // Node 1: 意图 & 槽位
    private String intent;
    private String slotFrom;
    private String slotTo;
    // LLM 从用户消息推断出的城市（如「滕王阁」→南昌、「鸟巢」→北京），优先级最高
    private String llmInferredCity;

    // Node 2: 位置解析
    private LocationVO fromGeo;
    private LocationVO toGeo;

    // Node 3: 城市匹配
    private Long fromCityId;
    private String fromCityName;
    private Long toCityId;
    private String toCityName;
    private String scenario; // SAME_CITY, CROSS_CITY, NO_METRO, MISSING_DEST, NO_ROUTE, SAME_STATION
    // NO_METRO 场景下：最近一座已开通城市的名称（用于引导用户）
    private String nearestSupportedCityName;
    // NO_METRO 场景下：用户查询的未知城市名
    private String unknownCityName;

    // Node 4: 路径规划
    private RoutePlanVO routePlan;
    // 下单结果（用于持久化 ORDER_CARD）
    private Map<String, Object> orderCard;
    // 起点 POI/坐标 到 起点地铁站的步行距离（km），用于「先走到地铁站」接驳建议
    private Double fromStationDistKm;
    // 终点地铁站 到 终点 POI/坐标 的步行距离（km）
    private Double toStationDistKm;
    // 起点 POI 的友好显示名（如「万达广场」），来自 slot 或 geo.formattedAddress
    private String fromDisplayName;
    private String toDisplayName;

    // Node 5: 回复生成
    private String assistantReply;
    private List<String> chips;

    // Token 统计
    @Builder.Default
    private int tokensIn = 0;
    @Builder.Default
    private int tokensOut = 0;

    // 流程控制
    @Builder.Default
    private boolean shortCircuit = false;
    @Builder.Default
    private String runId = UUID.randomUUID().toString();

    // 上下文消息
    private List<Map<String, String>> contextMessages;

    public void addTokens(int in, int out) {
        this.tokensIn += in;
        this.tokensOut += out;
    }

    public boolean shouldShortCircuit() {
        return shortCircuit;
    }

    /**
     * 转换为 ChatMessage 实体（用于持久化 assistant 回复）
     */
    public ChatMessage toAssistantMessage() {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(chatSessionId);
        msg.setRole("assistant");
        msg.setContent(assistantReply);
        msg.setTokensIn(tokensIn);
        msg.setTokensOut(tokensOut);
        return msg;
    }
}
