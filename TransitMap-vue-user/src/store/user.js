import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const username = ref(localStorage.getItem('username') || '')
  const roleCode = ref(Number(localStorage.getItem('roleCode')) || 0)
  const role = ref(localStorage.getItem('role') || '')
  const avatar = ref(localStorage.getItem('avatar') || '')
  const email = ref(localStorage.getItem('email') || '')
  const mobile = ref(localStorage.getItem('mobile') || '')

  // 匿名用户标识（首次访问时生成，持久化到 localStorage）
  function getAnonToken() {
    let anon = localStorage.getItem('anon_token')
    if (!anon) {
      try {
        anon = 'anon_' + crypto.randomUUID()
      } catch {
        // 非 secure context（http://非localhost）下 randomUUID 不可用，退化到时间戳+随机数
        anon = 'anon_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 10)
      }
      localStorage.setItem('anon_token', anon)
    }
    return anon
  }
  const anonToken = getAnonToken()

  function setUser(data) {
    if (!data) return
    token.value = data.token || ''
    userId.value = data.id || ''
    username.value = data.username || ''
    roleCode.value = data.roleCode || 0
    role.value = data.role || ''
    avatar.value = data.avatar || ''
    email.value = data.email || ''
    mobile.value = data.mobile || ''

    localStorage.setItem('token', data.token || '')
    localStorage.setItem('userId', data.id || '')
    localStorage.setItem('username', data.username || '')
    localStorage.setItem('roleCode', data.roleCode || 0)
    localStorage.setItem('role', data.role || '')
    localStorage.setItem('avatar', data.avatar || '')
    localStorage.setItem('email', data.email || '')
    localStorage.setItem('mobile', data.mobile || '')
  }

  function clearUser() {
    token.value = ''
    userId.value = ''
    username.value = ''
    roleCode.value = 0
    role.value = ''
    avatar.value = ''
    email.value = ''
    mobile.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('roleCode')
    localStorage.removeItem('role')
    localStorage.removeItem('avatar')
    localStorage.removeItem('email')
    localStorage.removeItem('mobile')
  }

  function isLoggedIn() {
    return !!token.value
  }

  function getRoleName() {
    const roleMap = { 1: 'user', 2: 'admin', 3: 'super-admin', 4: 'root-admin' }
    return roleMap[roleCode.value] || 'user'
  }

  return {
    token, userId, username, roleCode, role, avatar, email, mobile, anonToken,
    setUser, clearUser, isLoggedIn, getRoleName
  }
})
