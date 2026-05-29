import request from '@/utils/request'

// 获取质量统计
export function getQualityStats(params) {
  return request.get('/manage/quality/stats', { params })
}

// 获取低分对话列表
export function getLowScoreConversations(params) {
  return request.get('/manage/quality/low-score', { params })
}
