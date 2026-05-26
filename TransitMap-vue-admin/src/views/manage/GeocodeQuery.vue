<template>
  <div class="manage-page">
    <div class="page-header">
      <h2><el-icon><Search /></el-icon> 地理编码查询</h2>
    </div>

    <!-- 查询表单 -->
    <el-card shadow="hover" class="query-card">
      <el-form :model="form" label-width="80px" @submit.prevent="handleQuery">
        <el-form-item>
          <template #label>
            <span class="required-label">地址</span>
          </template>
          <el-input v-model="form.address" placeholder="请输入地址，如：天安门广场" clearable />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="选填，如：北京（提高精度）" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 查询结果 -->
    <el-card v-if="result" shadow="hover" class="result-card">
      <template #header>
        <div class="card-hd">
          <el-icon><Location /></el-icon>
          <span>查询结果</span>
        </div>
      </template>

      <div class="result-grid">
        <div class="result-row">
          <div class="result-item">
            <span class="item-label">经度</span>
            <div class="item-value">
              <span class="coord-value">{{ result.lng }}</span>
              <el-button size="small" text type="primary" @click="copyText(result.lng)">复制</el-button>
            </div>
          </div>
          <div class="result-item">
            <span class="item-label">纬度</span>
            <div class="item-value">
              <span class="coord-value">{{ result.lat }}</span>
              <el-button size="small" text type="primary" @click="copyText(result.lat)">复制</el-button>
            </div>
          </div>
        </div>

        <el-divider content-position="left">结构化地址</el-divider>

        <div class="result-row">
          <div class="result-item">
            <span class="item-label">省份</span>
            <span class="item-value">{{ result.province || '—' }}</span>
          </div>
          <div class="result-item">
            <span class="item-label">城市</span>
            <span class="item-value">{{ result.city || '—' }}</span>
          </div>
          <div class="result-item">
            <span class="item-label">区县</span>
            <span class="item-value">{{ result.district || '—' }}</span>
          </div>
        </div>
        <div class="result-row">
          <div class="result-item">
            <span class="item-label">街道</span>
            <span class="item-value">{{ result.street || '—' }}</span>
          </div>
          <div class="result-item">
            <span class="item-label">门牌号</span>
            <span class="item-value">{{ result.streetNumber || '—' }}</span>
          </div>
          <div class="result-item">
            <span class="item-label">地址级别</span>
            <span class="item-value">
              <el-tag size="small">{{ result.level || '未知' }}</el-tag>
            </span>
          </div>
        </div>

        <el-divider content-position="left">完整地址</el-divider>

        <div class="formatted-address">
          <span>{{ result.formattedAddress || '—' }}</span>
          <el-button size="small" text type="primary" @click="copyText(result.formattedAddress)">复制</el-button>
        </div>
      </div>
    </el-card>

    <!-- 快速替换地理编码 -->
    <el-card v-if="result" shadow="hover" class="replace-card">
      <template #header>
        <div class="card-hd">
          <el-icon><Refresh /></el-icon>
          <span>快速替换地理编码</span>
        </div>
      </template>

      <el-alert
        title="选择一个地铁站，将查询到的经纬度替换该站点的地理编码。替换后会自动重新计算相邻站距离。"
        type="info" show-icon :closable="false" style="margin-bottom:16px"
      />

      <el-form label-width="80px">
        <el-form-item label="国家">
          <el-select
            v-model="selCountryId"
            placeholder="请选择国家"
            clearable
            filterable
            style="width:100%"
            @change="onCountryChange"
          >
            <el-option
              v-for="c in countries"
              :key="c.id"
              :label="c.countryName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="城市">
          <el-select
            v-model="selCityId"
            placeholder="请选择城市"
            clearable
            filterable
            style="width:100%"
            :disabled="!selCountryId"
            @change="onCityChange"
          >
            <el-option
              v-for="c in filteredCities"
              :key="c.id"
              :label="c.cityName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="线路">
          <el-select
            v-model="selLineId"
            placeholder="请选择线路"
            clearable
            filterable
            style="width:100%"
            :disabled="!selCityId"
            @change="onLineChange"
          >
            <el-option
              v-for="l in lines"
              :key="l.id"
              :label="l.lineName + (l.lineNo ? ' (' + l.lineNo + ')' : '')"
              :value="l.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="站点">
          <el-select
            v-model="selStationId"
            placeholder="请选择站点"
            clearable
            filterable
            style="width:100%"
            :disabled="!selLineId"
          >
            <el-option
              v-for="s in stations"
              :key="s.id"
              :label="s.stationName"
              :value="s.id"
            >
              <span>{{ s.stationName }}</span>
              <span v-if="s.stationCode" style="color:#909399;margin-left:8px;font-size:12px">{{ s.stationCode }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button
            type="warning"
            :icon="Refresh"
            :loading="replacing"
            :disabled="!selStationId"
            @click="handleReplace"
          >
            替换坐标
          </el-button>
          <span v-if="selStationId" class="replace-hint">
            将把选中站点的坐标更新为 {{ result.lng }}, {{ result.lat }}
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 地图 -->
    <el-card v-if="result" shadow="hover" class="map-card">
      <template #header>
        <div class="card-hd">
          <el-icon><MapLocation /></el-icon>
          <span>地图定位</span>
        </div>
      </template>
      <div ref="mapContainer" class="map-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Location, MapLocation, Refresh } from '@element-plus/icons-vue'
import { geocodeQuery, geocodeReplaceCoordinates } from '@/api/geocode'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { getMetroLineList, getLineOrderedStations } from '@/api/metroLine'

// ===== 查询 =====
const form = ref({ address: '', city: '' })
const loading = ref(false)
const result = ref(null)
const mapContainer = ref(null)
let map = null
let marker = null

const handleQuery = async () => {
  if (!form.value.address.trim()) {
    ElMessage.warning('请输入地址')
    return
  }
  loading.value = true
  result.value = null
  try {
    const res = await geocodeQuery(form.value)
    result.value = res.data
    nextTick(() => showMap())
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

const showMap = () => {
  if (!mapContainer.value || !result.value) return
  const { lng, lat } = result.value
  const latNum = parseFloat(lat)
  const lngNum = parseFloat(lng)
  if (isNaN(latNum) || isNaN(lngNum)) return

  if (!map) {
    map = L.map(mapContainer.value).setView([latNum, lngNum], 15)
    L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
      subdomains: ['1', '2', '3', '4'],
      attribution: '&copy; 高德地图'
    }).addTo(map)
  } else {
    map.setView([latNum, lngNum], 15)
  }

  if (marker) map.removeLayer(marker)
  marker = L.marker([latNum, lngNum]).addTo(map)
    .bindPopup(result.value.formattedAddress || '')
    .openPopup()

  setTimeout(() => map.invalidateSize(), 100)
}

const copyText = (text) => {
  if (!text) return
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.error('复制失败')
  )
}

