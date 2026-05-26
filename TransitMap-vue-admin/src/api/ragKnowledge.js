import request from '@/utils/request'

export function listKnowledge(params) {
  return request.get('/manage/rag-knowledge/page', { params })
}
export function createKnowledge(body) {
  return request.post('/manage/rag-knowledge/create', body)
}
export function updateKnowledge(id, body) {
  return request.put(`/manage/rag-knowledge/${id}`, body)
}
export function deleteKnowledge(id) {
  return request.delete(`/manage/rag-knowledge/${id}`)
}
export function backfillEmbedding(limit = 100) {
  return request.post(`/manage/rag-knowledge/backfill-embedding?limit=${limit}`)
}
export function rebuildEmbedding() {
  return request.post('/manage/rag-knowledge/rebuild-embedding')
}
export function getEmbeddingStatus() {
  return request.get('/manage/rag-knowledge/embedding-status')
}
