import request from '@/utils/request'

export function getPublicConfigs() {
  return request.get('/manage/system-config/public')
}

export function getAllConfigs() {
  return request.get('/manage/system-config/all')
}

export function updateConfigs(configs) {
  return request.put('/manage/system-config/update', configs)
}
