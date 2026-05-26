import request from '@/utils/request'

export function getMetroLineList(params) {
  return request.get('/manage/metro-line/list', { params })
}

export function getMetroLineDetail(id) {
  return request.get(`/manage/metro-line/${id}`)
}

export function createMetroLine(data) {
  return request.post('/manage/metro-line', data)
}

export function updateMetroLine(id, data) {
  return request.put(`/manage/metro-line/${id}`, data)
}

export function deleteMetroLine(id) {
  return request.delete(`/manage/metro-line/${id}`)
}

export function batchDeleteMetroLines(ids) {
  return request.delete('/manage/metro-line/batch', { data: { ids } })
}

export function batchImportMetroLines(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/manage/metro-line/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getLineOrderedStations(lineId) {
  return request.get(`/manage/metro-line/${lineId}/stations-ordered`)
}
