package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.entity.AgentDecryptRequest;
import com.mu.transitmap.entity.AgentTokenUsage;
import com.mu.transitmap.entity.ChatIntentLog;
import com.mu.transitmap.entity.ChatMessage;
import com.mu.transitmap.entity.ChatSession;
import com.mu.transitmap.entity.SystemMessage;
import com.mu.transitmap.entity.User;
import com.mu.transitmap.mapper.AgentDecryptRequestMapper;
import com.mu.transitmap.mapper.AgentTokenUsageMapper;
import com.mu.transitmap.mapper.ChatIntentLogMapper;
import com.mu.transitmap.mapper.ChatMessageMapper;
import com.mu.transitmap.mapper.ChatSessionMapper;
import com.mu.transitmap.mapper.SystemMessageMapper;
import com.mu.transitmap.mapper.UserMapper;
import com.mu.transitmap.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent 会话管理 REST 接口（管理后台）
 * 仅提供脱敏统计数据，明文消息需更高权限（M3 后续）
 */
@RestController
@RequestMapping("/chat-manage")
public class ChatSessionManageController {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatIntentLogMapper chatIntentLogMapper;

    @Autowired
    private AgentTokenUsageMapper agentTokenUsageMapper;

    @Autowired
    private AgentDecryptRequestMapper decryptRequestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemMessageMapper systemMessageMapper;

