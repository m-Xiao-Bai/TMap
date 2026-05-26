package com.mu.transitmap.agent.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.agent.AgentContext;
import com.mu.transitmap.agent.AgentNode;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.ICityService;
import com.mu.transitmap.service.IMetroStationService;
import com.mu.transitmap.vo.LocationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Node 3: 城市匹配
 *
 * 匹配顺序：
 *   1. 高德 geocode 返回的 city → DB 模糊匹配
 *   2. 高德失败 / 城市没匹配上 → 用 slot 站名在 metro_station 反查 city_id
 *   3. 都失败 → NO_METRO
 */
@Component
public class MatchCityNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(MatchCityNode.class);

    @Autowired
    private ICityService cityService;

    @Autowired
    private IMetroStationService stationService;

    @Override
    public void execute(AgentContext ctx, Consumer<Object> push) throws Exception {
        if (ctx.shouldShortCircuit()) return;

        // 【最高优先级】LLM 推断的城市（如「滕王阁」→南昌、「鸟巢」→北京）
        // 如果 LLM 推断了一个已开通城市，直接命中，不用查高德
        if (ctx.getLlmInferredCity() != null && !ctx.getLlmInferredCity().isBlank()) {
            City c = findCity(ctx.getLlmInferredCity());
            if (c != null) {
                ctx.setFromCityId(c.getId());
                ctx.setFromCityName(c.getCityName());
                ctx.setToCityId(c.getId());
                ctx.setToCityName(c.getCityName());
                ctx.setScenario("SAME_CITY");
                log.info("LLM 推断城市直接命中: {} -> {}", ctx.getLlmInferredCity(), c.getCityName());
                return; // 不 short-circuit，让 PathPlan 用 cityName 走 LLM-RAG
            }
        }

        // 1. 高德返回的城市 → DB 模糊匹配
        if (ctx.getFromGeo() != null && ctx.getFromGeo().getCity() != null) {
            City c = findCity(ctx.getFromGeo().getCity());
            if (c != null) {
                ctx.setFromCityId(c.getId());
                ctx.setFromCityName(c.getCityName());
            }
        }
        if (ctx.getToGeo() != null && ctx.getToGeo().getCity() != null) {
            City c = findCity(ctx.getToGeo().getCity());
            if (c != null) {
                ctx.setToCityId(c.getId());
                ctx.setToCityName(c.getCityName());
            }
        }

        // 2. 兜底：高德失败时，用站名反查
        if (ctx.getFromCityId() == null && ctx.getSlotFrom() != null) {
            City c = findCityByStationName(ctx.getSlotFrom());
            if (c != null) {
                ctx.setFromCityId(c.getId());
                ctx.setFromCityName(c.getCityName());
                log.info("通过站名反查城市: '{}' -> {}", ctx.getSlotFrom(), c.getCityName());
            }
        }
        if (ctx.getToCityId() == null && ctx.getSlotTo() != null) {
            City c = findCityByStationName(ctx.getSlotTo());
            if (c != null) {
                ctx.setToCityId(c.getId());
                ctx.setToCityName(c.getCityName());
                log.info("通过站名反查城市: '{}' -> {}", ctx.getSlotTo(), c.getCityName());
            }
        }

        // 3. 判断场景
        if (ctx.getFromCityId() == null && ctx.getToCityId() == null) {
            ctx.setScenario("NO_METRO");
            ctx.setNearestSupportedCityName(findNearestSupportedCity(
                    pickAnyGeo(ctx.getFromGeo(), ctx.getToGeo())));
            ctx.setShortCircuit(true);
        } else if (ctx.getFromCityId() != null && ctx.getToCityId() != null
                && !ctx.getFromCityId().equals(ctx.getToCityId())) {
            ctx.setScenario("CROSS_CITY");
            ctx.setShortCircuit(true);
        } else {
            // 一边匹配 / 两边都匹配且同城 → SAME_CITY
            // 兜底：若只有一边匹配，把另一边也设成同城（让 PathPlan 用 LLM/geo 继续找站）
            ctx.setScenario("SAME_CITY");
            if (ctx.getFromCityId() == null) {
                ctx.setFromCityId(ctx.getToCityId());
                ctx.setFromCityName(ctx.getToCityName());
                log.info("起点城市未匹配，兜底设为终点城市: {}", ctx.getToCityName());
            }
            if (ctx.getToCityId() == null) {
                ctx.setToCityId(ctx.getFromCityId());
                ctx.setToCityName(ctx.getFromCityName());
                log.info("终点城市未匹配，兜底设为起点城市: {}", ctx.getFromCityName());
            }
        }
    }

    private City findCity(String cityName) {
        if (cityName == null || cityName.isEmpty()) return null;
        String cleanName = cityName.replaceAll("市$", "");
        List<City> cities = cityService.list(new LambdaQueryWrapper<City>()
                .and(w -> w.like(City::getCityName, cleanName)
                        .or().like(City::getCityNameEn, cleanName)
                        .or().like(City::getCityAlias, cleanName))
                .eq(City::getStatusCode, 3));
        return cities.isEmpty() ? null : cities.get(0);
    }

    /**
     * 通过站名反查城市
     * 1) 在 metro_station 表里按站名（精确/like）找
     * 2) 失败 → 如果 slot 含已开通城市名（如"南昌机场"包含"南昌"），用城市名反查
     */
    private City findCityByStationName(String slotName) {
        if (slotName == null || slotName.isBlank()) return null;
        String raw = slotName.trim();
        String clean = raw.replaceAll("(地铁站|火车站|站)$", "").trim();

        List<MetroStation> hits = stationService.list(new LambdaQueryWrapper<MetroStation>()
                .eq(MetroStation::getStatusCode, 1)
                .and(w -> w.eq(MetroStation::getStationName, raw)
                        .or().eq(MetroStation::getStationName, clean)
                        .or().eq(MetroStation::getStationAlias, raw))
                .last("LIMIT 1"));
        if (hits.isEmpty() && clean.length() >= 2) {
            hits = stationService.list(new LambdaQueryWrapper<MetroStation>()
                    .eq(MetroStation::getStatusCode, 1)
                    .like(MetroStation::getStationName, clean)
                    .last("LIMIT 1"));
        }
        if (!hits.isEmpty()) {
            Long cityId = hits.get(0).getCityId();
            if (cityId != null) return cityService.getById(cityId);
        }

        // 兜底：slot 是否包含某个已开通城市的名字（覆盖"南昌机场"这种口语化输入）
        List<City> opened = cityService.list(new LambdaQueryWrapper<City>()
                .eq(City::getStatusCode, 3));
        for (City c : opened) {
            String name = c.getCityName();
            if (name == null || name.isEmpty()) continue;
            String namePure = name.replaceAll("市$", "");
            if (raw.contains(name) || raw.contains(namePure)) {
                log.info("slot '{}' 包含城市名 '{}'", raw, name);
                return c;
            }
            String alias = c.getCityAlias();
            if (alias != null && !alias.isEmpty() && raw.contains(alias)) return c;
        }
        return null;
    }

    private LocationVO pickAnyGeo(LocationVO a, LocationVO b) {
        if (a != null && a.getLat() != null && a.getLng() != null) return a;
        return b;
    }

    private String findNearestSupportedCity(LocationVO ref) {
        try {
            List<City> opened = cityService.list(new LambdaQueryWrapper<City>()
                    .eq(City::getStatusCode, 3));
            if (opened.isEmpty()) return null;
            // City 表无经纬度字段，无法真正算距离，返回第一个开通城市作为兜底
            return opened.get(0).getCityName();
        } catch (Exception e) {
            log.warn("findNearestSupportedCity failed", e);
            return null;
        }
    }
}
