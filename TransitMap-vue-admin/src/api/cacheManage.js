import request from '@/utils/request'

export function getCacheStatus() {
  return request.get('/manage/cache/status')
}

export function clearCacheCategory(category) {
  return request.delete(`/manage/cache/clear/${category}`)
}
