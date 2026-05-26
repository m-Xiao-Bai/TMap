import request from '@/utils/request'

export function getCityList(params) {
  return request.get('/manage/city/list', { params })
}

export function getCityDetail(id) {
  return request.get(`/manage/city/${id}`)
}

export function createCity(data) {
  return request.post('/manage/city', data)
}

export function updateCity(id, data) {
  return request.put(`/manage/city/${id}`, data)
}

export function deleteCity(id) {
  return request.delete(`/manage/city/${id}`)
}

export function batchDeleteCities(ids) {
  return request.delete('/manage/city/batch', { data: { ids } })
}

export function batchImportCities(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/manage/city/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getCityAll() {
  return request.get('/manage/city/all')
}
