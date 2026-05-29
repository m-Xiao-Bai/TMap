import request from '@/utils/request'

// 获取所有 agent.* 配置
export function getAgentConfigs() {
  return request.get('/manage/agent-config/all')
}

// 批量更新 agent.* 配置
export function updateAgentConfigs(configs) {
  return request.put('/manage/agent-config/update', configs)
}

// 测试 LLM 连通性
export function testLlm() {
  return request.post('/manage/agent-config/test-llm')
}

// 测试高德地图 API 连通性
export function testAmap() {
  return request.post('/manage/agent-config/test-amap')
}

// 获取引擎状态
export function getEngineStatus() {
  return request.get('/manage/agent-config/engine-status')
}

// 手动触发 Python 健康检查
export function checkPythonHealth() {
  return request.post('/manage/agent-config/check-python-health')
}
