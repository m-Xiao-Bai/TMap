import request from '@/utils/request'

export function geocodeSingle(stationId) {
  return request.post(`/manage/geocode/single/${stationId}`)
}

export function geocodeBatch(data) {
  return request.post('/manage/geocode/batch', data)
}

export function getGeocodeStatus(cityId) {
  return request.get('/manage/geocode/status', { params: { cityId } })
}

export function geocodeQuery(data) {
  return request.post('/manage/geocode/query', data)
}

export function geocodeReplaceCoordinates(data) {
  return request.post('/manage/geocode/replace-coordinates', data)
}
