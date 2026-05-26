import request from '@/utils/request'

export function getMetroStationList(params) {
  return request.get('/manage/metro-station/list', { params })
}

export function getMetroStationDetail(id) {
  return request.get(`/manage/metro-station/${id}`)
}

export function createMetroStation(data) {
  return request.post('/manage/metro-station', data)
}

export function updateMetroStation(id, data) {
  return request.put(`/manage/metro-station/${id}`, data)
}

export function deleteMetroStation(id) {
  return request.delete(`/manage/metro-station/${id}`)
}

export function batchDeleteMetroStations(ids) {
  return request.delete('/manage/metro-station/batch', { data: ids })
}

export function batchImportMetroStations(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/manage/metro-station/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getStationsByCityId(cityId) {
  return request.get(`/manage/metro-station/by-city/${cityId}`)
}

export function batchAssignLine(data) {
  return request.post('/manage/metro-station/batch-assign-line', data)
}

export function batchRemoveLine(data) {
  return request.post('/manage/metro-station/batch-remove-line', data)
}
