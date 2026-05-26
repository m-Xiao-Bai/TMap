<template>
  <div class="metro-detail" v-loading="loading">
    <div class="hero-banner" :style="heroStyle">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <el-button class="back-btn" :icon="ArrowLeft" circle @click="goBack" />
        <div class="hero-main">
          <div class="hero-line-circle" :style="{ background: line.lineColor || '#409EFF' }">
            <span class="hero-line-no">{{ line.lineNo || '--' }}</span>
          </div>
          <div class="hero-text">
            <h1 class="hero-name">{{ line.lineName || '加载中...' }}</h1>
            <div class="hero-sub">
              <el-tag size="small" effect="dark" :style="tagBgStyle">{{ line.status }}</el-tag>
              <span class="hero-location">{{ line.cityName }} · {{ line.countryName }}</span>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <el-button :icon="Sort" @click="goToReorder">排序站点</el-button>
          <el-button type="primary" :icon="Edit" @click="openEditDialog">编辑线路</el-button>
          <el-popconfirm
            v-if="isRootAdmin"
            title="确定删除该线路？此操作不可逆"
            confirm-button-text="是" cancel-button-text="否"
            @confirm="handleDelete"
          >
            <template #reference>
              <el-button type="danger" :icon="Delete" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <div class="stats-row">
        <div class="stat-card" v-for="s in stats" :key="s.label">
          <div class="stat-icon" :style="{ background: s.bg }">
            <el-icon :size="22"><component :is="s.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-val">{{ s.value }}</span>
            <span class="stat-label">{{ s.label }}</span>
          </div>
        </div>
      </div>

      <div class="cards-grid">
        <el-card shadow="hover" class="detail-card">
          <template #header>
            <div class="card-hd"><el-icon><InfoFilled /></el-icon><span>基本信息</span></div>
          </template>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">线路ID</span>
              <code class="info-val">{{ line.id }}</code>
            </div>
            <div class="info-item">
              <span class="info-label">所属国家</span>
              <span class="info-val">{{ line.countryName || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属城市</span>
              <span class="info-val">{{ line.cityName || '-' }}</span>
            </div>
            <div class="info-item" v-if="line.cityNameEn">
              <span class="info-label">城市英文</span>
              <span class="info-val">{{ line.cityNameEn }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">线路编号</span>
              <div class="line-no-badge">
                <span class="ln-dot" :style="{ background: line.lineColor || '#ccc' }"></span>
                <strong>{{ line.lineNo }}</strong>
              </div>
            </div>
            <div class="info-item">
              <span class="info-label">线路名称</span>
              <span class="info-val">{{ line.lineName }}</span>
            </div>
            <div class="info-item" v-if="line.lineColor">
              <span class="info-label">线路颜色</span>
              <div class="color-display">
                <span class="color-swatch" :style="{ background: line.lineColor }"></span>
                <code>{{ line.lineColor }}</code>
                <span v-if="line.lineColorCn" class="color-cn">({{ line.lineColorCn }})</span>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="detail-card">
          <template #header>
            <div class="card-hd"><el-icon><Odometer /></el-icon><span>运营数据</span></div>
          </template>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">总里程</span>
              <span class="info-val num">{{ line.totalKm ?? '-' }} <small>km</small></span>
            </div>
            <div class="info-item">
              <span class="info-label">车站数量</span>
              <span class="info-val num">{{ line.stationCount ?? '-' }} <small>座</small></span>
            </div>
            <div class="info-item">
              <span class="info-label">列车数量</span>
              <span class="info-val num">{{ line.trainCount ?? '-' }} <small>列</small></span>
            </div>
            <div class="info-item" v-if="line.avgSpeed">
              <span class="info-label">平均速度</span>
              <span class="info-val num">{{ line.avgSpeed }} <small>km/h</small></span>
            </div>
            <div class="info-item" v-if="line.fullTime">
              <span class="info-label">全程耗时</span>
              <span class="info-val num">{{ line.fullTime }} <small>分钟</small></span>
            </div>
            <div class="info-item" v-if="line.firstTime">
              <span class="info-label">首班车</span>
              <span class="info-val num">{{ line.firstTime }}</span>
            </div>
            <div class="info-item" v-if="line.lastTime">
              <span class="info-label">末班车</span>
              <span class="info-val num">{{ line.lastTime }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <div v-if="transferLines.length" class="transfer-section">
        <div class="section-title">
          <el-icon><Connection /></el-icon>
          <span>换乘线路</span>
          <el-tag size="small" round effect="plain">{{ transferLines.length }} 条</el-tag>
        </div>
        <div class="transfer-grid">
          <div
            v-for="(tl, idx) in transferLines"
            :key="idx"
            class="transfer-card"
            :class="{ clickable: tl.lineId }"
            :style="{ '--tc': tl.color || '#409EFF' }"
            @click="goToMetroLine(tl)"
          >
            <div class="tc-line-dot" :style="{ background: tl.color || '#409EFF' }"></div>
            <div class="tc-info">
              <span class="tc-name">{{ tl.name }}</span>
              <span v-if="tl.nameEn" class="tc-name-en">{{ tl.nameEn }}</span>
            </div>
            <div class="tc-arrow">
              <el-icon><Right /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <div v-if="transferStations.length" class="transfer-section">
        <div class="section-title">
          <el-icon><Place /></el-icon>
          <span>换乘站点</span>
          <el-tag size="small" round effect="plain">{{ transferStations.length }} 站</el-tag>
        </div>
        <div class="station-grid">
          <div v-for="(st, idx) in transferStations" :key="idx" class="station-card">
            <span class="st-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
            <span>{{ typeof st === 'string' ? st : (st.name || st.stationName || '') }}</span>
          </div>
        </div>
      </div>

      <div class="transfer-section" v-loading="mapLoading">
        <div class="section-title">
          <el-icon><MapLocation /></el-icon>
          <span>线路走向地图</span>
          <el-tag v-if="mapLineData.length && mapLineData[0].stations" size="small" round effect="plain">
            {{ mapLineData[0].stations.length }} 站
          </el-tag>
          <div class="map-toggle">
            <button
              class="toggle-btn"
              :class="{ active: mapMode === 'geo' }"
              @click="mapMode = 'geo'"
            >
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/></svg>
              地理地图
            </button>
            <button
              class="toggle-btn"
              :class="{ active: mapMode === 'enhanced' }"
              @click="mapMode = 'enhanced'"
            >
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/><path d="M8 14l2 2 4-4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
              增强地理图
            </button>
          </div>
        </div>
        <el-alert
          v-if="mapHasBranch && mapBranchInfo"
          :title="mapBranchInfo"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom:12px"
        />
        <MetroMap
          v-if="mapLineData.length && mapMode === 'geo'"
          :lines="mapLineData"
          :stations="[]"
          height="520px"
          :show-legend="false"
          :fit-bounds="true"
        />
        <MetroMapEnhanced
          v-else-if="mapLineData.length && mapMode === 'enhanced'"
          :lines="mapLineData"
          :stations="[]"
          height="520px"
          :show-legend="false"
          :fit-bounds="true"
        />
        <el-empty v-else-if="!mapLoading" description="暂无站点数据" />
      </div>

      <div v-if="lineStations.length" class="transfer-section">
        <div class="section-title">
          <el-icon><Place /></el-icon>
          <span>沿线站点</span>
          <el-tag size="small" round effect="plain">{{ lineStations.length }} 站</el-tag>
        </div>
        <div class="station-grid">
          <div
            v-for="(st, idx) in lineStations"
            :key="st.id"
            class="station-card clickable"
            @click="goToStation(st.id)"
          >
            <span class="st-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
            <span class="st-idx">{{ idx + 1 }}.</span>
            <span>{{ st.stationName }}</span>
            <span v-if="st.stationNameEn" class="st-en">({{ st.stationNameEn }})</span>
            <span v-if="st.isTransfer" class="st-transfer-tag">
              <el-tag size="small" type="warning" effect="plain">换乘</el-tag>
            </span>
          </div>
        </div>
      </div>

      <el-card v-if="openDateInfo" shadow="hover" class="detail-card timeline-card">
        <template #header>
          <div class="card-hd"><el-icon><Clock /></el-icon><span>线路里程碑</span></div>
        </template>
        <el-timeline>
          <el-timeline-item
            :timestamp="'开通日期'"
            placement="top"
            color="#409EFF"
            size="large"
          >
            <div class="tl-content">
              <div class="tl-date-block">
                <span class="tl-day">{{ openDateInfo.day }}</span>
                <span class="tl-month">{{ openDateInfo.monthYear }}</span>
              </div>
              <div class="tl-desc">
                <p>{{ line.lineName }} ({{ line.lineNo }}) 正式投入运营</p>
                <p class="tl-sub">服务于 {{ line.cityName }} · {{ line.countryName }}</p>
              </div>
            </div>
          </el-timeline-item>
          <el-timeline-item
            v-if="line.createdAt"
            :timestamp="'创建于 ' + line.createdAt"
            placement="top"
            color="#67c23a"
          >
            <p class="tl-desc">数据录入系统</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <div v-if="extraData" class="extra-section">
        <div class="section-title">
          <el-icon><MoreFilled /></el-icon>
          <span>扩展数据</span>
          <el-button link size="small" type="primary" @click="copyExtra">复制</el-button>
        </div>
        <div class="extra-card">
          <pre class="extra-json">{{ extraData }}</pre>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible" title="编辑地铁线路" width="760px"
      :close-on-click-modal="false" destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属国家" prop="countryId">
              <el-select v-model="form.countryId" :disabled="!canEditLineName" placeholder="请选择国家" filterable style="width: 100%" @change="onCountryChange">
                <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属城市" prop="cityId">
              <el-select v-model="form.cityId" :disabled="!canEditLineName" placeholder="请选择城市" filterable style="width: 100%">
                <el-option v-for="c in dialogCityOptions" :key="c.id" :value="c.id" :label="c.cityName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="线路编号" prop="lineNo">
              <el-input v-model="form.lineNo" :disabled="!canEditLineName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="线路名称" prop="lineName">
              <el-input v-model="form.lineName" :disabled="!canEditLineName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.statusCode" style="width: 100%">
                <el-option v-for="(label, code) in config.statusMap" :key="Number(code)" :value="Number(code)" :label="label" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="线路颜色">
              <el-color-picker v-model="form.lineColor" :disabled="!canEditAllFields" />
              <span class="color-hex-text">{{ form.lineColor || '未选择' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="颜色(中文)">
              <el-input v-model="form.lineColorCn" :disabled="!canEditAllFields" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">运营数据</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="里程(km)"><el-input-number v-model="form.totalKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="车站数"><el-input-number v-model="form.stationCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="列车数"><el-input-number v-model="form.trainCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="均速(km/h)"><el-input-number v-model="form.avgSpeed" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="全程(min)"><el-input-number v-model="form.fullTime" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开通日期"><el-date-picker v-model="form.openDate" :disabled="!canEditAllFields" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="首班车"><el-time-picker v-model="form.firstTime" :disabled="!canEditAllFields" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="末班车"><el-time-picker v-model="form.lastTime" :disabled="!canEditAllFields" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="选择时间" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="换乘线路数"><el-input-number v-model="form.transferLineCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="可换乘线路"><el-input v-model="form.transferLines" :disabled="!canEditAllFields" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="扩展字段"><el-input v-model="form.extra" :disabled="!canEditAllFields" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit, Delete, InfoFilled, Odometer, Connection, Right, Place, Clock, MoreFilled, Histogram, TrendCharts, DataLine, Timer, MapLocation, Sort } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getMetroLineDetail, updateMetroLine, deleteMetroLine, getMetroLineList, getLineOrderedStations } from '@/api/metroLine'
import MetroMap from '@/components/MetroMap.vue'
import MetroMapEnhanced from '@/components/MetroMapEnhanced.vue'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { normalizeList, ensureString } from '@/utils/normalize'

const router = useRouter()
const route = useRoute()
const { isRootAdmin, canEditAllFields } = usePermission()
const { state: config } = useSystemConfig()

const FALLBACK_COLORS = ['#e60012','#f39800','#009944','#0068b7','#00a0e9','#8b5eaa','#7fc242','#e6007e','#1d2088','#9b7b4d','#009f9d','#d70f5c','#6cc3c0','#f26522','#5c2d91']
const canEditLineName = computed(() => isRootAdmin.value)

const loading = ref(false)
const line = ref({})
const lineStations = ref([])
const mapLineData = ref([])
const mapHasBranch = ref(false)
const mapBranchInfo = ref(null)
const mapLoading = ref(false)
const mapMode = ref('geo')

const heroStyle = computed(() => {
  const c = line.value.lineColor || '#409EFF'
  return { '--hero-color': c }
})

const tagBgStyle = computed(() => ({
  background: line.value.lineColor || '#409EFF',
  border: 'none',
}))

const stats = computed(() => [
  { label: '线路里程', value: (line.value.totalKm ?? '-') + ' km', icon: DataLine, bg: 'rgba(64,158,255,0.12)' },
  { label: '车站数量', value: (line.value.stationCount ?? '-') + ' 座', icon: Place, bg: 'rgba(103,194,58,0.12)' },
  { label: '列车配置', value: (line.value.trainCount ?? '-') + ' 列', icon: TrendCharts, bg: 'rgba(242,158,50,0.12)' },
  { label: '开通年份', value: line.value.openDate || '-', icon: Clock, bg: 'rgba(139,107,170,0.12)' },
])

const transferLines = computed(() => {
  const raw = line.value.transferLines
  if (!raw) return []
  try {
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    const arr = Array.isArray(data) ? data : (data.lines || data.transferLines || [])
    return arr.map((t, i) => {
      if (typeof t === 'string') {
        const lookupKey = t.toLowerCase()
        const dbEntry = metroLineDbMap.value[lookupKey]
        return {
          name: t, nameEn: '', stations: [], btntext: '', btncolor: '',
          color: dbEntry?.color || FALLBACK_COLORS[i % FALLBACK_COLORS.length],
          lineId: dbEntry?.id || '',
        }
      }
      const name = t.name || t.lineName || t.lineNo || ''
      const lookupKey = name.toLowerCase()
      const dbEntry = metroLineDbMap.value[lookupKey]
      return {
        name,
        nameEn: t.nameEn || t.en || '',
        stations: t.stations || t.transferStations || [],
        btntext: t.btntext || t.openDate || '',
        btncolor: t.btncolor || '',
        color: dbEntry?.color || t.color || t.lineColor || FALLBACK_COLORS[i % FALLBACK_COLORS.length],
        lineId: dbEntry?.id || t.transferColorId || t.lineId || '',
      }
    }).filter(t => t.name)
  } catch (e) { return [] }
})

const transferStations = computed(() => {
  const raw = line.value.transferStations
  if (!raw) return []
  try {
    const data = JSON.parse(raw)
    return Array.isArray(data) ? data : (data.stations || data.transferStations || [])
  } catch { return [] }
})

const openDateInfo = computed(() => {
  if (!line.value.openDate) return null
  try {
    const d = new Date(line.value.openDate)
    if (isNaN(d.getTime())) return { day: line.value.openDate, monthYear: '' }
    const months = ['一月','二月','三月','四月','五月','六月','七月','八月','九月','十月','十一月','十二月']
    return { day: String(d.getDate()), monthYear: `${months[d.getMonth()]} ${d.getFullYear()}` }
  } catch { return null }
})

const extraData = computed(() => {
  if (!line.value.extra) return null
  try { return JSON.stringify(JSON.parse(line.value.extra), null, 2) } catch { return line.value.extra }
})

const countryOptions = ref([])
const cityOptions = ref([])
const metroLineDbMap = ref({})

const dialogCityOptions = computed(() => {
  if (!form.countryId) return cityOptions.value
  return cityOptions.value.filter(c => String(c.countryId) === String(form.countryId))
})

const onCountryChange = () => {
  if (form.cityId && !dialogCityOptions.value.find(c => c.id === form.cityId)) form.cityId = null
}

const goBack = () => { router.push('/metro-lines') }
const goToMetroLine = (tl) => { if (tl && tl.lineId) router.push(`/metro-line-detail/${tl.lineId}`) }
const goToReorder = () => { router.push(`/station-reorder/${route.params.id}`) }
const copyExtra = () => {
  navigator.clipboard.writeText(extraData.value).then(() => ElMessage.success('已复制'), () => ElMessage.warning('复制失败'))
}

const fetchLineMapData = async () => {
  mapLoading.value = true
  try {
    const res = await getLineOrderedStations(route.params.id)
    const data = res.data || {}
    const stations = data.stations || []
    if (stations.length > 0) {
      const connected = !data.hasBranch
      mapLineData.value = [{
        name: data.lineName || line.value.lineName || '线路',
        color: data.lineColor || line.value.lineColor || '#409EFF',
        stations,
        connected,
      }]
      // 用排序后的站点数据填充沿线站点列表（保证顺序正确）
      lineStations.value = stations
    } else {
      mapLineData.value = []
      lineStations.value = []
    }
    mapHasBranch.value = data.hasBranch || false
    mapBranchInfo.value = data.branchInfo || null
  } catch (e) {
    console.error('fetchLineMapData error:', e)
    mapLineData.value = []
    lineStations.value = []
  } finally {
    mapLoading.value = false
  }
}

const goToStation = (id) => { router.push(`/metro-station-detail/${id}`) }

const fetchLine = async () => {
  loading.value = true
  try {
    line.value = (await getMetroLineDetail(route.params.id)).data
    fetchMetroLineMap()
    fetchLineMapData()
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

const fetchOptions = async () => {
  try { countryOptions.value = normalizeList((await getCountryAll()).data || [], ['id']) } catch {}
  try { cityOptions.value = normalizeList((await getCityAll()).data || [], ['id', 'countryId']) } catch {}
}

const fetchMetroLineMap = async () => {
  if (!line.value.cityId) return
  try {
    const res = await getMetroLineList({ cityId: line.value.cityId, pageSize: 200 })
    const lines = res.data?.records || []
    const map = {}
    lines.forEach(l => {
      const entry = { id: l.id, color: l.lineColor || '' }
      if (l.lineName) map[l.lineName.toLowerCase()] = entry
      if (l.lineNo) map[l.lineNo.toLowerCase()] = entry
    })
    metroLineDbMap.value = map
  } catch (e) { /* */ }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const initForm = () => ({
  countryId: null, cityId: null, lineName: '', lineNo: '', lineColor: '', lineColorCn: '',
  totalKm: 0, stationCount: 0, transferLineCount: 0, transferLines: '',
  transferStationCount: 0, transferStations: '', trainCount: 0, avgSpeed: null,
  firstTime: '', lastTime: '', fullTime: null, openDate: '', statusCode: 0, extra: '',
})

const form = reactive(initForm())

const formRules = {
  countryId: [{ required: true, message: '请选择国家', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  lineName: [{ required: true, message: '请输入线路名称', trigger: 'blur' }, { max: 100, trigger: 'blur' }],
  lineNo: [{ required: true, message: '请输入线路编号', trigger: 'blur' }, { max: 20, trigger: 'blur' }],
}

const openEditDialog = () => {
  const l = line.value
  form.countryId = ensureString(l.countryId); form.cityId = ensureString(l.cityId); form.lineName = l.lineName; form.lineNo = l.lineNo
  form.lineColor = l.lineColor || ''; form.lineColorCn = l.lineColorCn || ''
  form.totalKm = l.totalKm; form.stationCount = l.stationCount; form.transferLineCount = l.transferLineCount
  form.transferLines = l.transferLines || ''; form.transferStationCount = l.transferStationCount
  form.transferStations = l.transferStations || ''; form.trainCount = l.trainCount; form.avgSpeed = l.avgSpeed
  form.firstTime = l.firstTime || ''; form.lastTime = l.lastTime || ''; form.fullTime = l.fullTime
  form.openDate = l.openDate || ''; form.statusCode = l.statusCode; form.extra = l.extra || ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  submitting.value = true
  try {
    const p = {
      countryId: form.countryId, cityId: form.cityId, lineName: form.lineName, lineNo: form.lineNo,
      lineColor: form.lineColor || undefined, lineColorCn: form.lineColorCn || undefined,
      totalKm: form.totalKm, stationCount: form.stationCount, transferLineCount: form.transferLineCount,
      transferLines: form.transferLines || undefined, transferStationCount: form.transferStationCount,
      transferStations: form.transferStations || undefined, trainCount: form.trainCount, avgSpeed: form.avgSpeed,
      firstTime: form.firstTime || undefined, lastTime: form.lastTime || undefined,
      fullTime: form.fullTime, openDate: form.openDate || undefined,
      statusCode: form.statusCode, extra: form.extra || undefined,
    }
    if (!canEditLineName.value) { delete p.lineName; delete p.lineNo }
    await updateMetroLine(line.value.id, p)
    ElMessage.success('修改成功'); dialogVisible.value = false; fetchLine()
  } catch {} finally { submitting.value = false }
}

const handleDelete = async () => {
  try { await deleteMetroLine(line.value.id); ElMessage.success('删除成功'); router.push('/metro-lines') } catch {}
}

onMounted(() => { fetchLine(); fetchOptions() })
watch(() => route.params.id, () => { if (route.params.id) fetchLine() })
</script>

<style scoped>
.metro-detail { background: var(--bg-primary); min-height: 100vh; }

.hero-banner {
  position: relative; overflow: hidden;
  background: linear-gradient(160deg, var(--hero-color, #409EFF) 0%, color-mix(in srgb, var(--hero-color, #409EFF) 30%, #0f0f1a) 70%, #0f0f1a 100%);
  padding: 0; min-height: 260px;
}
.hero-overlay {
  position: absolute; inset: 0;
  background: radial-gradient(ellipse at 30% 50%, rgba(255,255,255,0.08) 0%, transparent 60%),
              linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.25) 100%);
  pointer-events: none;
}
.hero-content { position: relative; z-index: 1; padding: 32px 40px 36px; }

.back-btn { position: absolute; top: 32px; left: 40px; background: rgba(255,255,255,0.2) !important; border: none !important; color: #fff !important; }
.back-btn:hover { background: rgba(255,255,255,0.35) !important; }

.hero-main { display: flex; align-items: center; gap: 28px; margin-top: 48px; padding-left: 0; }
.hero-line-circle {
  width: 96px; height: 96px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3), inset 0 0 20px rgba(255,255,255,0.15);
  flex-shrink: 0;
}
.hero-line-no { color: #fff; font-size: 22px; font-weight: 800; letter-spacing: 1px; text-shadow: 0 2px 4px rgba(0,0,0,0.2); }
.hero-text { flex: 1; }
.hero-name { margin: 0 0 10px; font-size: 32px; font-weight: 700; color: #fff; text-shadow: 0 2px 8px rgba(0,0,0,0.3); }
.hero-sub { display: flex; align-items: center; gap: 14px; }
.hero-location { color: rgba(255,255,255,0.75); font-size: 14px; }
.hero-actions { position: absolute; top: 36px; right: 40px; display: flex; gap: 8px; z-index: 2; }
.hero-actions .el-button { backdrop-filter: blur(8px); }

.detail-body { padding: 24px 40px 40px; max-width: 1100px; margin: 0 auto; }

.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
.stat-card {
  background: #fff; border-radius: 12px; padding: 20px; display: flex; align-items: center; gap: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04); transition: all 0.3s;
}
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
.stat-icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; color: #606266;
}
.stat-info { display: flex; flex-direction: column; }
.stat-val { font-size: 20px; font-weight: 700; color: #303133; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }

.cards-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }
.detail-card { border-radius: 12px; overflow: hidden; }
.card-hd { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; color: #303133; }
.card-hd .el-icon { color: #409EFF; }

.info-list { display: flex; flex-direction: column; }
.info-item {
  display: flex; align-items: center; padding: 10px 0; border-bottom: 1px solid #f2f3f5;
}
.info-item:last-child { border-bottom: none; }
.info-label { width: 90px; flex-shrink: 0; font-size: 13px; color: #909399; }
.info-val { font-size: 14px; color: #303133; flex: 1; }
.info-val.num { font-size: 18px; font-weight: 700; color: #409EFF; }
.info-val.num small { font-size: 12px; color: #909399; font-weight: 400; }
.info-val code { font-size: 13px; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; }

.line-no-badge { display: flex; align-items: center; gap: 8px; }
.ln-dot { width: 12px; height: 12px; border-radius: 3px; }
.color-display { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.color-swatch { width: 22px; height: 22px; border-radius: 6px; border: 2px solid #fff; box-shadow: 0 0 0 1px #dcdfe6; }
.color-display code { font-size: 13px; background: #f5f7fa; padding: 2px 8px; border-radius: 4px; }
.color-cn { font-size: 13px; color: #606266; }

.transfer-section { margin-bottom: 24px; }
.section-title {
  display: flex; align-items: center; gap: 10px; margin-bottom: 16px;
  font-size: 16px; font-weight: 600; color: #303133;
}
.section-title .el-icon { color: #409EFF; }

/* ── 地图切换按钮 ── */
.map-toggle {
  margin-left: auto;
  display: flex;
  gap: 2px;
  background: #f0f2f5;
  border-radius: 8px;
  padding: 3px;
}
.toggle-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #909399;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s;
  white-space: nowrap;
}
.toggle-btn:hover { color: #606266; }
.toggle-btn.active {
  background: #fff;
  color: #409EFF;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  font-weight: 600;
}

.transfer-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
.transfer-card {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 16px; border-radius: 10px; background: #fff;
  border: 1px solid #ebeef5; position: relative; overflow: hidden;
  cursor: pointer; transition: all 0.3s;
}
.transfer-card::after {
  content:''; position: absolute; inset: 0; background: var(--tc);
  opacity: 0; transition: opacity 0.3s; z-index: 0;
}
.transfer-card:hover { border-color: var(--tc); transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.08); }
.transfer-card:hover::after { opacity: 0.06; }
.transfer-card:hover .tc-arrow { opacity: 1; transform: translateX(0); }
.transfer-card:not(.clickable) { cursor: default; }
.transfer-card:not(.clickable):hover { border-color: #ebeef5; transform: none; box-shadow: none; }
.transfer-card:not(.clickable):hover::after { opacity: 0; }
.transfer-card:not(.clickable):hover .tc-arrow { opacity: 0.3; transform: translateX(-4px); }

.tc-line-dot { width: 16px; height: 16px; border-radius: 50%; flex-shrink: 0; position: relative; z-index: 1; }
.tc-info { display: flex; flex-direction: column; flex: 1; position: relative; z-index: 1; }
.tc-name { font-size: 14px; font-weight: 600; color: #303133; }
.tc-name-en { font-size: 11px; color: #909399; }
.tc-arrow {
  opacity: 0; transform: translateX(-6px); transition: all 0.3s;
  color: var(--tc); position: relative; z-index: 1;
}

.station-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
.station-card.clickable { cursor: pointer; }
.station-card.clickable:hover { background: #f0f5ff; border-color: #b3d8ff; }
.st-idx { color: #909399; font-size: 12px; min-width: 20px; }
.st-en { color: #909399; font-size: 12px; }
.st-transfer-tag { margin-left: auto; }
.station-card {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; border-radius: 8px; background: #fafbfc;
  border: 1px solid #ebeef5; font-size: 14px; color: #303133;
  transition: all 0.2s;
}
.station-card:hover { background: #f0f5ff; border-color: #b3d8ff; }
.st-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.color-hex-text { margin-left: 8px; font-size: 13px; color: #909399; font-family: monospace; }

.timeline-card { margin-bottom: 24px; }
.tl-content { display: flex; align-items: center; gap: 20px; }
.tl-date-block {
  display: flex; flex-direction: column; align-items: center;
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: #fff; padding: 12px 20px; border-radius: 10px;
  min-width: 80px;
}
.tl-day { font-size: 28px; font-weight: 700; line-height: 1.1; }
.tl-month { font-size: 12px; opacity: 0.85; margin-top: 2px; }
.tl-desc { font-size: 14px; color: #303133; margin: 0; }
.tl-sub { font-size: 12px; color: #909399; margin-top: 4px; }

.extra-section { margin-bottom: 24px; }
.extra-card {
  background: linear-gradient(135deg, #1e1e2e 0%, #2a2a3e 100%);
  border-radius: 12px; padding: 20px 24px; border: 1px solid #3a3a50;
}
.extra-json { margin: 0; font-size: 13px; line-height: 1.7; color: #cdd6f4;
  font-family: 'JetBrains Mono','Fira Code','Consolas',monospace;
  max-height: 260px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }

@media (max-width: 900px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .cards-grid { grid-template-columns: 1fr; }
  .detail-body { padding: 20px 16px; }
  .hero-content { padding: 24px 20px 28px; }
  .hero-actions { top: 24px; right: 20px; }
  .hero-name { font-size: 24px; }
}
</style>
