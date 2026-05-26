package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mu.transitmap.dto.MetroStationManageCreateDTO;
import com.mu.transitmap.dto.MetroStationManageQueryDTO;
import com.mu.transitmap.dto.MetroStationManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.entity.TicketOrder;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.MetroStationMapper;
import com.mu.transitmap.service.IMetroStationService;
import com.mu.transitmap.util.JsonUtil;
import com.mu.transitmap.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetroStationServiceImpl extends ServiceImpl<MetroStationMapper, MetroStation> implements IMetroStationService {

    private static final ObjectMapper CACHE_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private CountryServiceImpl countryService;

    @Autowired
    private CityServiceImpl cityService;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private TicketOrderServiceImpl ticketOrderService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public Page<MetroStation> getMetroStationPage(MetroStationManageQueryDTO dto) {
        int pn = dto.getPageNum() != null ? dto.getPageNum() : 1;
        int ps = dto.getPageSize() != null ? dto.getPageSize() : systemConfigService.getConfigInt("pagination.default_size", 10);

        LambdaQueryWrapper<MetroStation> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                .like(MetroStation::getStationName, dto.getKeyword())
                .or().like(MetroStation::getStationNameEn, dto.getKeyword())
                .or().like(MetroStation::getStationAlias, dto.getKeyword())
            );
        }
        if (dto.getCountryId() != null) {
            wrapper.eq(MetroStation::getCountryId, dto.getCountryId());
        }
        if (dto.getCityId() != null) {
            wrapper.eq(MetroStation::getCityId, dto.getCityId());
        }
        if (dto.getIsTransfer() != null) {
            wrapper.eq(MetroStation::getIsTransfer, dto.getIsTransfer());
        }
        if (dto.getStationType() != null) {
            wrapper.eq(MetroStation::getStationType, dto.getStationType());
        }
        if (dto.getStatusCode() != null) {
            wrapper.eq(MetroStation::getStatusCode, dto.getStatusCode());
        }
        if (dto.getLineId() != null) {
            wrapper.apply("JSON_CONTAINS(line_ids, {0})", "\"" + dto.getLineId() + "\"");
        }

        String sortField = dto.getSortField();
        boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
        if (StringUtils.hasText(sortField)) {
            String[] fields = sortField.split(",");
            String[] orders = dto.getSortOrder() != null ? dto.getSortOrder().split(",") : new String[]{};
            for (int i = 0; i < fields.length; i++) {
                String f = fields[i].trim();
                boolean a = i < orders.length && "asc".equalsIgnoreCase(orders[i].trim());
                if ("stationName".equals(f)) {
                    wrapper.orderBy(true, a, MetroStation::getStationName);
                } else if ("cityName".equals(f)) {
                    wrapper.orderBy(true, a, MetroStation::getCityName);
                } else if ("longitude".equals(f)) {
                    wrapper.orderBy(true, a, MetroStation::getLongitude);
                } else if ("latitude".equals(f)) {
                    wrapper.orderBy(true, a, MetroStation::getLatitude);
                } else if ("statusCode".equals(f)) {
                    wrapper.orderBy(true, a, MetroStation::getStatusCode);
                }
            }
        }
        if (!StringUtils.hasText(sortField) || sortField.trim().isEmpty()) {
            wrapper.orderByDesc(MetroStation::getCreatedAt);
        }

        return this.page(new Page<>(pn, ps), wrapper);
    }

    @Override
    @Transactional
    public void createMetroStation(MetroStationManageCreateDTO dto, Integer operatorRoleCode) {
        if (operatorRoleCode == null || operatorRoleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Country country = countryService.getById(dto.getCountryId());
        if (country == null) {
            throw new BusinessException(ErrorCode.METRO_STATION_WRONG_COUNTRY);
        }

        City city = cityService.getById(dto.getCityId());
        if (city == null || !city.getCountryId().equals(dto.getCountryId())) {
            throw new BusinessException(ErrorCode.METRO_STATION_WRONG_CITY);
        }

        // 站名唯一性检查（同一城市下）
        if (StringUtils.hasText(dto.getStationName()) && dto.getCityId() != null) {
            boolean nameExists = this.lambdaQuery()
                    .eq(MetroStation::getCityId, dto.getCityId())
                    .eq(MetroStation::getStationName, dto.getStationName())
                    .exists();
            if (nameExists) {
                throw new BusinessException(ErrorCode.METRO_STATION_NAME_ALREADY_EXISTS);
            }
        }

        MetroStation station = new MetroStation();
        station.setCountryId(dto.getCountryId());
        station.setCountryName(country.getCountryName());
        station.setStationName(dto.getStationName());
        station.setCityId(dto.getCityId());
        station.setCityName(city.getCityName());
        station.setLongitude(dto.getLongitude());
        station.setLatitude(dto.getLatitude());
        station.setStationNameEn(dto.getStationNameEn());
        station.setStationAlias(dto.getStationAlias());
        station.setIsTransfer(dto.getIsTransfer() != null ? dto.getIsTransfer() : 0);
        station.setLineIds(JsonUtil.toJsonValue(dto.getLineIds()));
        station.setLineNames(JsonUtil.toJsonValue(dto.getLineNames()));
        station.setExitCount(dto.getExitCount() != null ? dto.getExitCount() : 0);
        station.setHasToilet(dto.getHasToilet() != null ? dto.getHasToilet() : 0);
        station.setStationType(dto.getStationType() != null ? dto.getStationType() : 0);
        station.setOsmid(0L);

        if (StringUtils.hasText(dto.getOpenDate())) {
            try { station.setOpenDate(LocalDate.parse(dto.getOpenDate(), DATE_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }
        if (StringUtils.hasText(dto.getFirstTime())) {
            try { station.setFirstTime(LocalTime.parse(dto.getFirstTime(), TIME_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }
        if (StringUtils.hasText(dto.getLastTime())) {
            try { station.setLastTime(LocalTime.parse(dto.getLastTime(), TIME_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }

        station.setPrevStationIds(JsonUtil.toJsonValue(dto.getPrevStationIds()));
        station.setPrevStationNames(JsonUtil.toJsonValue(dto.getPrevStationNames()));
        station.setPrevStationDistances(JsonUtil.toJsonValue(dto.getPrevStationDistances()));
        station.setNextStationIds(JsonUtil.toJsonValue(dto.getNextStationIds()));
        station.setNextStationNames(JsonUtil.toJsonValue(dto.getNextStationNames()));
        station.setNextStationDistances(JsonUtil.toJsonValue(dto.getNextStationDistances()));

        int sc = dto.getStatusCode() != null ? dto.getStatusCode() : 0;
        station.setStatusCode(sc);
        station.setStatus(systemConfigService.getStatusName(sc));
        station.setExtra(JsonUtil.toJsonValue(dto.getExtra()));

        save(station);
        redisUtils.deleteMetroStationListCache(dto.getCityId());
        ticketOrderService.invalidateStationCache();
    }

    @Override
    @Transactional
    public void updateMetroStation(Long id, MetroStationManageUpdateDTO dto, Integer operatorRoleCode) {
        MetroStation station = getById(id);
        if (station == null) {
            throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        }

        if (operatorRoleCode == null || operatorRoleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (dto.getStationName() != null && !dto.getStationName().equals(station.getStationName())) {
            if (operatorRoleCode != 4) {
                throw new BusinessException(ErrorCode.METRO_STATION_CANNOT_MODIFY_NAME);
            }
            station.setStationName(dto.getStationName());
        }

        if (dto.getCountryId() != null) {
            Country country = countryService.getById(dto.getCountryId());
            if (country == null) throw new BusinessException(ErrorCode.METRO_STATION_WRONG_COUNTRY);
            station.setCountryId(dto.getCountryId());
            station.setCountryName(country.getCountryName());
        }
        if (dto.getCityId() != null) {
            City city = cityService.getById(dto.getCityId());
            if (city == null) throw new BusinessException(ErrorCode.METRO_STATION_WRONG_CITY);
            station.setCityId(dto.getCityId());
            station.setCityName(city.getCityName());
        }
        if (dto.getLongitude() != null) station.setLongitude(dto.getLongitude());
        if (dto.getLatitude() != null) station.setLatitude(dto.getLatitude());
        if (dto.getStationNameEn() != null) station.setStationNameEn(dto.getStationNameEn());
        if (dto.getStationAlias() != null) station.setStationAlias(dto.getStationAlias());
        if (dto.getIsTransfer() != null) station.setIsTransfer(dto.getIsTransfer());
        if (StringUtils.hasText(dto.getLineIds())) station.setLineIds(JsonUtil.toJsonValue(dto.getLineIds()));
        if (StringUtils.hasText(dto.getLineNames())) station.setLineNames(JsonUtil.toJsonValue(dto.getLineNames()));
        if (dto.getExitCount() != null) station.setExitCount(dto.getExitCount());
        if (dto.getHasToilet() != null) station.setHasToilet(dto.getHasToilet());
        if (dto.getStationType() != null) station.setStationType(dto.getStationType());

        if (dto.getOpenDate() != null) {
            try { station.setOpenDate(LocalDate.parse(dto.getOpenDate(), DATE_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }
        if (dto.getFirstTime() != null) {
            try { station.setFirstTime(LocalTime.parse(dto.getFirstTime(), TIME_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }
        if (dto.getLastTime() != null) {
            try { station.setLastTime(LocalTime.parse(dto.getLastTime(), TIME_FMT)); }
            catch (Exception e) { throw new BusinessException(ErrorCode.PARAM_FORMAT_ERROR); }
        }

        if (StringUtils.hasText(dto.getPrevStationIds())) station.setPrevStationIds(JsonUtil.toJsonValue(dto.getPrevStationIds()));
        if (StringUtils.hasText(dto.getPrevStationNames())) station.setPrevStationNames(JsonUtil.toJsonValue(dto.getPrevStationNames()));
        if (StringUtils.hasText(dto.getPrevStationDistances())) {
            validateDistances(dto.getPrevStationDistances());
            station.setPrevStationDistances(JsonUtil.toJsonValue(dto.getPrevStationDistances()));
        }
        if (StringUtils.hasText(dto.getNextStationIds())) station.setNextStationIds(JsonUtil.toJsonValue(dto.getNextStationIds()));
        if (StringUtils.hasText(dto.getNextStationNames())) station.setNextStationNames(JsonUtil.toJsonValue(dto.getNextStationNames()));
        if (StringUtils.hasText(dto.getNextStationDistances())) {
            validateDistances(dto.getNextStationDistances());
            station.setNextStationDistances(JsonUtil.toJsonValue(dto.getNextStationDistances()));
        }

        if (dto.getStatusCode() != null) {
            station.setStatusCode(dto.getStatusCode());
            station.setStatus(systemConfigService.getStatusName(dto.getStatusCode()));
        }
        if (StringUtils.hasText(dto.getExtra())) station.setExtra(JsonUtil.toJsonValue(dto.getExtra()));

        updateById(station);
        redisUtils.deleteMetroStationListCache(station.getCityId());
        ticketOrderService.invalidateStationCache();
    }

    @Override
    @Transactional
    public void deleteMetroStation(Long id) {
        MetroStation station = getById(id);
        if (station == null) throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        // 检查是否有订单引用此站点
        long orderCount = ticketOrderService.count(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getStartStationId, id)
                        .or()
                        .eq(TicketOrder::getEndStationId, id));
        if (orderCount > 0) {
            throw new BusinessException(ErrorCode.METRO_STATION_HAS_ORDERS);
        }
        removeById(id);
        redisUtils.deleteMetroStationListCache(station.getCityId());
        ticketOrderService.invalidateStationCache();
    }

    @Override
    @Transactional
    public void batchDeleteMetroStations(List<Long> ids, Integer operatorRoleCode) {
        if (operatorRoleCode == null || operatorRoleCode != 4) {
            throw new BusinessException(ErrorCode.METRO_STATION_PERMISSION_DENIED);
        }
        // 检查是否有订单引用这些站点
        for (Long id : ids) {
            long orderCount = ticketOrderService.count(
                    new LambdaQueryWrapper<TicketOrder>()
                            .eq(TicketOrder::getStartStationId, id)
                            .or()
                            .eq(TicketOrder::getEndStationId, id));
            if (orderCount > 0) {
                throw new BusinessException(ErrorCode.METRO_STATION_HAS_ORDERS);
            }
        }
        // 记录涉及的城市ID，用于清除缓存
        Set<Long> cityIds = new HashSet<>();
        for (Long id : ids) {
            MetroStation s = getById(id);
            if (s != null && s.getCityId() != null) {
                cityIds.add(s.getCityId());
            }
        }
        removeByIds(ids);
        for (Long cityId : cityIds) {
            redisUtils.deleteMetroStationListCache(cityId);
        }
        ticketOrderService.invalidateStationCache();
    }

    @Override
    @Transactional
    public int batchImportStations(List<MetroStationManageCreateDTO> dtoList, Integer operatorRoleCode) {
        int count = 0;
        for (MetroStationManageCreateDTO dto : dtoList) {
            try {
                createMetroStation(dto, operatorRoleCode);
                count++;
            } catch (Exception e) {
                log.warn("批量导入站点失败: stationName={}, error={}", dto.getStationName(), e.getMessage());
            }
        }
        return count;
    }

    @Override
    public List<MetroStation> getStationsByCityId(Long cityId) {
        // 尝试从Redis缓存获取
        Object cached = redisUtils.getMetroStationListCache(cityId);
        if (cached instanceof List) {
            List<?> list = (List<?>) cached;
            if (list.isEmpty()) {
                return new ArrayList<>();
            }
            // Redis 反序列化后元素可能是 LinkedHashMap（丢失了具体类型）
            // 用 Jackson 转回 MetroStation，避免 ClassCastException
            Object first = list.get(0);
            if (first instanceof MetroStation) {
                @SuppressWarnings("unchecked")
                List<MetroStation> typed = (List<MetroStation>) list;
                return typed;
            }
            try {
                return CACHE_MAPPER.convertValue(
                        list, new TypeReference<List<MetroStation>>() {});
            } catch (Exception e) {
                log.warn("缓存反序列化失败，回退到查 DB", e);
                // 缓存损坏 → 删除并重新查
                redisUtils.deleteMetroStationListCache(cityId);
            }
        }

        List<MetroStation> stations = this.lambdaQuery()
                .eq(MetroStation::getCityId, cityId)
                .eq(MetroStation::getStatusCode, 1)
                .list();

        // 写入Redis缓存
        int ttl = systemConfigService.getConfigInt("cache.ttl.metroStation", 86400);
        redisUtils.setMetroStationListCache(cityId, stations, ttl);

        return stations;
    }

    @Override
    public Map<String, Object> getOrderedStationsByLineId(Long lineId) {
        // 1. 使用 SQL JSON_CONTAINS 直接查出该线路的运营中站点
        List<MetroStation> lineStations = this.lambdaQuery()
                .eq(MetroStation::getStatusCode, 1)
                .apply("JSON_CONTAINS(line_ids, {0})", "\"" + lineId + "\"")
                .list();

        if (lineStations.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("stations", Collections.emptyList());
            result.put("hasBranch", false);
            result.put("branchInfo", null);
            return result;
        }

        // 构建站点ID集合（该线路内）
        Set<String> lineStationIdSet = lineStations.stream()
                .map(s -> String.valueOf(s.getId()))
                .collect(Collectors.toSet());

        // 构建站点ID到站点对象的映射
        Map<String, MetroStation> stationMap = new HashMap<>();
        for (MetroStation s : lineStations) {
            stationMap.put(String.valueOf(s.getId()), s);
        }

        // 2. 构建邻接表
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (MetroStation s : lineStations) {
            adjacency.putIfAbsent(String.valueOf(s.getId()), new HashSet<>());
            List<String> sLineIds = parseJsonArray(s.getLineIds());
            int lineIndex = sLineIds.indexOf(String.valueOf(lineId));

            // 获取同线路的邻居
            List<String> neighborIds = getSameLineNeighbors(s, lineIndex, lineStationIdSet);

            for (String nid : neighborIds) {
                adjacency.get(String.valueOf(s.getId())).add(nid);
                adjacency.computeIfAbsent(nid, k -> new HashSet<>()).add(String.valueOf(s.getId()));
            }
        }

        // 3. 找端点（邻接数 = 1）
        List<String> endpoints = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
            if (entry.getValue().size() == 1) {
                endpoints.add(entry.getKey());
            }
        }

        // 4. 排序
        List<MetroStation> ordered;
        boolean hasBranch = false;
        String branchInfo = null;

        if (endpoints.size() >= 2) {
            // 正常线路：从一个端点遍历到另一个
            ordered = bfsOrder(endpoints.get(0), adjacency, stationMap);
            if (ordered.size() < lineStations.size()) {
                // 可能存在支线，尝试从其他端点找更长路径
                List<MetroStation> bestPath = ordered;
                for (int i = 1; i < endpoints.size(); i++) {
                    List<MetroStation> path = bfsOrder(endpoints.get(i), adjacency, stationMap);
                    if (path.size() > bestPath.size()) {
                        bestPath = path;
                    }
                }
                if (bestPath.size() < lineStations.size()) {
                    hasBranch = true;
                    branchInfo = "该线路存在支线，地图仅展示主线（" + bestPath.size() + "/" + lineStations.size() + "站）";
                }
                ordered = bestPath;
            }
        } else if (endpoints.size() == 1) {
            // 环形或单端点
            ordered = bfsOrder(endpoints.get(0), adjacency, stationMap);
            if (ordered.size() < lineStations.size()) {
                hasBranch = true;
                branchInfo = "该线路可能存在环形或支线，地图仅展示部分路线（" + ordered.size() + "/" + lineStations.size() + "站）";
            }
        } else {
            // 无端点（完全环形或无连接数据）
            ordered = new ArrayList<>(lineStations);
            ordered.sort(Comparator.comparing(MetroStation::getId));
            if (lineStations.size() > 1) {
                hasBranch = true;
                branchInfo = "该线路无明确端点，站点按ID排序展示";
            }
        }

        // 5. 如果排序结果为空或只有1站，且原始数据有连接关系，回退到简单排序
        if (ordered.size() <= 1 && lineStations.size() > 1) {
            boolean hasAnyConnection = adjacency.values().stream().anyMatch(s -> !s.isEmpty());
            if (!hasAnyConnection) {
                ordered = new ArrayList<>(lineStations);
                ordered.sort(Comparator.comparing(MetroStation::getOpenDate,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                branchInfo = "站点间无连接数据，按开通日期排序";
            }
        }

        // 6. 将未被BFS遍历到的站点追加到结果末尾，确保该线路下所有站点都被返回
        if (ordered.size() < lineStations.size()) {
            Set<Long> orderedIds = ordered.stream()
                    .map(MetroStation::getId)
                    .collect(Collectors.toSet());
            List<MetroStation> remaining = lineStations.stream()
                    .filter(s -> !orderedIds.contains(s.getId()))
                    .sorted(Comparator.comparing(MetroStation::getOpenDate,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            if (!remaining.isEmpty()) {
                ordered = new ArrayList<>(ordered);
                ordered.addAll(remaining);
                hasBranch = true;
                branchInfo = "该线路存在未排序站点，已按开通日期追加（共" + ordered.size() + "站）";
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("stations", ordered);
        result.put("hasBranch", hasBranch);
        result.put("branchInfo", branchInfo);
        return result;
    }

    /**
     * 获取站点在指定线路上的同线路邻居ID列表
     */
    private List<String> getSameLineNeighbors(MetroStation station, int lineIndex, Set<String> lineStationIdSet) {
        List<String> result = new ArrayList<>();

        if (lineIndex >= 0) {
            // 精确模式：按index取对应线路的前后站
            List<String> prevIds = parseJsonArray(station.getPrevStationIds());
            List<String> nextIds = parseJsonArray(station.getNextStationIds());

            if (lineIndex < prevIds.size()) {
                String pid = prevIds.get(lineIndex);
                if (pid != null && !pid.isEmpty() && lineStationIdSet.contains(pid)) {
                    result.add(pid);
                }
            }
            if (lineIndex < nextIds.size()) {
                String nid = nextIds.get(lineIndex);
                if (nid != null && !nid.isEmpty() && lineStationIdSet.contains(nid)) {
                    result.add(nid);
                }
            }
        } else {
            // 宽松模式：汇总所有前后站，过滤属于同线路的
            List<String> allPrev = parseJsonArray(station.getPrevStationIds());
            List<String> allNext = parseJsonArray(station.getNextStationIds());

            for (String pid : allPrev) {
                if (pid != null && !pid.isEmpty() && lineStationIdSet.contains(pid)) {
                    result.add(pid);
                }
            }
            for (String nid : allNext) {
                if (nid != null && !nid.isEmpty() && lineStationIdSet.contains(nid)) {
                    result.add(nid);
                }
            }
        }

        return result;
    }

    /**
     * BFS遍历获取有序站点列表
     */
    private List<MetroStation> bfsOrder(String startId, Map<String, Set<String>> adjacency,
                                         Map<String, MetroStation> stationMap) {
        List<MetroStation> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = startId;

        while (current != null && !visited.contains(current)) {
            visited.add(current);
            MetroStation station = stationMap.get(current);
            if (station != null) {
                ordered.add(station);
            }

            // 找下一个未访问的邻居
            String next = null;
            Set<String> neighbors = adjacency.get(current);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        next = neighbor;
                        break;
                    }
                }
            }
            current = next;
        }

        return ordered;
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    /**
     * 解析JSON字符串为List<String>（返回可变列表）
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return new ArrayList<>(JSON_MAPPER.readValue(json, STRING_LIST_TYPE));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 校验距离数组：必须是合法的非负数JSON数组
     */
    private void validateDistances(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            List<Number> distances = JSON_MAPPER.readValue(json, new TypeReference<List<Number>>() {});
            for (Number d : distances) {
                if (d == null) continue;
                double val = d.doubleValue();
                if (val < 0 || Double.isNaN(val) || Double.isInfinite(val)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 增强的批量导入：校验站名重复、坐标冲突，收集详细错误信息
     * @return Map 包含 successCount 和 errors（每条的详细错误原因）
     */
    @Override
    @Transactional
    public Map<String, Object> batchImportStationsWithDetails(
            List<MetroStationManageCreateDTO> dtoList, Integer operatorRoleCode) {
        int successCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < dtoList.size(); i++) {
            MetroStationManageCreateDTO dto = dtoList.get(i);
            List<String> reasons = new ArrayList<>();

            // 1. 校验站名是否已存在（同一城市下站名唯一）
            if (dto.getStationName() != null && dto.getCityId() != null) {
                boolean nameExists = this.lambdaQuery()
                        .eq(MetroStation::getCityId, dto.getCityId())
                        .eq(MetroStation::getStationName, dto.getStationName())
                        .exists();
                if (nameExists) {
                    reasons.add("站名「" + dto.getStationName() + "」在该城市下已存在");
                }
            }

            // 2. 校验坐标冲突：相同坐标 + 相同站名 → 重复，跳过
            //                    相同坐标 + 不同站名 → 不同站点，允许导入
            if (dto.getLongitude() != null && dto.getLatitude() != null && dto.getCityId() != null && reasons.isEmpty()) {
                MetroStation existingAtCoords = this.lambdaQuery()
                        .eq(MetroStation::getCityId, dto.getCityId())
                        .eq(MetroStation::getLongitude, dto.getLongitude())
                        .eq(MetroStation::getLatitude, dto.getLatitude())
                        .last("LIMIT 1")
                        .one();
                if (existingAtCoords != null) {
                    if (existingAtCoords.getStationName().equals(dto.getStationName())) {
                        reasons.add("经纬度(" + dto.getLongitude() + ", " + dto.getLatitude()
                                + ") 与已有站点「" + existingAtCoords.getStationName() + "」完全一致，视为同一站点");
                    }
                    // 不同站名 + 相同坐标 → 可能是换乘枢纽的不同线路站点，允许导入
                }
            }

            // 3. 有校验错误则跳过
            if (!reasons.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", i + 1); // 相对于 DTO 列表的行号
                err.put("stationName", dto.getStationName());
                err.put("reasons", reasons);
                errors.add(err);
                continue;
            }

            // 4. 执行导入
            try {
                createMetroStation(dto, operatorRoleCode);
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", i + 1);
                err.put("stationName", dto.getStationName());
                err.put("reasons", List.of(e.getMessage() != null ? e.getMessage() : "导入失败"));
                errors.add(err);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errors", errors);
        return result;
    }

    @Override
    @Transactional
    public void batchAssignLine(Long lineId, String lineName, List<Long> stationIds, Integer operatorRoleCode) {
        if (operatorRoleCode == null || operatorRoleCode < 2) {
            throw new BusinessException(ErrorCode.METRO_STATION_PERMISSION_DENIED);
        }
        String lineIdStr = String.valueOf(lineId);
        Set<Long> cityIds = new HashSet<>();
        for (Long stationId : stationIds) {
            MetroStation station = getById(stationId);
            if (station == null) continue;
            cityIds.add(station.getCityId());

            List<String> ids = parseJsonArray(station.getLineIds());
            if (ids.contains(lineIdStr)) continue;

            List<String> names = parseJsonArray(station.getLineNames());
            ids.add(lineIdStr);
            names.add(lineName);
            station.setLineIds(JsonUtil.toJsonValue(writeJson(ids)));
            station.setLineNames(JsonUtil.toJsonValue(writeJson(names)));
            if (ids.size() > 1) {
                station.setIsTransfer(1);
            }
            updateById(station);
        }
        for (Long cityId : cityIds) {
            redisUtils.deleteMetroStationListCache(cityId);
        }
    }

    @Override
    @Transactional
    public void batchRemoveLine(Long lineId, List<Long> stationIds, Integer operatorRoleCode) {
        if (operatorRoleCode == null || operatorRoleCode < 2) {
            throw new BusinessException(ErrorCode.METRO_STATION_PERMISSION_DENIED);
        }
        String lineIdStr = String.valueOf(lineId);
        Set<Long> cityIds = new HashSet<>();
        for (Long stationId : stationIds) {
            MetroStation station = getById(stationId);
            if (station == null) continue;
            cityIds.add(station.getCityId());

            List<String> ids = parseJsonArray(station.getLineIds());
            List<String> names = parseJsonArray(station.getLineNames());
            int idx = ids.indexOf(lineIdStr);
            if (idx < 0) continue;

            ids.remove(idx);
            if (idx < names.size()) names.remove(idx);
            station.setLineIds(ids.isEmpty() ? null : JsonUtil.toJsonValue(writeJson(ids)));
            station.setLineNames(names.isEmpty() ? null : JsonUtil.toJsonValue(writeJson(names)));
            station.setIsTransfer(ids.size() > 1 ? 1 : 0);
            updateById(station);
        }
        for (Long cityId : cityIds) {
            redisUtils.deleteMetroStationListCache(cityId);
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
