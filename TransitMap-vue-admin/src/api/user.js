import request from '@/utils/request'

export function adminLogin(data) {
  return request.post('/user/login/admin', data)
}

export function superAdminLogin(data) {
  return request.post('/user/login/super-admin', data)
}

export function rootAdminLogin(data) {
  return request.post('/user/login/root-admin', data)
}

export function userLogout() {
  return request.post('/user/logout')
}

export function getUserInfo() {
  return request.get('/user/info')
}

export function getCaptchaImage() {
  return request.get('/utils/captcha/image')
}

export function getUserList(params) {
  return request.get('/manage/user/list', { params })
}

export function getUserDetail(id) {
  return request.get(`/manage/user/${id}`)
}

export function createUser(data) {
  return request.post('/manage/user', data)
}

export function updateUser(id, data) {
  return request.put(`/manage/user/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/manage/user/${id}`)
}

export function updateUserStatus(id, statusCode) {
  return request.put(`/manage/user/${id}/status`, {}, { params: { statusCode } })
}

export function updateProfile(data) {
  return request.put('/user/profile', data)
}

export function updatePassword(data) {
  return request.put('/user/password', data)
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
