import request from '@/utils/request'

export function getCountryList(params) {
  return request.get('/manage/country/list', { params })
}

export function getCountryAll() {
  return request.get('/manage/country/all')
}

export function getCountryDetail(id) {
  return request.get(`/manage/country/${id}`)
}

export function createCountry(data) {
  return request.post('/manage/country', data)
}

export function updateCountry(id, data) {
  return request.put(`/manage/country/${id}`, data)
}

export function deleteCountry(id) {
  return request.delete(`/manage/country/${id}`)
}

export function batchDeleteCountries(ids) {
  return request.delete('/manage/country/batch', { data: { ids } })
}

export function batchImportCountries(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/manage/country/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
