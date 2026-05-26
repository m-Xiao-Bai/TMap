import request from '@/utils/request'

// 获取或创建今天的会话
export function getTodaySession() {
  return request.get('/agent/session/today')
}

// 获取用户会话列表
export function getSessionList() {
  return request.get('/agent/session/list')
}

// 创建新会话
export function newSession() {
  return request.post('/agent/session/new')
}

// 删除会话
export function deleteSession(id) {
  return request.delete(`/agent/session/${id}`)
}

// 获取会话消息历史
export function getMessages(sessionId) {
  return request.get(`/agent/session/${sessionId}/messages`)
}

// 服务端定位
export function locate() {
  return request.post('/agent/locate')
}

// 站点/城市搜索联想
export function suggest(q, cityId, type = 'station') {
  return request.get('/agent/suggest', { params: { q, cityId, type } })
}

// 消息反馈
export function feedback(messageId, data) {
  return request.post(`/agent/message/${messageId}/feedback`, data)
}

// 获取欢迎快捷词
export function getWelcomeChips() {
  return request.get('/agent/chips/welcome')
}

// 查询当日配额（匿名用户）
export function getQuota() {
  return request.get('/agent/quota')
}

// 请求管理员添加新城市
export function requestCity(cityName) {
  return request.post('/agent/request-city', { cityName })
}
