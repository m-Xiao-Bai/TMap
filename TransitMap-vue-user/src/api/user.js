import request from '@/utils/request'

export function userLogin(data) {
  return request.post('/user/login', data)
}

export function userRegister(data) {
  return request.post('/user/register', data)
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

export function sendEmailCode(email) {
  return request.post('/utils/captcha/send', null, { params: { email } })
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
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000,
  })
}
