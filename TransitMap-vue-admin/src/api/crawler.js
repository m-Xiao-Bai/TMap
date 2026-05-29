import request from '@/utils/request'

// ── 爬取任务 ──

// 触发单城市爬取
export function triggerCrawl(data) {
  return request.post('/manage/crawler/trigger', data)
}

// 批量爬取
export function triggerBatchCrawl(data) {
  return request.post('/manage/crawler/batch', data)
}

// 查询任务状态
export function getTaskStatus(taskId) {
  return request.get(`/manage/crawler/task/${taskId}`)
}

// 获取所有任务列表
export function getAllTasks() {
  return request.get('/manage/crawler/tasks')
}

// 取消任务
export function cancelTask(taskId) {
  return request.delete(`/manage/crawler/task/${taskId}`)
}

// 健康检查
export function checkCrawlerHealth() {
  return request.get('/manage/crawler/health')
}

// ── 审核 ──

// 获取待审核列表
export function getPendingReviews(params) {
  return request.get('/manage/crawler/review/pending', { params })
}

// 批准审核
export function approveReview(reviewId) {
  return request.post(`/manage/crawler/review/approve/${reviewId}`)
}

// 拒绝审核
export function rejectReview(reviewId) {
  return request.post(`/manage/crawler/review/reject/${reviewId}`)
}
