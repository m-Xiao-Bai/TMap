package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.entity.TicketOrder;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.TicketOrderMapper;
import com.mu.transitmap.service.ITicketOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TicketOrderServiceImpl extends ServiceImpl<TicketOrderMapper, TicketOrder> implements ITicketOrderService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<Map<String, String>>() {};
    private static final TypeReference<List<Map<String, Object>>> TIER_LIST = new TypeReference<List<Map<String, Object>>>() {};

    /** 站点缓存过期时间（毫秒）：5 分钟 */
    private static final long STATION_CACHE_TTL_MS = 5 * 60 * 1000;

    @Autowired
    private SystemConfigServiceImpl configService;
    @Autowired
    private MetroStationServiceImpl metroStationService;
    @Autowired
    @org.springframework.context.annotation.Lazy
    private MessageServiceImpl messageService;

    // ═══════════════════════════════════════════
    //  站点 + 图 缓存（避免重复查库）
    // ═══════════════════════════════════════════

    /** 站点列表缓存 */
    private volatile List<MetroStation> cachedStations;
    /** 站点 Map 缓存：id → MetroStation */
    private volatile Map<Long, MetroStation> cachedStationMap;
    /** 邻接图缓存：stationId → neighborIds */
    private volatile Map<Long, List<Long>> cachedGraph;
    /** 缓存刷新时间 */
    private volatile long cacheTimestamp;

    /**
     * 获取缓存的站点列表，过期自动刷新
     */
    private List<MetroStation> getCachedStations() {
        if (cachedStations == null || System.currentTimeMillis() - cacheTimestamp > STATION_CACHE_TTL_MS) {
            synchronized (this) {
                if (cachedStations == null || System.currentTimeMillis() - cacheTimestamp > STATION_CACHE_TTL_MS) {
                    refreshStationCache();
                }
            }
        }
        return cachedStations;
    }

    private Map<Long, MetroStation> getCachedStationMap() {
        getCachedStations(); // 确保缓存有效
        return cachedStationMap;
    }

    private Map<Long, List<Long>> getCachedGraph() {
        getCachedStations(); // 确保缓存有效
        return cachedGraph;
    }

    private void refreshStationCache() {
        List<MetroStation> stations = metroStationService.lambdaQuery()
                .eq(MetroStation::getStatusCode, 1)
                .list();

        Map<Long, MetroStation> stationMap = new HashMap<>();
        Map<Long, List<Long>> graph = new HashMap<>();

        for (MetroStation s : stations) {
            long sid = s.getId();
            stationMap.put(sid, s);
            graph.putIfAbsent(sid, new ArrayList<>());

            List<String> nextIds = parseJsonArray(s.getNextStationIds());
            List<String> prevIds = parseJsonArray(s.getPrevStationIds());
            for (String nid : nextIds) {
                if (nid != null && !nid.isEmpty()) {
                    graph.get(sid).add(Long.parseLong(nid));
                }
            }
            for (String pid : prevIds) {
                if (pid != null && !pid.isEmpty()) {
                    graph.get(sid).add(Long.parseLong(pid));
                }
            }
        }

        cachedStations = stations;
        cachedStationMap = stationMap;
        cachedGraph = graph;
        cacheTimestamp = System.currentTimeMillis();
        log.info("站点缓存已刷新，共 {} 个站点", stations.size());
    }

    /**
     * 管理员修改站点数据后调用，清除缓存
     */
    public void invalidateStationCache() {
        cachedStations = null;
        cachedStationMap = null;
        cachedGraph = null;
        cacheTimestamp = 0;
        log.info("站点缓存已清除");
    }

    // ═══════════════════════════════════════════
    //  购票
    // ═══════════════════════════════════════════

    @Transactional
    @Override
    public List<Map<String, Object>> createOrders(Long userId, Long startStationId, Long endStationId, int quantity) {
        // 1. 校验
        if (startStationId.equals(endStationId)) {
            throw new BusinessException(ErrorCode.TICKET_STATION_SAME);
        }
        if (quantity < 1 || quantity > 10) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }

        Map<Long, MetroStation> stationMap = getCachedStationMap();
        Map<Long, List<Long>> graph = getCachedGraph();

        // 2. 获取起终点站点信息（从缓存）
        MetroStation startStation = stationMap.get(startStationId);
        MetroStation endStation = stationMap.get(endStationId);
        if (startStation == null || endStation == null) {
            throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        }

        // 3. BFS 计算最短路径（使用缓存的图）
        List<Long> stationIdPath = bfsShortestPath(startStationId, endStationId, graph);
        if (stationIdPath == null || stationIdPath.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKET_ROUTE_NOT_FOUND);
        }

        // 4. 收集路径上的站点名称、线路信息（从缓存）
        List<String> stationNamePath = new ArrayList<>();
        Set<String> lineIdSet = new LinkedHashSet<>();
        Set<String> lineNameSet = new LinkedHashSet<>();
        for (Long sid : stationIdPath) {
            MetroStation s = stationMap.get(sid);
            if (s != null) {
                stationNamePath.add(s.getStationName());
                List<String> sLineIds = parseJsonArray(s.getLineIds());
                List<String> sLineNames = parseJsonArray(s.getLineNames());
                lineIdSet.addAll(sLineIds);
                for (int i = 0; i < sLineIds.size() && i < sLineNames.size(); i++) {
                    lineNameSet.add(sLineNames.get(i));
                }
            }
        }

        // 5. 验证路径并计算距离、时间、票价（全部从缓存）
        double totalDistanceKm = validatePathAndGetDistance(stationIdPath, stationMap);
        BigDecimal distanceKm = BigDecimal.valueOf(totalDistanceKm)
                .setScale(2, RoundingMode.HALF_UP);
        int stationCount = stationIdPath.size() - 1;
        Map<String, Object> estimateParams = configService.getConfigObject(
                "ticket.estimate_params", new TypeReference<Map<String, Object>>() {});
        double minutesPerStop = getDouble(estimateParams, "minutesPerStop", 3.0);
        double minMinutes = getDouble(estimateParams, "minMinutes", 2.0);
        int durationMinutes = Math.max((int) minMinutes, (int) Math.round(stationCount * minutesPerStop));
        int price = calculatePrice(stationCount);

        // 6. 循环创建 quantity 张票
        String stationIdsJson = toJson(stationIdPath.stream().map(String::valueOf).collect(Collectors.toList()));
        String stationNamesJson = toJson(stationNamePath);
        String lineIdsJson = toJson(new ArrayList<>(lineIdSet));
        String lineNamesJson = toJson(new ArrayList<>(lineNameSet));
        LocalDateTime now = LocalDateTime.now();
        int qrValidityHours = configService.getConfigInt("ticket.qr_validity_hours", 24);

        List<TicketOrder> orders = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            String orderNo = generateOrderNo();
            String qrCode = UUID.randomUUID().toString().replace("-", "");

            TicketOrder order = new TicketOrder()
                    .setOrderNo(orderNo)
                    .setUserId(userId)
                    .setStartStationId(startStationId)
                    .setStartStationName(startStation.getStationName())
                    .setEndStationId(endStationId)
                    .setEndStationName(endStation.getStationName())
                    .setStationCount(stationCount)
                    .setStationIds(stationIdsJson)
                    .setStationNames(stationNamesJson)
                    .setLineIds(lineIdsJson)
                    .setLineNames(lineNamesJson)
                    .setPrice(price)
                    .setDistanceKm(distanceKm)
                    .setDurationMinutes(durationMinutes)
                    .setStatus(0)
                    .setQrCode(qrCode)
                    .setQrExpireTime(now.plusHours(qrValidityHours))
                    .setOrderTime(now)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            orders.add(order);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderNo", orderNo);
            result.put("price", price);
            result.put("qrCode", qrCode);
            result.put("stationCount", stationCount);
            result.put("distanceKm", distanceKm);
            result.put("durationMinutes", durationMinutes);
            results.add(result);
        }

        saveBatch(orders);

        // 回填 orderId 到结果
        for (int i = 0; i < orders.size(); i++) {
            results.get(i).put("orderId", orders.get(i).getId());
        }

        log.info("用户{}批量创建{}张票, 起点{}→终点{}, {}站, {}元/张", userId, quantity,
                startStation.getStationName(), endStation.getStationName(), stationCount, price);

        // 发送消息
        try {
            messageService.sendOrderMessage("ORDER_CREATED",
                    "新订单创建",
                    String.format("用户下单 %d 张，%s → %s，%d 站，%d 元/张", quantity,
                            startStation.getStationName(), endStation.getStationName(), stationCount, price),
                    userId, orders.get(0).getId());
        } catch (Exception e) { log.warn("发送订单创建消息失败", e); }

        return results;
    }

    @Transactional
    @Override
    public void payOrder(Long userId, Long orderId) {
        TicketOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }
        // 原子更新：只有 status=0 时才更新为 1，防止并发重复支付
        LocalDateTime now = LocalDateTime.now();
        boolean ok = update(new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, orderId)
                .eq(TicketOrder::getStatus, 0)
                .set(TicketOrder::getStatus, 1)
                .set(TicketOrder::getPayTime, now)
                .set(TicketOrder::getUpdateTime, now));
        if (!ok) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }
        log.info("用户{}支付订单{}, 金额{}元", userId, order.getOrderNo(), order.getPrice());

        try {
            messageService.sendOrderMessage("ORDER_PAID",
                    "订单已支付",
                    String.format("订单 %s 已支付 %d 元", order.getOrderNo(), order.getPrice()),
                    userId, orderId);
        } catch (Exception e) { log.warn("发送支付消息失败", e); }
    }

    @Transactional
    @Override
    public void requestRefund(Long userId, Long orderId, String reason) {
        TicketOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 1) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(5);
        order.setRefundReason(reason);
        order.setUpdateTime(now);
        updateById(order);
        log.info("用户{}申请退票订单{}, 原因: {}", userId, order.getOrderNo(), reason);

        try {
            messageService.sendOrderMessage("REFUND_PENDING",
                    "退票申请待审核",
                    String.format("订单 %s 申请退票，原因：%s", order.getOrderNo(), reason),
                    userId, orderId);
        } catch (Exception e) { log.warn("发送退票消息失败", e); }
    }

    @Transactional
    @Override
    public void approveRefund(Long orderId, int action, Integer roleCode) {
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        TicketOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 5) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        if (action == 1) {
            // 批准退票
            order.setStatus(4);
            order.setRefundTime(now);
            order.setUpdateTime(now);
            updateById(order);
            log.info("管理员{}批准退票订单{}", roleCode, order.getOrderNo());
            try {
                messageService.sendOrderMessage("ORDER_REFUNDED",
                        "退票已批准",
                        String.format("订单 %s 退票已批准，退款将原路返回", order.getOrderNo()),
                        order.getUserId(), orderId);
            } catch (Exception e) { log.warn("发送退票批准消息失败", e); }
        } else if (action == 2) {
            // 拒绝退票，恢复已支付状态
            order.setStatus(1);
            order.setRefundReason(null);
            order.setUpdateTime(now);
            updateById(order);
            log.info("管理员{}拒绝退票订单{}", roleCode, order.getOrderNo());
            try {
                messageService.sendOrderMessage("ORDER_PAID",
                        "退票被拒绝",
                        String.format("订单 %s 退票申请被拒绝，已恢复为已支付状态", order.getOrderNo()),
                        order.getUserId(), orderId);
            } catch (Exception e) { log.warn("发送退票拒绝消息失败", e); }
        } else {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    @Transactional
    @Override
    public void adminUpdateOrder(Long orderId, Map<String, Object> body, Integer roleCode) {
        if (roleCode == null || roleCode < 2) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        TicketOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = false;

        // 票价修改（需要 roleCode >= 3）
        if (body.containsKey("price")) {
            if (roleCode < 3) throw new BusinessException(ErrorCode.FORBIDDEN);
            order.setPrice(Integer.parseInt(String.valueOf(body.get("price"))));
            updated = true;
        }

        // 起止站修改（需要 roleCode >= 3），自动重新规划路线
        if (body.containsKey("startStationId") || body.containsKey("endStationId")) {
            if (roleCode < 3) throw new BusinessException(ErrorCode.FORBIDDEN);
            Long newStartId = body.containsKey("startStationId")
                    ? Long.parseLong(String.valueOf(body.get("startStationId")))
                    : order.getStartStationId();
            Long newEndId = body.containsKey("endStationId")
                    ? Long.parseLong(String.valueOf(body.get("endStationId")))
                    : order.getEndStationId();
            if (newStartId.equals(newEndId)) {
                throw new BusinessException(ErrorCode.TICKET_STATION_SAME);
            }

            Map<Long, MetroStation> stationMap = getCachedStationMap();
            Map<Long, List<Long>> graph = getCachedGraph();

            // 重新计算路线
            List<Long> stationIdPath = bfsShortestPath(newStartId, newEndId, graph);
            if (stationIdPath == null || stationIdPath.isEmpty()) {
                throw new BusinessException(ErrorCode.TICKET_ROUTE_NOT_FOUND);
            }
            MetroStation startStation = stationMap.get(newStartId);
            MetroStation endStation = stationMap.get(newEndId);
            if (startStation == null || endStation == null) {
                throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
            }
            // 收集路径信息
            List<String> stationNamePath = new ArrayList<>();
            Set<String> lineIdSet = new LinkedHashSet<>();
            Set<String> lineNameSet = new LinkedHashSet<>();
            for (Long sid : stationIdPath) {
                MetroStation s = stationMap.get(sid);
                if (s != null) {
                    stationNamePath.add(s.getStationName());
                    List<String> sLineIds = parseJsonArray(s.getLineIds());
                    List<String> sLineNames = parseJsonArray(s.getLineNames());
                    lineIdSet.addAll(sLineIds);
                    for (int i = 0; i < sLineIds.size() && i < sLineNames.size(); i++) {
                        lineNameSet.add(sLineNames.get(i));
                    }
                }
            }
            double totalDistanceKm = validatePathAndGetDistance(stationIdPath, stationMap);
            BigDecimal distanceKm = BigDecimal.valueOf(totalDistanceKm).setScale(2, RoundingMode.HALF_UP);
            int stationCount = stationIdPath.size() - 1;
            Map<String, Object> estimateParams = configService.getConfigObject(
                    "ticket.estimate_params", new TypeReference<Map<String, Object>>() {});
            double minutesPerStop = getDouble(estimateParams, "minutesPerStop", 3.0);
            double minMinutes = getDouble(estimateParams, "minMinutes", 2.0);
            int durationMinutes = Math.max((int) minMinutes, (int) Math.round(stationCount * minutesPerStop));
            int newPrice = calculatePrice(stationCount);

            order.setStartStationId(newStartId);
            order.setStartStationName(startStation.getStationName());
            order.setEndStationId(newEndId);
            order.setEndStationName(endStation.getStationName());
            order.setStationCount(stationCount);
            order.setStationIds(toJson(stationIdPath.stream().map(String::valueOf).collect(Collectors.toList())));
            order.setStationNames(toJson(stationNamePath));
            order.setLineIds(toJson(new ArrayList<>(lineIdSet)));
            order.setLineNames(toJson(new ArrayList<>(lineNameSet)));
            order.setDistanceKm(distanceKm);
            order.setDurationMinutes(durationMinutes);
            order.setPrice(newPrice);
            updated = true;
        }

        // 状态修改（需要 roleCode >= 3），校验合法的状态流转
        if (body.containsKey("status")) {
            if (roleCode < 3) throw new BusinessException(ErrorCode.FORBIDDEN);
            int newStatus = Integer.parseInt(String.valueOf(body.get("status")));
            int oldStatus = order.getStatus();
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
            }
            order.setStatus(newStatus);
            // 同步关键时间字段
            if (newStatus == 1 && order.getPayTime() == null) {
                order.setPayTime(now);
            } else if (newStatus == 2 && order.getExitTime() == null) {
                order.setExitTime(now);
            } else if (newStatus == 4) {
                order.setRefundTime(now);
            }
            updated = true;
        }

        // 进出站时间修改（roleCode >= 2 即可）
        if (body.containsKey("entryTime")) {
            String entryTimeStr = String.valueOf(body.get("entryTime"));
            if (!entryTimeStr.isEmpty() && !"null".equals(entryTimeStr)) {
                order.setEntryTime(LocalDateTime.parse(entryTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            updated = true;
        }
        if (body.containsKey("exitTime")) {
            String exitTimeStr = String.valueOf(body.get("exitTime"));
            if (!exitTimeStr.isEmpty() && !"null".equals(exitTimeStr)) {
                order.setExitTime(LocalDateTime.parse(exitTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            updated = true;
        }

        if (updated) {
            order.setUpdateTime(now);
            updateById(order);
            log.info("管理员{}修改订单{}", roleCode, order.getOrderNo());
        }
    }

    @Transactional
    @Override
    public void adminDeleteOrder(Long orderId, Integer roleCode) {
        if (roleCode == null || roleCode < 3) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        TicketOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        removeById(orderId);
        log.info("管理员{}删除订单{}", roleCode, order.getOrderNo());
    }

    @Transactional
    @Override
    public Map<String, Object> refreshQrCode(Long userId, Long orderId) {
        TicketOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 1) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }
        String newQrCode = UUID.randomUUID().toString().replace("-", "");
        int qrValidityHours = configService.getConfigInt("ticket.qr_validity_hours", 24);
        LocalDateTime now = LocalDateTime.now();
        order.setQrCode(newQrCode);
        order.setQrExpireTime(now.plusHours(qrValidityHours));
        order.setUpdateTime(now);
        updateById(order);
        log.info("用户{}刷新订单{}二维码, 新有效期至{}", userId, order.getOrderNo(), order.getQrExpireTime());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qrCode", newQrCode);
        result.put("qrExpireTime", order.getQrExpireTime());
        return result;
    }

    @Transactional
    @Override
    public Map<String, Object> verifyQrCode(String qrCode) {
        expireOrders();
        TicketOrder order = getOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getQrCode, qrCode));
        if (order == null) {
            throw new BusinessException(ErrorCode.TICKET_QR_INVALID);
        }
        if (order.getStatus() == 3) {
            throw new BusinessException(ErrorCode.TICKET_QR_EXPIRED);
        }
        if (order.getStatus() == 2) {
            throw new BusinessException(ErrorCode.TICKET_QR_USED);
        }
        if (order.getStatus() != 1) {
            throw new BusinessException(ErrorCode.TICKET_ORDER_STATUS_ERROR);
        }

        // 原子标记为已使用，防止并发重复核销
        LocalDateTime now = LocalDateTime.now();
        boolean ok = update(new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, order.getId())
                .eq(TicketOrder::getStatus, 1)
                .set(TicketOrder::getStatus, 2)
                .set(TicketOrder::getExitTime, now)
                .set(TicketOrder::getUpdateTime, now));
        if (!ok) {
            throw new BusinessException(ErrorCode.TICKET_QR_USED);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("startStationName", order.getStartStationName());
        result.put("endStationName", order.getEndStationName());
        result.put("stationCount", order.getStationCount());
        result.put("price", order.getPrice());
        result.put("status", 2);
        result.put("qrExpireTime", order.getQrExpireTime());
        log.info("核销订单{}, 二维码{}", order.getOrderNo(), qrCode);

        try {
            messageService.sendOrderMessage("ORDER_USED",
                    "车票已核销",
                    String.format("订单 %s 已核销使用", order.getOrderNo()),
                    order.getUserId(), order.getId());
        } catch (Exception e) { log.warn("发送核销消息失败", e); }

        return result;
    }

    @Transactional
    @Override
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        int paymentTimeoutHours = configService.getConfigInt("ticket.payment_timeout_hours", 24);
        // 待支付超过配置时间 → 已过期
        List<TicketOrder> unpaid = list(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, 0)
                .lt(TicketOrder::getOrderTime, now.minusHours(paymentTimeoutHours)));
        for (TicketOrder o : unpaid) {
            o.setStatus(3);
            o.setUpdateTime(now);
        }
        // 已支付但二维码过期 → 已过期
        List<TicketOrder> paid = list(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, 1)
                .lt(TicketOrder::getQrExpireTime, now));
        for (TicketOrder o : paid) {
            o.setStatus(3);
            o.setUpdateTime(now);
        }
        List<TicketOrder> all = new ArrayList<>();
        all.addAll(unpaid);
        all.addAll(paid);
        if (!all.isEmpty()) {
            updateBatchById(all);
            log.info("批量过期{}个订单", all.size());
            // 为每个过期订单发送消息
            for (TicketOrder o : all) {
                try {
                    messageService.sendOrderMessage("ORDER_EXPIRED",
                            "订单已过期",
                            String.format("订单 %s 已过期", o.getOrderNo()),
                            o.getUserId(), o.getId());
                } catch (Exception e) { log.warn("发送过期消息失败 orderId={}", o.getId(), e); }
            }
        }
    }

    @Override
    public List<TicketOrder> getUserOrders(Long userId) {
        expireOrders();
        return list(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getUserId, userId)
                .orderByDesc(TicketOrder::getOrderTime));
    }

    // ═══════════════════════════════════════════
    //  BFS 最短路径（使用传入的图，不查库）
    // ═══════════════════════════════════════════

    private List<Long> bfsShortestPath(Long startId, Long endId, Map<Long, List<Long>> graph) {
        // 目标站点不在图中 → 不可达
        if (!graph.containsKey(endId)) {
            return null;
        }

        Queue<Long> queue = new LinkedList<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        queue.offer(startId);
        visited.add(startId);
        parent.put(startId, null);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(endId)) {
                List<Long> path = new ArrayList<>();
                Long node = endId;
                while (node != null) {
                    path.add(node);
                    node = parent.get(node);
                }
                Collections.reverse(path);
                return path;
            }
            List<Long> neighbors = graph.getOrDefault(current, Collections.emptyList());
            for (Long neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.offer(neighbor);
                }
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  路径验证（使用传入的站点 Map，不查库）
    // ═══════════════════════════════════════════

    private double validatePathAndGetDistance(List<Long> stationIdPath, Map<Long, MetroStation> stationMap) {
        if (stationIdPath == null || stationIdPath.size() < 2) {
            throw new BusinessException(ErrorCode.TICKET_ROUTE_NOT_FOUND);
        }

        Map<String, Object> params = configService.getConfigObject(
                "ticket.estimate_params", new TypeReference<Map<String, Object>>() {});
        double kmPerStop = getDouble(params, "kmPerStop", 1.8);

        double totalKm = 0;
        int fallbackCount = 0;

        for (int i = 0; i < stationIdPath.size() - 1; i++) {
            MetroStation cur = stationMap.get(stationIdPath.get(i));
            MetroStation next = stationMap.get(stationIdPath.get(i + 1));
            if (cur == null || next == null) {
                throw new BusinessException(ErrorCode.TICKET_ROUTE_NOT_FOUND);
            }

            // 验证 cur 和 next 互为邻居
            boolean curToNext = isNeighbor(cur, next.getId());
            boolean nextToCur = isNeighbor(next, cur.getId());
            if (!curToNext && !nextToCur) {
                log.warn("路径验证失败：站点 {}({}) 和 {}({}) 之间无邻接关系",
                        cur.getStationName(), cur.getId(), next.getStationName(), next.getId());
                throw new BusinessException(ErrorCode.TICKET_ROUTE_NOT_FOUND);
            }

            // 优先从缓存读取真实距离（单位：公里）
            Double distKm = getNeighborDistance(cur, next.getId());
            if (distKm == null) {
                distKm = getNeighborDistance(next, cur.getId());
            }
            if (distKm != null && distKm > 0) {
                totalKm += distKm;
            } else {
                totalKm += kmPerStop;
                fallbackCount++;
            }
        }

        if (fallbackCount > 0) {
            log.info("路径距离计算：{} 段使用真实数据，{} 段使用默认参数 {}km",
                    stationIdPath.size() - 1 - fallbackCount, fallbackCount, kmPerStop);
        }

        return totalKm;
    }

    /**
     * 校验订单状态流转是否合法
     */
    private boolean isValidStatusTransition(int from, int to) {
        // 0=待支付, 1=已支付, 2=已使用, 3=已过期, 4=已退票, 5=审核中
        return switch (from) {
            case 0 -> to == 1 || to == 3;           // 待支付 → 已支付/已过期
            case 1 -> to == 2 || to == 3 || to == 5; // 已支付 → 已使用/已过期/审核中
            case 2 -> false;                         // 已使用，终态
            case 3 -> false;                         // 已过期，终态
            case 4 -> false;                         // 已退票，终态
            case 5 -> to == 1 || to == 4;            // 审核中 → 已支付(拒绝)/已退票(批准)
            default -> false;
        };
    }

    private boolean isNeighbor(MetroStation station, Long neighborId) {
        String idStr = String.valueOf(neighborId);
        List<String> nextIds = parseJsonArray(station.getNextStationIds());
        List<String> prevIds = parseJsonArray(station.getPrevStationIds());
        return nextIds.contains(idStr) || prevIds.contains(idStr);
    }

    // ═══════════════════════════════════════════
    //  距离查询
    // ═══════════════════════════════════════════

    private Double getNeighborDistance(MetroStation station, Long neighborId) {
        String neighborIdStr = String.valueOf(neighborId);
        List<String> nextIds = parseJsonArray(station.getNextStationIds());
        List<String> prevIds = parseJsonArray(station.getPrevStationIds());
        List<String> nextDists = parseJsonArray(station.getNextStationDistances());
        List<String> prevDists = parseJsonArray(station.getPrevStationDistances());

        int idx = nextIds.indexOf(neighborIdStr);
        if (idx >= 0 && idx < nextDists.size()) {
            return parseDouble(nextDists.get(idx));
        }
        idx = prevIds.indexOf(neighborIdStr);
        if (idx >= 0 && idx < prevDists.size()) {
            return parseDouble(prevDists.get(idx));
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  票价计算
    // ═══════════════════════════════════════════

    private int calculatePrice(int stationCount) {
        List<Map<String, Object>> tiers = configService.getConfigObject(
                "ticket.price_tiers", TIER_LIST);
        if (tiers == null || tiers.isEmpty()) {
            return 2;
        }
        for (Map<String, Object> tier : tiers) {
            int maxStops = ((Number) tier.get("maxStops")).intValue();
            if (stationCount <= maxStops) {
                return ((Number) tier.get("price")).intValue();
            }
        }
        Map<String, Object> lastTier = tiers.get(tiers.size() - 1);
        return ((Number) lastTier.get("price")).intValue();
    }

    // ═══════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return new ArrayList<>(MAPPER.readValue(json, STRING_LIST));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        if (map == null || map.get(key) == null) return defaultVal;
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000));
        return "TK" + timestamp + random;
    }
}
