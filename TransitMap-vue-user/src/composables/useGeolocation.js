import { ref } from 'vue'
import { locate } from '@/api/agent'

export function useGeolocation() {
  const location = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function getLocation() {
    loading.value = true
    error.value = null

    // 优先使用浏览器定位
    if (navigator.geolocation) {
      try {
        const pos = await new Promise((resolve, reject) => {
          navigator.geolocation.getCurrentPosition(resolve, reject, {
            timeout: 5000,
            maximumAge: 300000
          })
        })
        location.value = {
          lat: pos.coords.latitude,
          lng: pos.coords.longitude
        }
        loading.value = false
        return location.value
      } catch (e) {
        // 浏览器定位失败，使用后端定位
      }
    }

    // 后端 IP 定位兜底
    try {
      const res = await locate()
      if (res.code === 200 && res.data) {
        location.value = {
          lat: res.data.lat,
          lng: res.data.lng,
          city: res.data.city
        }
      }
    } catch (e) {
      error.value = '定位失败'
    }

    loading.value = false
    return location.value
  }

  return { location, loading, error, getLocation }
}