// ===== 级联选择 =====
const countries = ref([])
const allCities = ref([])
const lines = ref([])
const stations = ref([])

const selCountryId = ref(null)
const selCityId = ref(null)
const selLineId = ref(null)
const selStationId = ref(null)
const replacing = ref(false)

const filteredCities = computed(() => {
  if (!selCountryId.value) return []
  return allCities.value.filter(c => c.countryId === selCountryId.value)
})

const onCountryChange = () => {
  selCityId.value = null
  selLineId.value = null
  selStationId.value = null
  lines.value = []
  stations.value = []
}

const onCityChange = async () => {
  selLineId.value = null
  selStationId.value = null
  stations.value = []
  if (!selCityId.value) { lines.value = []; return }
  try {
    const res = await getMetroLineList({ cityId: selCityId.value, pageSize: 999 })
    lines.value = res.data?.records || []
  } catch {
    lines.value = []
  }
}

const onLineChange = async () => {
  selStationId.value = null
  if (!selLineId.value) { stations.value = []; return }
  try {
    const res = await getLineOrderedStations(selLineId.value)
    stations.value = res.data?.stations || []
  } catch {
    stations.value = []
  }
}

const handleReplace = async () => {
  if (!selStationId.value || !result.value) return
  const station = stations.value.find(s => s.id === selStationId.value)
  const stationName = station?.stationName || selStationId.value
  try {
    await ElMessageBox.confirm(
      `确定将站点「${stationName}」的坐标更新为 ${result.value.lng}, ${result.value.lat} 吗？`,
      '确认替换',
      { type: 'warning', confirmButtonText: '确定替换', cancelButtonText: '取消' }
    )
  } catch { return }

  replacing.value = true
  try {
    const res = await geocodeReplaceCoordinates({
      stationId: String(selStationId.value),
      lng: result.value.lng,
      lat: result.value.lat,
    })
    ElMessage.success(`站点「${res.data.stationName}」坐标已更新`)
  } catch {
    // error handled by interceptor
  } finally {
    replacing.value = false
  }
}

onMounted(async () => {
  try {
    const [countryRes, cityRes] = await Promise.all([getCountryAll(), getCityAll()])
    countries.value = countryRes.data || []
    allCities.value = cityRes.data || []
  } catch {
    // ignore
  }
})
</script>

<script>
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
export default { name: 'GeocodeQuery' }
</script>

<style scoped>
.manage-page { padding: 4px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; display: flex; align-items: center; gap: 8px; }

.query-card { margin-bottom: 16px; border-radius: 12px; }
.result-card { margin-bottom: 16px; border-radius: 12px; }
.replace-card { margin-bottom: 16px; border-radius: 12px; }
.map-card { margin-bottom: 16px; border-radius: 12px; }

.card-hd { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; }

.required-label::before { content: '*'; color: #F56C6C; margin-right: 4px; }

.result-grid { display: flex; flex-direction: column; gap: 12px; }
.result-row { display: flex; gap: 24px; flex-wrap: wrap; }
.result-item { flex: 1; min-width: 160px; }
.item-label { display: block; font-size: 12px; color: #909399; margin-bottom: 4px; }
.item-value { font-size: 14px; color: #303133; display: flex; align-items: center; gap: 4px; }
.coord-value { font-family: monospace; font-size: 15px; font-weight: 600; color: #409eff; }

.formatted-address {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; background: #f5f7fa; border-radius: 8px;
  font-size: 14px; color: #303133;
}

.replace-hint {
  margin-left: 12px;
  font-size: 13px;
  color: #909399;
}

.map-container { height: 400px; border-radius: 8px; }
</style>
