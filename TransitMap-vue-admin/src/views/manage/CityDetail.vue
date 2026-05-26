<template>
  <div class="city-detail" v-loading="loading">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
      <span class="page-title">城市详情 - {{ city.cityName || '加载中...' }}</span>
      <div class="header-actions">
        <el-button type="primary" :icon="Edit" @click="openEditDialog">编辑城市</el-button>
        <el-popconfirm
          v-if="isRootAdmin"
          title="确定删除该城市？此操作不可逆"
          confirm-button-text="是"
          cancel-button-text="否"
          @confirm="handleDelete"
        >
          <template #reference>
            <el-button type="danger" :icon="Delete">删除城市</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <div class="detail-content">
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-tag :type="auditStatusTag(city.statusCode)" size="small" effect="plain">
              {{ auditStatusText(city.statusCode) }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="城市ID">{{ city.id }}</el-descriptions-item>
          <el-descriptions-item label="城市名称">
            <span class="val-em">{{ city.cityName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="英文名称">{{ city.cityNameEn || '-' }}</el-descriptions-item>
          <el-descriptions-item label="别称">{{ city.cityAlias || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属国家">
            <el-tag size="small" effect="plain" type="info">{{ city.countryName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地铁系统LOGO">{{ city.metroLineLogo || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="info-card">
        <template #header><span>交通数据</span></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="地铁系统数">
            <span class="val-num">{{ city.metroCount ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="地铁线路数">
            <span class="val-num">{{ city.metroLineCount ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="高铁数量">
            <span class="val-num">{{ city.hsrCount ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="地铁里程(km)">
            <span class="val-num">{{ city.metroKm ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="高铁里程(km)">
            <span class="val-num">{{ city.hsrKm ?? '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="人口数">
            <span class="val-num">{{ city.population ? formatNumber(city.population) : '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="info-card">
        <template #header><span>状态与时间</span></template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditStatusTag(city.statusCode)" size="small">{{ auditStatusText(city.statusCode) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上线状态">
            <el-tag v-if="city.statusCode === 3" size="small">上线</el-tag>
            <el-tag v-else-if="city.statusCode === 4" type="warning" size="small">下线</el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态码">{{ city.statusCode }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ city.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="更新时间" :span="2">{{ city.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="info-card" v-loading="mapLoading">
        <template #header>
          <div class="card-header">
            <span><el-icon style="margin-right:6px;vertical-align:middle"><MapLocation /></el-icon>地铁线路地图</span>
            <el-tag v-if="cityMapStations.length" size="small" round effect="plain">{{ cityMapStations.length }} 个站点</el-tag>
            <div class="map-toggle">
              <button class="toggle-btn" :class="{ active: mapMode === 'geo' }" @click="mapMode = 'geo'">
                <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/></svg>
                地理地图
              </button>
              <button class="toggle-btn" :class="{ active: mapMode === 'enhanced' }" @click="mapMode = 'enhanced'">
                <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/><path d="M8 14l2 2 4-4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
                增强地理图
              </button>
            </div>
          </div>
        </template>
        <MetroMap
          v-if="cityMapLines.length && mapMode === 'geo'"
          :stations="cityMapStations"
          :lines="cityMapLines"
          height="520px"
          :show-legend="true"
          :fit-bounds="true"
        />
        <MetroMapEnhanced
          v-else-if="cityMapLines.length && mapMode === 'enhanced'"
          :stations="cityMapStations"
          :lines="cityMapLines"
          height="520px"
          :show-legend="true"
          :fit-bounds="true"
        />
        <el-empty v-else-if="!mapLoading" description="暂无运营中的地铁站点数据" />
      </el-card>

      <el-card v-if="city.metroLines || city.extra" shadow="never" class="info-card">
        <template #header><span>扩展数据</span></template>

        <div v-if="metroLineList.length" class="metro-section">
          <div class="section-label">
            <el-icon><Histogram /></el-icon>
            <span>地铁线路列表</span>
            <el-tag size="small" round effect="plain">{{ metroLineList.length }} 条线路</el-tag>
          </div>
          <div class="metro-line-grid">
            <div
              v-for="(line, idx) in metroLineList"
              :key="idx"
              class="metro-line-card"
              :class="{ clickable: line.lineId }"
              :style="{ '--line-color': line.color }"
              @click="goToMetroLine(line)"
            >
              <div class="line-dot" :style="{ background: line.color }"></div>
              <div class="line-info">
                <span class="line-name">{{ line.name }}</span>
                <span v-if="line.nameEn" class="line-name-en">{{ line.nameEn }}</span>
              </div>
              <span class="line-index">{{ String(idx + 1).padStart(2, '0') }}</span>
            </div>
          </div>
        </div>

        <div v-if="metroTableLines.length" class="metro-section">
          <div class="section-label">
            <el-icon><List /></el-icon>
            <span>线路详情</span>
          </div>
          <el-table :data="metroTableLines" border stripe size="small" class="metro-table" max-height="360" :row-class-name="tableRowClass" @row-click="goToMetroLine">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="name" label="线路名称" min-width="120">
              <template #default="{ row }">
                <div class="table-line-name">
                  <span class="color-dot" :style="{ background: row.color }"></span>
                  <strong>{{ row.name }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="nameEn" label="英文名" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.nameEn || '-' }}</template>
            </el-table-column>
            <el-table-column prop="length" label="长度(km)" width="100" align="right">
              <template #default="{ row }">{{ row.length ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="stations" label="车站数" width="80" align="center">
              <template #default="{ row }">{{ row.stations ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="opened" label="开通年份" width="100" align="center">
              <template #default="{ row }">{{ row.opened ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.type" size="small" effect="plain">{{ row.type }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="city.extra" class="extra-section">
          <div class="section-label">
            <el-icon><MoreFilled /></el-icon>
            <span>扩展字段</span>
            <el-button link size="small" type="primary" @click="copyExtra">复制</el-button>
          </div>
          <div class="extra-json-card">
            <pre class="extra-json">{{ formatJson(city.extra) }}</pre>
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog
      v-model="dialogVisible"
      title="编辑城市信息"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属国家" prop="countryId">
              <el-select v-model="form.countryId" :disabled="!canEditCityName" placeholder="请选择国家" filterable style="width: 100%">
                <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市名称" prop="cityName">
              <el-input v-model="form.cityName" :disabled="!canEditCityName" placeholder="中文名称" />
              <div v-if="!canEditCityName" class="field-tip">仅最高管理员</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="英文名称">
              <el-input v-model="form.cityNameEn" :disabled="!canEditAllFields" placeholder="English name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="别称">
              <el-input v-model="form.cityAlias" :disabled="!canEditAllFields" placeholder="别名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁系统LOGO">
              <el-input v-model="form.metroLineLogo" :disabled="!canEditAllFields" placeholder="LOGO地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.statusCode" style="width: 100%">
                <el-option :value="0" label="审核中" />
                <el-option :value="1" label="审核通过" />
                <el-option :value="2" label="审核不通过" />
                <el-option :value="3" label="上线" />
                <el-option :value="4" label="下线" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">交通数据</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁系统数">
              <el-input-number v-model="form.metroCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地铁线路数">
              <el-input-number v-model="form.metroLineCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="高铁数量">
              <el-input-number v-model="form.hsrCount" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人口数">
              <el-input-number v-model="form.population" :min="0" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁里程(km)">
              <el-input-number v-model="form.metroKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="高铁里程(km)">
              <el-input-number v-model="form.hsrKm" :min="0" :precision="1" :disabled="!canEditAllFields" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地铁线路列表">
              <el-input v-model="form.metroLines" :disabled="!canEditAllFields" placeholder="JSON格式" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扩展字段">
              <el-input v-model="form.extra" :disabled="!canEditAllFields" placeholder="JSON格式" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, Delete, Histogram, List, MoreFilled, MapLocation } from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { getCityDetail, updateCity, deleteCity } from '@/api/city'
import { getCountryAll } from '@/api/country'
import { getMetroLineList } from '@/api/metroLine'
import { getStationsByCityId } from '@/api/metroStation'
import MetroMap from '@/components/MetroMap.vue'
import MetroMapEnhanced from '@/components/MetroMapEnhanced.vue'
import { usePermission } from '@/composables/usePermission'
import { normalizeList, ensureString } from '@/utils/normalize'

const router = useRouter()
const route = useRoute()
const { isRootAdmin, canEditAllFields } = usePermission()
const FALLBACK_COLORS = ['#e60012','#f39800','#009944','#0068b7','#00a0e9','#8b5eaa','#7fc242','#e6007e','#1d2088','#9b7b4d','#009f9d','#d70f5c','#6cc3c0','#f26522','#5c2d91']
const canEditCityName = computed(() => isRootAdmin.value)

const loading = ref(false)
const city = ref({})
const metroLineDbMap = ref({})
const metroLineColorMap = ref({})
const cityMapStations = ref([])
const cityMapLines = ref([])
const mapLoading = ref(false)
const mapMode = ref('geo')

const countryOptions = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  countryId: null, cityName: '', cityNameEn: '', cityAlias: '',
  metroLineLogo: '', metroCount: 0, metroLineCount: 0,
  hsrCount: 0, metroKm: 0, hsrKm: 0, population: null,
  metroLines: '', extra: '', statusCode: 0,
})

const formRules = {
  countryId: [{ required: true, message: '请选择所属国家', trigger: 'change' }],
  cityName: [
    { required: true, message: '请输入城市名称', trigger: 'blur' },
    { max: 50, message: '最长50字符', trigger: 'blur' },
  ],
}

const auditStatusTag = (code) => {
  if (code === 3 || code === 4) return 'success'
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[code] || 'info'
}

const auditStatusText = (code) => {
  if (code === 0) return '审核中'
  if (code === 1 || code === 3 || code === 4) return '审核通过'
  if (code === 2) return '审核不通过'
  return '未知'
}

const formatNumber = (num) => {
  if (num >= 100000000) return (num / 100000000).toFixed(2) + '亿'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return String(num)
}

const formatJson = (str) => {
  try { return JSON.stringify(JSON.parse(str), null, 2) }
  catch { return str }
}


const parseMetroData = () => {
  const raw = city.value.metroLines
  if (!raw) return { simple: [], table: [] }
  try {
    const data = JSON.parse(raw)
    if (Array.isArray(data)) {
      const simple = []
      const table = []
      data.forEach((item, idx) => {
        if (typeof item === 'string') {
          simple.push({ name: item, nameEn: '', color: FALLBACK_COLORS[idx % FALLBACK_COLORS.length] })
        } else if (typeof item === 'object' && item !== null) {
          const name = item.name || item.lineName || item.title || ''
          const nameEn = item.nameEn || item.en || item.english || ''
          const color = item.color || item.lineColor || FALLBACK_COLORS[idx % FALLBACK_COLORS.length]
          simple.push({ name, nameEn, color })
          const hasDetail = item.length || item.stations || item.opened || item.type
          if (hasDetail || item.nameEn) {
            table.push({ name, nameEn, color, length: item.length, stations: item.stations, opened: item.opened, type: item.type })
          }
        }
      })
      return { simple, table }
    }
    if (typeof data === 'object' && data !== null) {
      const lines = data.lines || data.lineList || data.metroLines || []
      if (Array.isArray(lines)) {
        const simple = []
        lines.forEach((item, idx) => {
          if (typeof item === 'string') {
            simple.push({ name: item, nameEn: '', color: FALLBACK_COLORS[idx % FALLBACK_COLORS.length] })
          } else if (typeof item === 'object' && item !== null) {
            const name = item.name || item.lineName || item.title || ''
            const nameEn = item.nameEn || item.en || item.english || ''
            const color = item.color || item.lineColor || FALLBACK_COLORS[idx % FALLBACK_COLORS.length]
            simple.push({ name, nameEn, color })
            const hasDetail = item.length || item.stations || item.opened || item.type
            if (hasDetail || item.nameEn) {
              table.push({ name, nameEn, color, length: item.length, stations: item.stations, opened: item.opened, type: item.type })
            }
          }
        })
        return { simple, table: [] }
      }
    }
    return { simple: [], table: [] }
  } catch {
    return { simple: [], table: [] }
  }
}

const metroLineList = computed(() => {
  if (!city.value.metroLines) return []
  const data = parseMetroData().simple
  data.forEach(l => {
    l.lineId = l.lineId || metroLineDbMap.value[l.name] || metroLineDbMap.value[l.nameEn]
    // 优先用数据库 lineColor，其次用城市 JSON 中的颜色，最后用调色板
    const dbColor = metroLineColorMap.value[l.name] || metroLineColorMap.value[l.nameEn] || (l.lineId ? metroLineColorMap.value[String(l.lineId)] : '')
    if (dbColor) l.color = dbColor
  })
  return data
})

const metroTableLines = computed(() => {
  if (!city.value.metroLines) return []
  const data = parseMetroData().table
  data.forEach(l => {
    l.lineId = l.lineId || metroLineDbMap.value[l.name] || metroLineDbMap.value[l.nameEn]
    const dbColor = metroLineColorMap.value[l.name] || metroLineColorMap.value[l.nameEn] || (l.lineId ? metroLineColorMap.value[String(l.lineId)] : '')
    if (dbColor) l.color = dbColor
  })
  return data
})

const copyExtra = () => {
  const text = formatJson(city.value.extra)
  navigator.clipboard.writeText(text).then(
    () => ElMessage.success('已复制到剪贴板'),
    () => ElMessage.warning('复制失败')
  )
}

const tableRowClass = ({ row }) => {
  return row.lineId ? 'clickable-row' : ''
}

const goBack = () => { router.push('/cities') }

const goToMetroLine = (line) => {
  if (line && line.lineId) {
    router.push(`/metro-line-detail/${line.lineId}`)
  }
}

const fetchCity = async () => {
  loading.value = true
  try {
    const id = route.params.id
    const res = await getCityDetail(id)
    city.value = res.data
    await fetchMetroLineMap()
    fetchCityMapData()
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}

const fetchCountries = async () => {
  try {
    const res = await getCountryAll()
    countryOptions.value = normalizeList(res.data || [], ['id'])
  } catch (e) { console.error('fetchCountries error:', e) }
}

const fetchMetroLineMap = async () => {
  if (!city.value.id) return
  try {
    const res = await getMetroLineList({ cityId: city.value.id, pageSize: 200 })
    const lines = res.data?.records || []
    const nameMap = {}
    const colorMap = {}
    lines.forEach(l => {
      if (l.lineName) nameMap[l.lineName] = l.id
      if (l.lineNo) nameMap[l.lineNo] = l.id
      // 构建颜色映射：lineName/lineNo -> lineColor, lineId -> lineColor
      if (l.lineColor) {
        if (l.lineName) colorMap[l.lineName] = l.lineColor
        if (l.lineNo) colorMap[l.lineNo] = l.lineColor
        colorMap[String(l.id)] = l.lineColor
      }
    })
    metroLineDbMap.value = nameMap
    metroLineColorMap.value = colorMap
  } catch (e) { console.error('fetchMetroLineMap error:', e) }
}

const fetchCityMapData = async () => {
  if (!city.value.id) return
  mapLoading.value = true
  try {
    const res = await getStationsByCityId(city.value.id)
    const stations = res.data || []
    cityMapStations.value = stations

    // 预解析每个站点的 lineIds，避免重复 parse
    const parsedCache = new Map()
    const getParsedLineIds = (s) => {
      if (!parsedCache.has(s.id)) {
        try { parsedCache.set(s.id, JSON.parse(s.lineIds || '[]')) }
        catch { parsedCache.set(s.id, []) }
      }
      return parsedCache.get(s.id)
    }

    // 按 lineIds 分组，收集每条线路的所有站点
    const lineStationMap = {} // lid -> station[]
    for (const station of stations) {
      const lineIds = getParsedLineIds(station)
      for (const lid of lineIds) {
        if (!lineStationMap[lid]) lineStationMap[lid] = []
        lineStationMap[lid].push(station)
      }
    }

    // 从数据库获取线路真实颜色：metroLineDbMap 的 key 是 lineName, value 是 lineId
    // metroLineList 里每个元素有 lineId 和 color（来自城市JSON）
    // 我们需要从 getMetroLineList 拿到 lineColor（数据库真实颜色）
    const dbColorMap = {} // lineId -> { name, color }
    try {
      const lineRes = await getMetroLineList({ cityId: city.value.id, pageSize: 200 })
      const dbLines = lineRes.data?.records || []
      for (const l of dbLines) {
        dbColorMap[String(l.id)] = {
          name: l.lineName,
          color: l.lineColor || '',
          lineNo: l.lineNo || '',
        }
      }
    } catch {}

    // 构建线路数据，颜色直接取数据库 lineColor
    const lineMap = {}
    let colorIdx = 0
    for (const lid of Object.keys(lineStationMap)) {
      let name = ''
      let color = ''
      // 1. 从 dbColorMap 找（key 就是 lineId）
      if (dbColorMap[lid]) {
        name = dbColorMap[lid].name || ''
        color = dbColorMap[lid].color || ''
      }
      // 2. 回退：从站点的 lineNames 字段找名字
      if (!name) {
        for (const station of lineStationMap[lid]) {
          const sLineIds = getParsedLineIds(station)
          let sLineNames = []
          try { sLineNames = JSON.parse(station.lineNames || '[]') } catch { sLineNames = [] }
          const idx = sLineIds.indexOf(lid)
          if (idx >= 0 && sLineNames[idx]) { name = sLineNames[idx]; break }
        }
      }
      // 3. 回退：从 metroLineDbMap 找名字
      if (!name) {
        for (const [n, id] of Object.entries(metroLineDbMap.value)) {
          if (String(id) === String(lid)) { name = n; break }
        }
      }
      // 4. 最终回退
      if (!name) name = `未知线路(${lid})`
      // 最终回退：调色板
      if (!color) {
        color = FALLBACK_COLORS[colorIdx % FALLBACK_COLORS.length]
      }
      colorIdx++

      const lineStations = lineStationMap[lid]
      const { ordered, connected } = orderByAdjacency(lineStations, lid)
      lineMap[lid] = { id: lid, name, color, stations: ordered, connected }
    }

    cityMapLines.value = Object.values(lineMap).filter(l => l.stations.length >= 2)
  } catch (e) {
    console.error('fetchCityMapData error:', e)
  } finally {
    mapLoading.value = false
  }
}

const parseJsonSafe = (str) => {
  try { return JSON.parse(str || '[]') } catch { return [] }
}

const orderByAdjacency = (stations, lineId) => {
  if (stations.length <= 1) return { ordered: stations, connected: stations.length > 0 }

  const idSet = new Set(stations.map(s => String(s.id)))
  const stationMap = {}
  stations.forEach(s => { stationMap[String(s.id)] = s })

  // 构建邻接表
  const adj = {}
  stations.forEach(s => { adj[String(s.id)] = new Set() })

  for (const s of stations) {
    const sid = String(s.id)
    const lineIds = parseJsonSafe(s.lineIds)
    const idx = lineIds.indexOf(lineId)

    const allPrev = parseJsonSafe(s.prevStationIds)
    const allNext = parseJsonSafe(s.nextStationIds)

    const neighbors = []
    if (idx >= 0) {
      if (idx < allPrev.length && idSet.has(String(allPrev[idx]))) neighbors.push(String(allPrev[idx]))
      if (idx < allNext.length && idSet.has(String(allNext[idx]))) neighbors.push(String(allNext[idx]))
    } else {
      for (const id of allPrev) { if (idSet.has(String(id))) neighbors.push(String(id)) }
      for (const id of allNext) { if (idSet.has(String(id))) neighbors.push(String(id)) }
    }

    for (const nid of neighbors) {
      adj[sid].add(nid)
      adj[nid].add(sid)
    }
  }

  // 检查是否有任何连接关系
  const hasAnyConnection = Object.values(adj).some(s => s.size > 0)
  if (!hasAnyConnection) {
    // 无连接数据，返回原顺序，标记为未连接
    return { ordered: stations, connected: false }
  }

  // 找端点（邻接数=1）
  const endpoints = Object.keys(adj).filter(id => adj[id].size === 1)

  if (endpoints.length > 0) {
    const start = endpoints[0]
    const ordered = []
    const visited = new Set()
    let current = start
    while (current && !visited.has(current)) {
      visited.add(current)
      if (stationMap[current]) ordered.push(stationMap[current])
      let next = null
      for (const n of adj[current]) {
        if (!visited.has(n)) { next = n; break }
      }
      current = next
    }
    // 检查是否所有站点都连通
    const allConnected = ordered.length === stations.length
    if (!allConnected) {
      // 有未访问的站点（存在支线或断裂），追加到末尾
      for (const s of stations) {
        if (!visited.has(String(s.id))) ordered.push(s)
      }
    }
    return { ordered, connected: allConnected }
  }

  // 无端点（环形）：从任意点遍历
  const start = Object.keys(adj)[0]
  const ordered = []
  const visited = new Set()
  let current = start
  while (current && !visited.has(current)) {
    visited.add(current)
    if (stationMap[current]) ordered.push(stationMap[current])
    let next = null
    for (const n of adj[current]) {
      if (!visited.has(n)) { next = n; break }
    }
    current = next
  }
  return { ordered, connected: ordered.length === stations.length }
}

const openEditDialog = () => {
  const c = city.value
  form.countryId = ensureString(c.countryId)
  form.cityName = c.cityName
  form.cityNameEn = c.cityNameEn || ''
  form.cityAlias = c.cityAlias || ''
  form.metroLineLogo = c.metroLineLogo || ''
  form.metroCount = c.metroCount
  form.metroLineCount = c.metroLineCount
  form.hsrCount = c.hsrCount
  form.metroKm = c.metroKm
  form.hsrKm = c.hsrKm
  form.population = c.population
  form.metroLines = c.metroLines || ''
  form.extra = c.extra || ''
  form.statusCode = c.statusCode
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      countryId: form.countryId,
      cityName: form.cityName,
      cityNameEn: form.cityNameEn || undefined,
      cityAlias: form.cityAlias || undefined,
      metroLineLogo: form.metroLineLogo || undefined,
      metroCount: form.metroCount,
      metroLineCount: form.metroLineCount,
      hsrCount: form.hsrCount,
      metroKm: form.metroKm,
      hsrKm: form.hsrKm,
      population: form.population,
      metroLines: form.metroLines || undefined,
      extra: form.extra || undefined,
      statusCode: form.statusCode,
    }
    if (!canEditCityName.value) delete payload.cityName
    await updateCity(city.value.id, payload)
    ElMessage.success('修改成功')
    dialogVisible.value = false
    fetchCity()
  } catch (e) { /* handled by interceptor */ }
  finally { submitting.value = false }
}

const handleDelete = async () => {
  try {
    await deleteCity(city.value.id)
    ElMessage.success('删除成功')
    router.push('/cities')
  } catch (e) { /* handled */ }
}

onMounted(() => {
  fetchCity()
  fetchCountries()
})
watch(() => route.params.id, () => { if (route.params.id) fetchCity() })
</script>

<style scoped>
.city-detail { padding: 0; }

.page-header {
  display: flex; align-items: center; gap: 16px;
  margin-bottom: 20px; flex-wrap: wrap;
}
.page-title { font-size: 20px; font-weight: 600; color: #303133; flex: 1; }
.header-actions { display: flex; gap: 8px; }

.detail-content { display: flex; flex-direction: column; gap: 16px; }

.info-card .card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }

/* ── 地图切换按钮 ── */
.map-toggle {
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

.val-em { font-weight: 600; color: #303133; font-size: 15px; }
.val-num { font-family: 'Courier New', monospace; font-size: 14px; color: #409EFF; font-weight: 500; }

.metro-section { margin-bottom: 24px; }
.metro-section:last-child { margin-bottom: 0; }

.section-label {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 14px;
}
.section-label .el-icon { color: #409EFF; font-size: 16px; }

.metro-line-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}

.metro-line-card {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border-radius: 8px;
  background: #fafbfc; border: 1px solid #ebeef5;
  transition: all 0.25s ease; cursor: default;
  position: relative; overflow: hidden;
}
.metro-line-card.clickable { cursor: pointer; }
.metro-line-card.clickable:hover { border-color: #409EFF; transform: translateY(-2px); box-shadow: 0 4px 16px rgba(64,158,255,0.12); }
.metro-line-card.clickable:hover .line-index { color: #409EFF; }
.metro-line-card::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0;
  width: 4px; background: var(--line-color);
}
.metro-line-card:hover {
  background: #f0f5ff; border-color: var(--line-color);
  transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,0.06);
}

.line-dot {
  width: 18px; height: 18px; border-radius: 50%;
  flex-shrink: 0; box-shadow: 0 0 0 2px #fff, 0 0 0 3px currentColor;
  color: var(--line-color);
}

.line-info { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.line-name { font-size: 14px; font-weight: 600; color: #303133; }
.line-name-en { font-size: 11px; color: #909399; margin-top: 1px; }

.line-index {
  font-size: 18px; font-weight: 700; color: #dcdfe6;
  font-family: 'Courier New', monospace; flex-shrink: 0;
}

.color-dot {
  display: inline-block; width: 10px; height: 10px; border-radius: 50%;
  margin-right: 6px; vertical-align: middle;
}
.table-line-name { display: flex; align-items: center; }

.metro-table { border-radius: 8px; overflow: hidden; }
.metro-table .el-table__row.clickable-row { cursor: pointer; }
.metro-table .el-table__row.clickable-row:hover td { background-color: #f0f5ff; }

.extra-section { margin-top: 0; }
.extra-json-card {
  background: linear-gradient(135deg, #1e1e2e 0%, #2a2a3e 100%);
  border-radius: 10px; padding: 16px 20px;
  border: 1px solid #3a3a50;
}
.extra-json {
  margin: 0; font-size: 13px; line-height: 1.7;
  color: #cdd6f4; font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  max-height: 280px; overflow-y: auto; white-space: pre-wrap; word-break: break-all;
}

.field-tip { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
