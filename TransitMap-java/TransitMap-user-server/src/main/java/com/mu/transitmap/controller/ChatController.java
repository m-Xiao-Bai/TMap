package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.*;
import com.mu.transitmap.mapper.*;
import com.mu.transitmap.result.Result;import com.mu.transitmap.service.AmapClient;
import com.mu.transitmap.service.ICityService;
import com.mu.transitmap.service.IMetroStationService;
import com.mu.transitmap.service.WelcomeChipsService;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.utils.JwtUtil;
import com.mu.transitmap.utils.RedisUtils;
import com.mu.transitmap.vo.LocationVO;
import com.mu.transitmap.websocket.AgentSessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent 聊天 REST 接口
 */
@RestController
@RequestMapping("/agent")
public class ChatController {

    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private WelcomeChipsService welcomeChipsService;
    @Autowired
    private SystemConfigServiceImpl configService;
    @Autowired
    private AmapClient amapClient;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private IMetroStationService stationService;
    @Autowired
    private ICityService cityService;
    @Autowired
    private AgentSessionRegistry sessionRegistry;
    @Autowired
    private SystemMessageMapper systemMessageMapper;

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 获取或创建今天的会话
     */
    @GetMapping("/session/today")
    public Result<ChatSession> todaySession(HttpServletRequest request) {
        Long userId = getUserId(request);
        String anonToken = request.getHeader("X-Anon-Token");

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .ge(ChatSession::getCreateTime, todayStart)
                .eq(ChatSession::getStatus, 1)
                .orderByDesc(ChatSession::getCreateTime)
                .last("LIMIT 1");

        if (userId != null) {
            wrapper.eq(ChatSession::getUserId, userId);
        } else if (anonToken != null) {
            wrapper.eq(ChatSession::getAnonToken, anonToken);
        } else {
            return Result.fail(401, "未登录");
        }

        ChatSession session = chatSessionMapper.selectOne(wrapper);
        if (session == null) {
            session = createSession(userId, anonToken);
        }
        return Result.success(session);
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> mySessions(HttpServletRequest request) {
        Long userId = getUserId(request);
        String anonToken = request.getHeader("X-Anon-Token");

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStatus, 1)
                .orderByDesc(ChatSession::getLastMsgAt);

        if (userId != null) {
            wrapper.eq(ChatSession::getUserId, userId);
        } else if (anonToken != null) {
            wrapper.eq(ChatSession::getAnonToken, anonToken);
        } else {
            return Result.success(List.of());
        }

        return Result.success(chatSessionMapper.selectList(wrapper));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/session/new")
    public Result<ChatSession> newSession(HttpServletRequest request) {
        Long userId = getUserId(request);
        String anonToken = request.getHeader("X-Anon-Token");
        if (userId == null && (anonToken == null || anonToken.isEmpty())) {
            return Result.fail(401, "未登录");
        }
        return Result.success(createSession(userId, anonToken));
    }

    /**
     * 删除会话（软删除）
     */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        ChatSession session = chatSessionMapper.selectById(id);
        if (session == null) return Result.fail(404, "会话不存在");

        // 验证所有权
        if (userId != null && !userId.equals(session.getUserId())) {
            return Result.fail(403, "无权操作");
        }

        session.setStatus(0);
        chatSessionMapper.updateById(session);
        return Result.success(null);
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/session/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        List<ChatMessage> msgs = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, id)
                        .orderByAsc(ChatMessage::getCreateTime));
        return Result.success(msgs);
    }

    /**
     * 服务端定位（IP 定位兜底）
     */
    @PostMapping("/locate")
    public Result<LocationVO> locate(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        LocationVO location = amapClient.ipLocate(ip);
        if (location != null) {
            return Result.success(location);
        }
        return Result.fail(500, "定位失败");
    }

    /**
     * 站点/城市搜索联想
     * type=station: 从 metro_station 表按 cityId + 前缀匹配
     * type=city:    从 city 表按前缀匹配
     */
    @GetMapping("/suggest")
    public Result<List<Map<String, Object>>> suggest(
            @RequestParam String q,
            @RequestParam(required = false) Long cityId,
            @RequestParam(defaultValue = "station") String type) {

        List<Map<String, Object>> results = new ArrayList<>();
        if (q == null || q.trim().isEmpty()) {
            return Result.success(results);
        }
        String kw = q.trim();

        if ("city".equalsIgnoreCase(type)) {
            List<City> cities = cityService.list(new LambdaQueryWrapper<City>()
                    .and(w -> w.like(City::getCityName, kw)
                            .or().like(City::getCityNameEn, kw)
                            .or().like(City::getCityAlias, kw))
                    .last("LIMIT 10"));
            for (City c : cities) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", c.getId());
                item.put("name", c.getCityName());
                item.put("subtitle", c.getCountryName());
                item.put("type", "city");
                results.add(item);
            }
            return Result.success(results);
        }

        // 默认 station
        LambdaQueryWrapper<MetroStation> wrapper = new LambdaQueryWrapper<MetroStation>()
                .and(w -> w.like(MetroStation::getStationName, kw)
                        .or().like(MetroStation::getStationNameEn, kw)
                        .or().like(MetroStation::getStationAlias, kw))
                .last("LIMIT 10");
        if (cityId != null) {
            wrapper.eq(MetroStation::getCityId, cityId);
        }
        List<MetroStation> stations = stationService.list(wrapper);
        for (MetroStation s : stations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("name", s.getStationName());
            item.put("subtitle", s.getLineNames());
            item.put("cityId", s.getCityId());
            item.put("cityName", s.getCityName());
            item.put("lat", s.getLatitude());
            item.put("lng", s.getLongitude());
            item.put("type", "station");
            results.add(item);
        }
        return Result.success(results);
    }

    /**
     * 消息反馈（赞/踩）
     */
    @PostMapping("/message/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ChatMessage msg = chatMessageMapper.selectById(id);
        if (msg == null) return Result.fail(404, "消息不存在");

        Object fb = body.get("feedback");
        if (fb instanceof Integer) {
            msg.setFeedback((Integer) fb);
        } else if (fb instanceof Number) {
            msg.setFeedback(((Number) fb).intValue());
        }
        chatMessageMapper.updateById(msg);
        return Result.success(null);
    }

    /**
     * 获取欢迎快捷词（基础 + 个性化融合）
     */
    @GetMapping("/chips/welcome")
    public Result<List<String>> welcomeChips(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<String> chips = welcomeChipsService.getForUser(userId);
        return Result.success(chips);
    }

    /**
     * 匿名用户的当日配额查询：用于前端展示「还剩 X 条」
     * 登录用户不限制，返回 unlimited=true
     */
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota(HttpServletRequest request) {
        Map<String, Object> r = new LinkedHashMap<>();
        Long userId = getUserId(request);
        if (userId != null) {
            r.put("unlimited", true);
            return Result.success(r);
        }
        String anonToken = request.getHeader("X-Anon-Token");
        int limit = configService.getConfigInt("agent.anon.daily_message_limit", 10);
        r.put("unlimited", false);
        r.put("limit", limit);
        if (anonToken == null || anonToken.isEmpty()) {
            r.put("used", 0);
            r.put("remaining", limit);
            return Result.success(r);
        }
        String dailyKey = "agent:anon:daily:" + anonToken + ":" + LocalDate.now();
        Long used = null;
        try {
            used = redisUtils.getKeyCount(dailyKey);
        } catch (Exception ignored) {}
        long usedVal = used == null ? 0L : used;
        r.put("used", usedVal);
        r.put("remaining", Math.max(0L, limit - usedVal));
        return Result.success(r);
    }

    /**
     * WS 监控指标（管理员展示用）
     * 仅 roleCode >= 2 可看
     */
    @GetMapping("/ws-metrics")
    public Result<Map<String, Object>> wsMetrics(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return Result.fail(401, "未登录");
        int role = getRoleCode(request);
        if (role < 2) return Result.fail(403, "无权限");
        return Result.success(sessionRegistry.metrics());
    }

    /**
     * 城市不存在时，用户请求管理员添加该城市
     * 创建一条系统消息发给管理员（target=2）+ 限流防刷
     */
    @PostMapping("/request-city")
    public Result<Void> requestCity(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String cityName = body.getOrDefault("cityName", "").trim();
        if (cityName.isEmpty()) return Result.fail(400, "缺少城市名");
        if (cityName.length() > 50) cityName = cityName.substring(0, 50);

        // 防刷：同一 anon/userId 一天最多请求 3 次同城市
        Long userId = getUserId(request);
        String anonToken = request.getHeader("X-Anon-Token");
        String requesterKey = userId != null ? "u:" + userId : "a:" + (anonToken == null ? "" : anonToken);
        String rlKey = "agent:req-city:" + requesterKey + ":" + cityName + ":" + LocalDate.now();
        try {
            Long c = redisUtils.incrementKey(rlKey, 25 * 3600L);
            if (c != null && c > 3) {
                return Result.fail(429, "您今天已多次请求该城市，请耐心等待管理员处理");
            }
        } catch (Exception ignored) {}

        SystemMessage msg = new SystemMessage();
        msg.setType("AGENT_CITY_REQUEST");
        msg.setTitle("用户请求添加城市：" + cityName);
        String requesterTag = userId != null
                ? ("用户 ID " + userId)
                : ("匿名用户 " + (anonToken == null ? "?" : anonToken.substring(0, Math.min(12, anonToken.length()))));
        msg.setContent(requesterTag + " 通过路线助手反馈：希望尽快接入「" + cityName + "」的地铁线路数据。");
        msg.setUserId(userId);
        msg.setTarget(2); // 仅管理员
        msg.setIsRead(0);
        msg.setCreateTime(LocalDateTime.now());
        systemMessageMapper.insert(msg);
        return Result.success(null);
    }

    // ===== 辅助方法 =====

    private int getRoleCode(HttpServletRequest request) {
        Object roleObj = request.getAttribute("roleCode");
        if (roleObj instanceof Integer) return (Integer) roleObj;
        // user-server 的 /agent/** 被拦截器排除，需要手动解 token
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    return jwtUtil.getRoleCodeFromToken(token);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private Long getUserId(HttpServletRequest request) {
        // 先从拦截器设置的属性中获取
        Object uid = request.getAttribute("userId");
        if (uid instanceof Long) return (Long) uid;

        // 拦截器被排除时，手动解析 JWT
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String storedToken = redisUtils.getToken(userId);
                    if (storedToken != null && storedToken.equals(token)) {
                        return userId;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private ChatSession createSession(Long userId, String anonToken) {
        // 匿名用户保留较短（默认 3 天），登录用户保留较长（默认 30 天）
        int ttlDays = (userId == null)
                ? configService.getConfigInt("agent.anon.session_ttl_days", 3)
                : configService.getConfigInt("agent.history.session_ttl_days", 30);
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAnonToken(anonToken);
        session.setTitle("新对话");
        session.setMsgCount(0);
        session.setStatus(1);
        session.setExpireAt(LocalDateTime.now().plusDays(ttlDays));
        chatSessionMapper.insert(session);
        return session;
    }
}
