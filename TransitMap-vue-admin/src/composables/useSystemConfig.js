import { reactive } from 'vue'
import { getPublicConfigs } from '@/api/systemConfig'

const defaults = {
  'station.status_map': '{"0":"未开通","1":"运营中","2":"建设中","3":"规划中","4":"已停运"}',
  'station.type_map': '{"0":"地下","1":"地面","2":"高架"}',
  'pagination.default_size': '10',
  'pagination.size_options': '[10,20,50,100]',
}

function parseJson(val, fallback) {
  if (!val) return fallback
  try { return JSON.parse(val) } catch { return fallback }
}

const state = reactive({
  loaded: false,
  statusMap: parseJson(defaults['station.status_map'], {}),
  typeMap: parseJson(defaults['station.type_map'], {}),
  defaultPageSize: 10,
  pageSizeOptions: parseJson(defaults['pagination.size_options'], []),
})

export function useSystemConfig() {
  const fetchConfigs = async () => {
    if (state.loaded) return
    try {
      const res = await getPublicConfigs()
      const list = res.data || []
      const map = {}
      list.forEach(c => { map[c.configKey] = c.configValue })
      applyConfig(map)
      state.loaded = true
    } catch {
      state.loaded = true
    }
  }

  function applyConfig(map) {
    state.statusMap = parseJson(map['station.status_map'], state.statusMap)
    state.typeMap = parseJson(map['station.type_map'], state.typeMap)
    const size = map['pagination.default_size']
    if (size != null) {
      const n = Number(size)
      if (!isNaN(n)) state.defaultPageSize = n
    }
    state.pageSizeOptions = parseJson(map['pagination.size_options'], state.pageSizeOptions)
  }

  return {
    state,
    fetchConfigs,
  }
}
