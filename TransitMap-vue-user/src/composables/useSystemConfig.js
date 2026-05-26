import { ref } from 'vue'
import { getPublicConfigs } from '@/api/public'

const configMap = ref(null)
const loading = ref(false)
let fetchPromise = null

async function ensureLoaded() {
  if (configMap.value) return
  if (fetchPromise) return fetchPromise
  loading.value = true
  fetchPromise = getPublicConfigs()
    .then(res => {
      const map = {}
      for (const cfg of (res.data || [])) {
        map[cfg.configKey] = cfg.configValue
      }
      configMap.value = map
    })
    .catch(err => {
      console.error('[useSystemConfig] fetch failed:', err)
      configMap.value = {}
    })
    .finally(() => {
      loading.value = false
      fetchPromise = null
    })
  return fetchPromise
}

export function useSystemConfig() {
  function getConfigValue(key, fallback = null) {
    if (!configMap.value) return fallback
    return configMap.value[key] ?? fallback
  }

  function getConfigJson(key, fallback = null) {
    const raw = getConfigValue(key)
    if (raw == null) return fallback
    try { return JSON.parse(raw) } catch { return fallback }
  }

  return { configMap, loading, ensureLoaded, getConfigValue, getConfigJson }
}