    /**
     * 会话分页列表
     */
    @GetMapping("/session/page")
    public Result<Page<ChatSession>> sessionPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Page<ChatSession> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getLastMsgAt);

        if (userId != null) {
            wrapper.eq(ChatSession::getUserId, userId);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(ChatSession::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(ChatSession::getCreateTime, LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        Page<ChatSession> result = chatSessionMapper.selectPage(page, wrapper);
        return Result.success(result);
    }

    /**
     * 会话详情：默认脱敏；如果当前管理员对该 session 有有效的 APPROVED 授权，返回明文
     */
    @GetMapping("/session/{id}/detail")
    public Result<Map<String, Object>> sessionDetail(@PathVariable Long id, HttpServletRequest request) {
        ChatSession session = chatSessionMapper.selectById(id);
        if (session == null) return Result.fail(404, "会话不存在");

        Long currentUserId = (Long) request.getAttribute("userId");
        boolean canSeePlain = hasValidDecryptAuth(id, currentUserId);

        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, id)
                .orderByAsc(ChatMessage::getCreateTime));

        List<Map<String, Object>> outMsgs = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("role", m.getRole());
            mm.put("length", m.getContent() == null ? 0 : m.getContent().length());
            mm.put("intent", m.getIntent());
            mm.put("inputMethod", m.getInputMethod());
            mm.put("tokensIn", m.getTokensIn());
            mm.put("tokensOut", m.getTokensOut());
            mm.put("latencyMs", m.getLatencyMs());
            mm.put("feedback", m.getFeedback());
            mm.put("createTime", m.getCreateTime());
            if (canSeePlain) {
                // 已被授权，返回明文
                mm.put("content", m.getContent());
                mm.put("extras", m.getExtras());
            }
            outMsgs.add(mm);
        }

        List<ChatIntentLog> logs = chatIntentLogMapper.selectList(new LambdaQueryWrapper<ChatIntentLog>()
                .eq(ChatIntentLog::getSessionId, id)
                .orderByAsc(ChatIntentLog::getCreateTime));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("messages", outMsgs);
        result.put("logs", logs);
        result.put("canSeePlain", canSeePlain);
        return Result.success(result);
    }

    // ===== 双人审批 =====

    /**
     * 申请查看明文（任意管理员可发起）
     */
    @PostMapping("/decrypt/request")
    public Result<AgentDecryptRequest> createDecryptRequest(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long sessionId = body.get("sessionId") == null ? null : Long.valueOf(body.get("sessionId").toString());
        String reason = body.getOrDefault("reason", "").toString();
        if (sessionId == null) return Result.fail(400, "缺少 sessionId");
        if (reason.isBlank()) return Result.fail(400, "请填写申请理由");

        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) return Result.fail(401, "未登录");

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) return Result.fail(404, "会话不存在");

        User u = userMapper.selectById(currentUserId);
        String reqName = u != null ? u.getUsername() : ("user-" + currentUserId);

        AgentDecryptRequest req = new AgentDecryptRequest();
        req.setSessionId(sessionId);
        req.setRequesterId(currentUserId);
        req.setRequesterName(reqName);
        req.setReason(reason);
        req.setStatus(AgentDecryptRequest.STATUS_PENDING);
        req.setCreateTime(LocalDateTime.now());
        decryptRequestMapper.insert(req);

        // 通知所有 roleCode >= 3 且不是申请人本人的管理员
        notifyApprovers(req);

        return Result.success(req);
    }

    /**
     * 给所有有审批权限（且非申请人本人）的管理员发系统消息
     */
    private void notifyApprovers(AgentDecryptRequest req) {
        try {
            List<User> approvers = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .ge(User::getRoleCode, 3)
                            .ne(User::getId, req.getRequesterId()));
            if (approvers == null || approvers.isEmpty()) return;
            String title = "明文查看申请待审批 · " + req.getRequesterName();
            String content = String.format(
                    "%s 申请查看会话 #%d 的明文对话\n申请理由：%s\n请前往「明文审批」页面处理。",
                    req.getRequesterName(), req.getSessionId(), req.getReason());
            LocalDateTime now = LocalDateTime.now();
            for (User a : approvers) {
                SystemMessage m = new SystemMessage();
                m.setType("AGENT_DECRYPT_REQUEST");
                m.setTitle(title);
                m.setContent(content);
                m.setUserId(a.getId());
                m.setTarget(2); // 仅管理员
                m.setIsRead(0);
                m.setCreateTime(now);
                systemMessageMapper.insert(m);
            }
        } catch (Exception e) {
            // 通知失败不应阻塞主流程
        }
    }

    /**
     * 待审批列表（角色 ≥ 3 可见）
     */
    @GetMapping("/decrypt/pending")
    public Result<List<AgentDecryptRequest>> pendingRequests(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("roleCode");
        if (role == null || role < 3) return Result.fail(403, "无权限");
        List<AgentDecryptRequest> list = decryptRequestMapper.selectList(
                new LambdaQueryWrapper<AgentDecryptRequest>()
                        .eq(AgentDecryptRequest::getStatus, AgentDecryptRequest.STATUS_PENDING)
                        .orderByDesc(AgentDecryptRequest::getCreateTime));
        return Result.success(list);
    }

    /**
     * 审批（必须不是申请人本人 + 角色 ≥ 3）
     */
    @PostMapping("/decrypt/{id}/approve")
    public Result<Void> approveDecrypt(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("roleCode");
        if (currentUserId == null) return Result.fail(401, "未登录");
        if (role == null || role < 3) return Result.fail(403, "无权限审批，需要超级管理员及以上");

        AgentDecryptRequest req = decryptRequestMapper.selectById(id);
        if (req == null) return Result.fail(404, "申请不存在");
        if (!AgentDecryptRequest.STATUS_PENDING.equals(req.getStatus())) {
            return Result.fail(400, "申请已被处理");
        }
        // 双人原则：审批人不能是申请人
        if (currentUserId.equals(req.getRequesterId())) {
            return Result.fail(403, "不能审批自己的申请，需要另一位超级管理员处理");
        }

        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String note = body.getOrDefault("note", "").toString();

        User u = userMapper.selectById(currentUserId);
        req.setApproverId(currentUserId);
        req.setApproverName(u != null ? u.getUsername() : ("user-" + currentUserId));
        req.setApproverNote(note);
        req.setApproveTime(LocalDateTime.now());
        if (approve) {
            req.setStatus(AgentDecryptRequest.STATUS_APPROVED);
            req.setExpireTime(LocalDateTime.now().plusHours(24));
        } else {
            req.setStatus(AgentDecryptRequest.STATUS_REJECTED);
        }
        decryptRequestMapper.updateById(req);

        // 通知申请人审批结果
        notifyRequester(req, approve);

        return Result.success(null);
    }

    /**
     * 通知申请人审批结果
     */
    private void notifyRequester(AgentDecryptRequest req, boolean approved) {
        try {
            SystemMessage m = new SystemMessage();
            m.setType("AGENT_DECRYPT_RESULT");
            m.setTitle(approved ? "明文查看申请已通过" : "明文查看申请已拒绝");
            String body = approved
                    ? String.format("会话 #%d 的明文查看权限已开通，有效期至 %s。审批人：%s",
                            req.getSessionId(), req.getExpireTime(), req.getApproverName())
                    : String.format("会话 #%d 的明文查看申请被拒绝。审批人：%s%s",
                            req.getSessionId(), req.getApproverName(),
                            (req.getApproverNote() != null && !req.getApproverNote().isBlank())
                                    ? "\n备注：" + req.getApproverNote() : "");
            m.setContent(body);
            m.setUserId(req.getRequesterId());
            m.setTarget(2);
            m.setIsRead(0);
            m.setCreateTime(LocalDateTime.now());
            systemMessageMapper.insert(m);
        } catch (Exception ignored) {}
    }

    /**
     * 我的申请记录
     */
    @GetMapping("/decrypt/my")
    public Result<List<AgentDecryptRequest>> myRequests(HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) return Result.fail(401, "未登录");
        return Result.success(decryptRequestMapper.selectList(
                new LambdaQueryWrapper<AgentDecryptRequest>()
                        .eq(AgentDecryptRequest::getRequesterId, currentUserId)
                        .orderByDesc(AgentDecryptRequest::getCreateTime)
                        .last("LIMIT 20")));
    }

    /**
     * 检查当前管理员是否对该 session 有有效的 APPROVED 授权
     */
    private boolean hasValidDecryptAuth(Long sessionId, Long userId) {
        if (userId == null) return false;
        AgentDecryptRequest req = decryptRequestMapper.selectOne(
                new LambdaQueryWrapper<AgentDecryptRequest>()
                        .eq(AgentDecryptRequest::getSessionId, sessionId)
                        .eq(AgentDecryptRequest::getRequesterId, userId)
                        .eq(AgentDecryptRequest::getStatus, AgentDecryptRequest.STATUS_APPROVED)
                        .orderByDesc(AgentDecryptRequest::getApproveTime)
                        .last("LIMIT 1"));
        if (req == null) return false;
        return req.getExpireTime() != null && req.getExpireTime().isAfter(LocalDateTime.now());
    }

    /**
     * 用量统计：每日 token 用量、请求数、成本
     * range: 默认近 7 天
     */
    @GetMapping("/usage/daily")
    public Result<List<AgentTokenUsage>> dailyUsage(
            @RequestParam(defaultValue = "7") int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<AgentTokenUsage> rows = agentTokenUsageMapper.selectList(
                new LambdaQueryWrapper<AgentTokenUsage>()
                        .ge(AgentTokenUsage::getStatDate, from)
                        .orderByAsc(AgentTokenUsage::getStatDate));
        return Result.success(rows);
    }

    /**
     * 用量汇总：总请求数/总 token/总成本
     */
    @GetMapping("/usage/summary")
    public Result<Map<String, Object>> usageSummary(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        List<AgentTokenUsage> rows = agentTokenUsageMapper.selectList(
                new LambdaQueryWrapper<AgentTokenUsage>()
                        .ge(AgentTokenUsage::getStatDate, from));

        long totalReq = 0, totalIn = 0, totalOut = 0, totalCost = 0;
        for (AgentTokenUsage r : rows) {
            totalReq += r.getRequestCount() == null ? 0 : r.getRequestCount();
            totalIn += r.getTokensIn() == null ? 0 : r.getTokensIn();
            totalOut += r.getTokensOut() == null ? 0 : r.getTokensOut();
            totalCost += r.getCostCents() == null ? 0 : r.getCostCents();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("totalRequests", totalReq);
        result.put("totalTokensIn", totalIn);
        result.put("totalTokensOut", totalOut);
        result.put("totalCostCents", totalCost);
        result.put("totalCostYuan", totalCost / 100.0);
        return Result.success(result);
    }

    /**
     * 反馈分析：差评最多的消息（按 feedback=-1 排序）
     */
    @GetMapping("/feedback/bad")
    public Result<List<Map<String, Object>>> badFeedback(
            @RequestParam(defaultValue = "20") int limit) {
        List<ChatMessage> msgs = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getFeedback, -1)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT " + limit));
        List<Map<String, Object>> result = new ArrayList<>(msgs.size());
        for (ChatMessage m : msgs) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("sessionId", m.getSessionId());
            mm.put("preview", m.getContent() == null ? "" :
                    (m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "..." : m.getContent()));
            mm.put("intent", m.getIntent());
            mm.put("createTime", m.getCreateTime());
            result.add(mm);
        }
        return Result.success(result);
    }

    /**
     * 输入方式占比（text / voice / chip）
     */
    @GetMapping("/stats/input-method")
    public Result<Map<String, Long>> inputMethodStats(
            @RequestParam(defaultValue = "7") int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<ChatMessage> msgs = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getRole, "user")
                        .ge(ChatMessage::getCreateTime, from));
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("text", 0L);
        stats.put("voice", 0L);
        stats.put("chip", 0L);
        for (ChatMessage m : msgs) {
            String key = m.getInputMethod() == null ? "text" : m.getInputMethod();
            stats.merge(key, 1L, Long::sum);
        }
        return Result.success(stats);
    }

    /**
     * 节点失败率 TOP（按 chat_intent_log 统计）
     */
    @GetMapping("/stats/node-failures")
    public Result<List<Map<String, Object>>> nodeFailures(
            @RequestParam(defaultValue = "1") int days) {
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        List<ChatIntentLog> logs = chatIntentLogMapper.selectList(
                new LambdaQueryWrapper<ChatIntentLog>()
                        .ge(ChatIntentLog::getCreateTime, from));
        Map<String, long[]> stats = new LinkedHashMap<>(); // [total, fail, latencySum]
        for (ChatIntentLog log : logs) {
            String name = log.getNodeName() == null ? "Unknown" : log.getNodeName();
            long[] s = stats.computeIfAbsent(name, k -> new long[3]);
            s[0]++;
            if (log.getSuccess() != null && log.getSuccess() == 0) s[1]++;
            s[2] += (log.getLatencyMs() == null ? 0 : log.getLatencyMs());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : stats.entrySet()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("node", e.getKey());
            r.put("total", e.getValue()[0]);
            r.put("failures", e.getValue()[1]);
            r.put("failureRate", e.getValue()[0] == 0 ? 0
                    : Math.round(e.getValue()[1] * 10000.0 / e.getValue()[0]) / 100.0);
            r.put("avgLatencyMs", e.getValue()[0] == 0 ? 0
                    : Math.round(e.getValue()[2] * 1.0 / e.getValue()[0]));
            result.add(r);
        }
        result.sort((a, b) -> Double.compare((double) b.get("failureRate"), (double) a.get("failureRate")));
        return Result.success(result);
    }
}
