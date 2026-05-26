import request from '@/utils/request'

// 会话分页
export function getSessionPage(params) {
  return request.get('/chat-manage/session/page', { params })
}

// 会话详情（脱敏）
export function getSessionDetail(id) {
  return request.get(`/chat-manage/session/${id}/detail`)
}

// 每日 token 用量
export function getDailyUsage(days = 7) {
  return request.get('/chat-manage/usage/daily', { params: { days } })
}

// 用量汇总
export function getUsageSummary(days = 30) {
  return request.get('/chat-manage/usage/summary', { params: { days } })
}

// 差评消息
export function getBadFeedback(limit = 20) {
  return request.get('/chat-manage/feedback/bad', { params: { limit } })
}

// 输入方式占比
export function getInputMethodStats(days = 7) {
  return request.get('/chat-manage/stats/input-method', { params: { days } })
}

// ===== 双人审批 =====

// 申请查看明文
export function requestDecrypt(sessionId, reason) {
  return request.post('/chat-manage/decrypt/request', { sessionId, reason })
}

// 待审批列表（超级及以上可见）
export function getPendingDecrypts() {
  return request.get('/chat-manage/decrypt/pending')
}

// 审批
export function approveDecrypt(id, approve, note) {
  return request.post(`/chat-manage/decrypt/${id}/approve`, { approve, note })
}

// 我的申请记录
export function getMyDecryptRequests() {
  return request.get('/chat-manage/decrypt/my')
}
