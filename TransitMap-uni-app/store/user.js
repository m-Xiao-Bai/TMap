import { reactive } from 'vue'

const state = reactive({
    token: uni.getStorageSync('token') || '',
    userId: uni.getStorageSync('userId') || '',
    username: uni.getStorageSync('username') || '',
    avatar: uni.getStorageSync('avatar') || '',
    email: uni.getStorageSync('email') || '',
    mobile: uni.getStorageSync('mobile') || '',
    roleCode: parseInt(uni.getStorageSync('roleCode') || '0')
})

export function useUserStore() {
    const setUser = (data) => {
        state.token = data.token || ''
        state.userId = String(data.id || '')
        state.username = data.username || ''
        state.avatar = data.avatar || ''
        state.email = data.email || ''
        state.mobile = data.mobile || ''
        state.roleCode = data.roleCode || 0

        uni.setStorageSync('token', state.token)
        uni.setStorageSync('userId', state.userId)
        uni.setStorageSync('username', state.username)
        uni.setStorageSync('avatar', state.avatar)
        uni.setStorageSync('email', state.email)
        uni.setStorageSync('mobile', state.mobile)
        uni.setStorageSync('roleCode', String(state.roleCode))
    }

    const clearUser = () => {
        state.token = ''
        state.userId = ''
        state.username = ''
        state.avatar = ''
        state.email = ''
        state.mobile = ''
        state.roleCode = 0

        uni.removeStorageSync('token')
        uni.removeStorageSync('userId')
        uni.removeStorageSync('username')
        uni.removeStorageSync('avatar')
        uni.removeStorageSync('email')
        uni.removeStorageSync('mobile')
        uni.removeStorageSync('roleCode')
    }

    const isLoggedIn = () => !!state.token

    return {
        state,
        setUser,
        clearUser,
        isLoggedIn
    }
}
