import { get, post, put } from '@/utils/request'

// #ifdef H5
const UPLOAD_BASE = '/transitMap'
// #endif
// #ifndef H5
const UPLOAD_BASE = 'http://localhost:8888/transitMap'
// #endif

export const userLogin = (data) => post('/user/login', data)
export const userRegister = (data) => post('/user/register', data)
export const userLogout = () => post('/user/logout')
export const getUserInfo = () => get('/user/info')
export const wechatLogin = (code) => post('/user/wechat/login', { code })
export const updateProfile = (data) => put('/user/profile', data)
export const updatePassword = (data) => put('/user/password', data)
export const getCaptchaImage = () => get('/utils/captcha/image')
export const sendEmailCode = (email) => post('/utils/captcha/send?email=' + encodeURIComponent(email))

export const uploadAvatar = (filePath) => {
    return new Promise((resolve, reject) => {
        const token = uni.getStorageSync('token')
        uni.uploadFile({
            url: UPLOAD_BASE + '/user/avatar',
            filePath,
            name: 'file',
            header: token ? { Authorization: 'Bearer ' + token } : {},
            success: (res) => {
                try {
                    const data = JSON.parse(res.data)
                    if (data.code === 200) resolve(data)
                    else reject(data)
                } catch (e) { reject(e) }
            },
            fail: reject
        })
    })
}
