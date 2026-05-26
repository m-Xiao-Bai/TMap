package com.mu.transitmap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM 位置消歧服务（已适配 LangChain4j）
 *
 * 用法：在确定性算法（站名匹配 + geocode + TopN 候选）全部失败时调用
 * 让 LLM 把方言/俗称/模糊描述翻译成 1~3 个标准 POI 名，再走一次匹配流程
 */
@Service
public class LlmLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmLocationResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_STR = new TypeReference<List<String>>() {};

    @Lazy
    @Autowired(required = false)
    private ChatModel chatLanguageModel;

    @Autowired
    private SystemConfigServiceImpl configService;

    /**
     * 让 LLM 把模糊地点翻译为 1~3 个标准 POI 名
     * 失败时返回空列表（不抛错，调用方继续走兜底）
     */
    public List<String> suggestStandardNames(String userInput, String cityName) {
        if (configService.getConfigInt("agent.location.llm_resolve_enabled", 1) != 1) {
            return Collections.emptyList();
        }
        if (userInput == null || userInput.isBlank()) return Collections.emptyList();

        String system = "你是地理消歧助手。用户说的地名可能是俗称/方言/简称/描述（如『小蛮腰』『市中心』）。"
                + "请把它翻译为 1~3 个最可能的标准 POI 名（如『广州塔』）或标准地铁站名。\n"
                + "严格要求：\n"
                + "1. 只输出 JSON 数组，不要任何其他文字，格式：[\"标准名1\", \"标准名2\"]\n"
                + "2. 数组项是地名字符串，不带城市前缀（"
                + (cityName != null ? "城市已知是「" + cityName + "」，不要再加城市名" : "城市未知")
                + "）\n"
                + "3. 如果用户说的就是一个常见标准名，直接返回它本身\n"
                + "4. 如果完全不知道，返回 []\n"
                + "5. 不要编造不存在的地名";

        String user = "用户输入：" + userInput
                + (cityName != null ? "\n所在城市：" + cityName : "")
                + "\n请输出 JSON 数组。";

        return callLlmForJsonArray(system, user, "LLM 消歧 '" + userInput + "'");
    }

    /**
     * 直接询问 LLM：给定地点和城市，最近的 1~3 个地铁站是哪些？
     */
    public List<String> suggestNearestStations(String userInput, String cityName) {
        if (configService.getConfigInt("agent.location.llm_first_enabled", 1) != 1) {
            return Collections.emptyList();
        }
        if (userInput == null || userInput.isBlank()) return Collections.emptyList();

        String system = "你是城市地铁助手。用户给出一个地点（可能是地铁站名、POI、地标、商圈、大学、俗称等）。\n"
                + "请告诉我【离这个地点最近的 1~3 个地铁站名】"
                + (cityName != null ? "（城市：" + cityName + "）" : "")
                + "。\n"
                + "严格要求：\n"
                + "1. 只输出 JSON 数组，不要任何其他文字：[\"站名1\", \"站名2\", \"站名3\"]\n"
                + "2. 数组项是地铁站名，不带城市前缀、不带'地铁站'后缀\n"
                + "3. 站名要尽量是当地真实存在的地铁站名（如「天安门东」「广州塔」「滕王阁」），不要编造\n"
                + "4. 如果用户说的本身就是地铁站名，把它放第一个\n"
                + "5. 按距离从近到远排序\n"
                + "6. 如果你不熟悉这座城市的地铁，返回 []\n"
                + "7. 不要解释、不要带任何描述文字";

        String user = "地点：" + userInput
                + (cityName != null ? "\n城市：" + cityName : "")
                + "\n请输出最近 1~3 个地铁站名的 JSON 数组。";

        return callLlmForJsonArray(system, user, "LLM 询问最近站 '" + userInput + "'");
    }

    /**
     * 【RAG 增强】给定一个真实存在的站点名列表，让 LLM 从中挑选 1~3 个最近的
     *
     * 优势：LLM 不会幻觉，因为它的回答被严格限制在我们提供的列表里
     */
    public List<String> pickNearestFromStationList(String userInput, String cityName,
                                                    List<String> availableStations) {
        if (configService.getConfigInt("agent.location.llm_first_enabled", 1) != 1) {
            return Collections.emptyList();
        }
        if (userInput == null || userInput.isBlank()) return Collections.emptyList();
        if (availableStations == null || availableStations.isEmpty()) return Collections.emptyList();

        // 站名以英文逗号分隔（节省 token，比换行紧凑）
        String stationsHint = String.join(",", availableStations);

        // token 防护：列表过长时截断（保留前 300 个站名，约 1200 token，足够大城市覆盖）
        if (availableStations.size() > 300) {
            stationsHint = String.join(",", availableStations.subList(0, 300)) + ",...";
        }

        String system = "你是城市地铁专家。用户给出一个地点（POI、地标、商圈、大学、俗称、地铁站名等），"
                + "你需要从【本城真实存在的地铁站列表】中挑选 1~3 个离该地点【物理距离最近】的站。\n"
                + "严格要求：\n"
                + "1. 只输出 JSON 数组，不要任何其他文字：[\"站名1\", \"站名2\"]\n"
                + "2. 数组项必须严格从下面列表里选，不能编造、不能改写、不能加'站'后缀\n"
                + "3. 按距离从近到远排序，最多 3 个\n"
                + "4. 如果用户说的本身就是列表中的站名，把它放第一个\n"
                + "5. 如果你完全不知道用户说的地点在哪，仍要返回最可能的 1~2 个候选（绝不返回空数组，除非用户输入是空）\n"
                + "6. 不要解释、不要带任何描述文字\n\n"
                + "【本城所有真实地铁站列表（必须从此挑选）】：\n"
                + stationsHint;

        String user = "城市：" + (cityName != null ? cityName : "未知")
                + "\n地点：" + userInput
                + "\n请从上面的站点列表中挑选 1~3 个离该地点最近的站，输出 JSON 数组。";

        return callLlmForJsonArray(system, user,
                "LLM 从站列表挑选 '" + userInput + "' (候选 " + availableStations.size() + " 个)");
    }

    /**
     * 通用：调一次 LLM 取一个 JSON 字符串数组（使用 LangChain4j ChatLanguageModel）
     */
    private List<String> callLlmForJsonArray(String system, String user, String tag) {
        if (chatLanguageModel == null) {
            log.warn("ChatModel 未配置，跳过 LLM 调用: {}", tag);
            return Collections.emptyList();
        }
        try {
            // 使用 LangChain4j 的消息格式
            SystemMessage sysMsg = SystemMessage.from(system);
            UserMessage userMsg = UserMessage.from(user);

            ChatResponse response = chatLanguageModel.chat(sysMsg, userMsg);
            String text = response.aiMessage().text();

            String json = extractJsonArray(text);
            if (json == null) return Collections.emptyList();
            List<String> arr = MAPPER.readValue(json, LIST_STR);
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String s : arr) {
                if (s == null) continue;
                String t = s.trim();
                if (t.isEmpty() || t.length() > 50) continue;
                seen.add(t);
            }
            List<String> out = new ArrayList<>(seen);
            log.info("{} → {}", tag, out);
            return out.subList(0, Math.min(3, out.size()));
        } catch (Exception e) {
            log.warn("{} 失败: {}", tag, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 LLM 输出中提取 JSON 数组 [...]
     */
    private String extractJsonArray(String text) {
        if (text == null) return null;
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }
}
