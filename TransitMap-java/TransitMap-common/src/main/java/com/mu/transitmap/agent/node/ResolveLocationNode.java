package com.mu.transitmap.agent.node;

import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.AgentNode;
import com.mu.transitmap.entity.ChatSession;
import com.mu.transitmap.mapper.ChatSessionMapper;
import com.mu.transitmap.service.AmapClient;
import com.mu.transitmap.vo.LocationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Node 2: 位置解析（高德地理编码）
 *
 * 重名 POI 消歧策略：
 *   1. IP 定位拿到当前城市 → 作为默认 hint
 *   2. 浏览器定位 regeo 拿到的 city → 优先级更高
 *   3. 用户上一轮提到的城市（chat_session.extras.lastCity） → 优先级更高
 *   4. 这三层任意一个有值，geocode 时把 city 参数传过去，避免高德返回错误城市的同名 POI
 */
@Component
public class ResolveLocationNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(ResolveLocationNode.class);

    @Autowired
    private AmapClient amapClient;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Override
    public void execute(AgentContext ctx, Consumer<Object> push) throws Exception {
        // 0. 推断"城市上下文" — 用来给 geocode 做重名消歧
        String cityHint = inferCityHint(ctx);
        if (cityHint != null) {
            log.info("Geocode 城市上下文 hint: {}", cityHint);
        }

        // 1. 解析出发地（先按 cityHint，失败再不限城市重试）
        if (ctx.getSlotFrom() != null && !ctx.getSlotFrom().isEmpty()) {
            ctx.setFromGeo(safeGeocode(ctx.getSlotFrom(), cityHint));
        }
        if (ctx.getFromGeo() == null && ctx.getLat() != 0 && ctx.getLng() != 0) {
            try {
                ctx.setFromGeo(amapClient.regeo(ctx.getLng(), ctx.getLat()));
            } catch (Exception e) {
                log.warn("regeo failed: {}", e.getMessage());
            }
        }
        if (ctx.getFromGeo() == null && ctx.getClientIp() != null) {
            try {
                ctx.setFromGeo(amapClient.ipLocate(ctx.getClientIp()));
            } catch (Exception e) {
                log.warn("ipLocate failed: {}", e.getMessage());
            }
        }

        // 2. 解析目的地（cityHint 用「起点解析出的城市」或「之前推断的」）
        String toHint = cityHint;
        if (ctx.getFromGeo() != null && ctx.getFromGeo().getCity() != null
                && !ctx.getFromGeo().getCity().isEmpty()) {
            toHint = ctx.getFromGeo().getCity();
        }
        if (ctx.getSlotTo() != null && !ctx.getSlotTo().isEmpty()) {
            ctx.setToGeo(safeGeocode(ctx.getSlotTo(), toHint));
        }

        // 3. 把本轮解析出的城市写回 session.extras.lastCity（下轮用）
        persistLastCity(ctx);

        // 4. 用户没说目的地 + 也没有任何上下文位置 → 缺目的地
        if (ctx.getToGeo() == null && (ctx.getSlotTo() == null || ctx.getSlotTo().isEmpty())) {
            ctx.setScenario("MISSING_DEST");
            ctx.setShortCircuit(true);
        }
    }

    /**
     * 安全 geocode：先按 city hint，失败再不限城市重试。
     * city hint 命中能消歧"滕王阁/广西大学"这种重名 POI
     */
    private LocationVO safeGeocode(String address, String cityHint) {
        if (cityHint != null && !cityHint.isEmpty()) {
            try {
                LocationVO loc = amapClient.geocode(address, cityHint);
                if (loc != null && loc.getLat() != null) {
                    log.info("Geocode 命中（city={}, addr={}）: {}", cityHint, address,
                            loc.getFormattedAddress());
                    return loc;
                }
            } catch (Exception e) {
                log.warn("Geocode '{}' with city='{}' failed: {}", address, cityHint, e.getMessage());
            }
        }
        try {
            LocationVO loc = amapClient.geocode(address, null);
            if (loc != null) {
                log.info("Geocode 命中（不限 city, addr={}）: {} @ {}", address,
                        loc.getFormattedAddress(), loc.getCity());
            }
            return loc;
        } catch (Exception e) {
            log.warn("Geocode '{}' (no city) failed: {}", address, e.getMessage());
            return null;
        }
    }

    /**
     * 推断本轮的城市 hint（用于 geocode 消歧）
     * 优先级：LLM 推断 > 浏览器定位 city > session.extras.lastCity > IP 定位 city
     */
    private String inferCityHint(AgentContext ctx) {
        // 【最高】LLM 在 IntentExtract 已识别的城市（如「滕王阁」→南昌、「鸟巢」→北京、「南昌西」→南昌）
        if (ctx.getLlmInferredCity() != null && !ctx.getLlmInferredCity().isBlank()) {
            return ctx.getLlmInferredCity();
        }
        // 浏览器定位（regeo）—— 最准
        if (ctx.getLat() != 0 && ctx.getLng() != 0) {
            try {
                LocationVO geo = amapClient.regeo(ctx.getLng(), ctx.getLat());
                if (geo != null && geo.getCity() != null && !geo.getCity().isEmpty()) {
                    return geo.getCity();
                }
            } catch (Exception ignored) {}
        }
        // 上轮会话的城市
        String last = readLastCity(ctx.getChatSessionId());
        if (last != null && !last.isEmpty()) return last;

        // IP 定位
        if (ctx.getClientIp() != null) {
            try {
                LocationVO geo = amapClient.ipLocate(ctx.getClientIp());
                if (geo != null && geo.getCity() != null && !geo.getCity().isEmpty()) {
                    return geo.getCity();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String readLastCity(Long sessionId) {
        if (sessionId == null) return null;
        try {
            ChatSession s = chatSessionMapper.selectById(sessionId);
            if (s == null || s.getExtras() == null) return null;
            // 简单 JSON 读取（避免引入 Jackson 实例）
            String json = s.getExtras();
            int idx = json.indexOf("\"lastCity\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int q1 = json.indexOf('"', colon + 1);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return null;
            return json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }

    private void persistLastCity(AgentContext ctx) {
        if (ctx.getChatSessionId() == null) return;
        String city = null;
        if (ctx.getFromGeo() != null && ctx.getFromGeo().getCity() != null
                && !ctx.getFromGeo().getCity().isEmpty()) {
            city = ctx.getFromGeo().getCity();
        } else if (ctx.getToGeo() != null && ctx.getToGeo().getCity() != null
                && !ctx.getToGeo().getCity().isEmpty()) {
            city = ctx.getToGeo().getCity();
        }
        if (city == null) return;

        try {
            ChatSession s = chatSessionMapper.selectById(ctx.getChatSessionId());
            if (s == null) return;
            // 简单写 JSON（覆盖式）
            String safeCity = city.replace("\"", "");
            s.setExtras("{\"lastCity\":\"" + safeCity + "\"}");
            chatSessionMapper.updateById(s);
        } catch (Exception e) {
            log.warn("persist lastCity failed", e);
        }
    }
}
