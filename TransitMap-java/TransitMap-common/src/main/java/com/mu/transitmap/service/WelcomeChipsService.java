package com.mu.transitmap.service;

import com.mu.transitmap.mapper.ChatMessageMapper;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 欢迎快捷词融合服务
 * 基础词条（管理端配置）+ 个性化（用户历史高频地点）
 */
@Service
public class WelcomeChipsService {

    private static final Pattern PLACE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}(?:站|广场|机场|火车站|高铁站|西站|东站|南站|北站)");

    @Autowired
    private SystemConfigServiceImpl configService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 获取融合后的欢迎快捷词
     */
    public List<String> getForUser(Long userId) {
        List<String> base = configService.getJson("agent.welcome_chips", List.class);
        if (base == null) {
            base = List.of("我要去...", "附近地铁", "换乘建议", "怎么买票", "常去地点");
        }

        if (userId == null
                || configService.getConfigInt("agent.welcome_chips.use_personalized", 1) == 0) {
            return base;
        }

        int personalCount = configService.getConfigInt("agent.welcome_chips.personalized_count", 3);

        // 从用户最近消息中提取高频地点
        List<String> recentMsgs = chatMessageMapper.findRecentUserMessages(userId, 20);
        List<String> personal = extractTopPlaces(recentMsgs, personalCount);

        // 融合：个性化在前，去重后取前 N
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(personal);
        merged.addAll(base);
        int limit = Math.max(personalCount + 2, base.size());
        return merged.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> extractTopPlaces(List<String> messages, int topN) {
        Map<String, Integer> freq = new HashMap<>();
        for (String msg : messages) {
            Matcher m = PLACE_PATTERN.matcher(msg);
            while (m.find()) {
                String place = m.group();
                freq.merge(place, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
