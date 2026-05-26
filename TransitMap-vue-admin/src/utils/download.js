import axios from 'axios'
import { useUserStore } from '@/store/user'

const download = axios.create({
  baseURL: '/transitMap-admin',
  timeout: 30000,
  responseType: 'blob',
})

download.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers['Authorization'] = `Bearer ${userStore.token}`
  }
  return config
})

export function downloadExcel(url, params, filename) {
  return download.get(url, { params }).then((res) => {
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  })
}
