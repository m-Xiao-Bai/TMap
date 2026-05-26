// #ifdef H5
const BASE_URL = '/transitMap'
// #endif
// #ifndef H5
const BASE_URL = 'http://localhost:8888/transitMap'
// #endif

const request = (options) => {
    return new Promise((resolve, reject) => {
        const token = uni.getStorageSync('token')
        const header = {
            'Content-Type': 'application/json',
            ...options.header
        }
        if (token) {
            header['Authorization'] = 'Bearer ' + token
        }

        uni.request({
            url: BASE_URL + options.url,
            method: options.method || 'GET',
            data: options.data,
            header,
            success: (res) => {
                if (res.statusCode === 200) {
                    const data = res.data
                    if (data.code === 200) {
                        resolve(data)
                    } else if (data.code === 102018 || data.code === 102019) {
                        uni.removeStorageSync('token')
                        uni.removeStorageSync('userInfo')
                        uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
                        setTimeout(() => {
                            uni.navigateTo({ url: '/pages/login/login' })
                        }, 1500)
                        reject(data)
                    } else {
                        uni.showToast({ title: data.message || '请求失败', icon: 'none' })
                        reject(data)
                    }
                } else if (res.statusCode === 401) {
                    uni.removeStorageSync('token')
                    uni.removeStorageSync('userInfo')
                    uni.showToast({ title: '请先登录', icon: 'none' })
                    setTimeout(() => {
                        uni.navigateTo({ url: '/pages/login/login' })
                    }, 1500)
                    reject(res)
                } else if (res.statusCode === 403) {
                    uni.showToast({ title: '没有权限', icon: 'none' })
                    reject(res)
                } else {
                    uni.showToast({ title: '服务器异常', icon: 'none' })
                    reject(res)
                }
            },
            fail: (err) => {
                uni.showToast({ title: '网络连接失败', icon: 'none' })
                reject(err)
            }
        })
    })
}

export const get = (url, data) => request({ url, method: 'GET', data })
export const post = (url, data) => request({ url, method: 'POST', data })
export const put = (url, data) => request({ url, method: 'PUT', data })
export const del = (url, data) => request({ url, method: 'DELETE', data })

export default request
