<template>
  <div class="station-detail" v-loading="loading">
    <div class="hero-banner" :style="heroStyle">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <el-button class="back-btn" :icon="ArrowLeft" circle @click="goBack" />
        <div class="hero-main">
          <div class="hero-station-icon">
            <el-icon :size="32"><MapLocation /></el-icon>
          </div>
          <div class="hero-text">
            <h1 class="hero-name">{{ station.stationName || '加载中...' }}</h1>
            <div class="hero-sub">
              <el-tag size="small" effect="dark" :style="tagBgStyle">{{ station.status || '未知' }}</el-tag>
              <span class="hero-location">{{ station.cityName }} · {{ station.countryName }}</span>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <el-button type="primary" :icon="Edit" @click="openEditDialog">编辑站点</el-button>
          <el-popconfirm
            v-if="isRootAdmin"
            title="确定删除该站点？此操作不可逆"
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
              <span class="info-label">站点ID</span>
              <code class="info-val">{{ station.id }}</code>
            </div>
            <div class="info-item">
              <span class="info-label">所属国家</span>
              <span class="info-val">{{ station.countryName || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属城市</span>
              <span class="info-val">{{ station.cityName || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">中文站名</span>
              <span class="info-val"><strong>{{ station.stationName }}</strong></span>
            </div>
            <div class="info-item" v-if="station.stationNameEn">
              <span class="info-label">英文站名</span>
              <span class="info-val">{{ station.stationNameEn }}</span>
            </div>
            <div class="info-item" v-if="station.stationAlias">
              <span class="info-label">别称</span>
              <span class="info-val">{{ station.stationAlias }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">站点类型</span>
              <el-tag size="small">{{ stationTypeMap[station.stationType] || '未知' }}</el-tag>
            </div>
            <div class="info-item">
              <span class="info-label">换乘站</span>
              <el-tag :type="station.isTransfer ? 'warning' : 'info'" size="small">{{ station.isTransfer ? '是' : '否' }}</el-tag>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="detail-card">
          <template #header>
            <div class="card-hd"><el-icon><Location /></el-icon><span>位置信息</span></div>
          </template>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">经度</span>
              <span class="info-val num">{{ station.longitude }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">纬度</span>
              <span class="info-val num">{{ station.latitude }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">出口数量</span>
              <span class="info-val num">{{ station.exitCount ?? '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">是否有厕所</span>
              <span class="info-val">{{ station.hasToilet ? '有' : '无' }}</span>
            </div>
            <div class="info-item" v-if="station.openDate">
              <span class="info-label">开通日期</span>
              <span class="info-val">{{ station.openDate }}</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="detail-card" v-if="station.firstTime || station.lastTime">
          <template #header>
            <div class="card-hd"><el-icon><Clock /></el-icon><span>运营时间</span></div>
          </template>
          <div class="info-list">
            <div class="info-item" v-if="station.firstTime">
              <span class="info-label">首班车</span>
              <span class="info-val num">{{ station.firstTime }}</span>
            </div>
            <div class="info-item" v-if="station.lastTime">
              <span class="info-label">末班车</span>
              <span class="info-val num">{{ station.lastTime }}</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="detail-card" v-if="hasLineInfo">
          <template #header>
            <div class="card-hd"><el-icon><Connection /></el-icon><span>所属线路</span></div>
          </template>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">线路名称</span>
              <div class="line-tags">
                <el-tag
                  v-for="(name, idx) in lineNames"
                  :key="idx"
                  :style="{ background: '#409EFF', borderColor: '#409EFF' }"
                  effect="dark"
                  size="small"
                  style="margin:2px;color:#fff"
                >
                  {{ name }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="detail-card" v-if="hasPrevNext">
          <template #header>
            <div class="card-hd"><el-icon><Sort /></el-icon><span>前后站点</span></div>
          </template>
          <div class="prev-next-section">
            <div class="pn-block" v-if="station.prevStationNames">
              <h4>前序站点</h4>
              <div class="pn-stations">
                <div class="pn-item" v-for="(name, idx) in prevNames" :key="'p'+idx">
                  <el-icon><DArrowLeft /></el-icon>
                  <span>{{ name }}</span>
                  <span v-if="prevDistances[idx]" class="pn-dist">{{ prevDistances[idx] }} km</span>
                </div>
              </div>
            </div>
            <div class="pn-arrow"><el-icon :size="28"><DArrowRight /></el-icon></div>
            <div class="pn-block current-station-block">
              <el-tag type="primary" size="large">{{ station.stationName }}</el-tag>
            </div>
            <div class="pn-arrow"><el-icon :size="28"><DArrowRight /></el-icon></div>
            <div class="pn-block" v-if="station.nextStationNames">
              <h4>后序站点</h4>
              <div class="pn-stations">
                <div class="pn-item" v-for="(name, idx) in nextNames" :key="'n'+idx">
                  <span>{{ name }}</span>
                  <span v-if="nextDistances[idx]" class="pn-dist">{{ nextDistances[idx] }} km</span>
                  <el-icon><DArrowLeft /></el-icon>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="editDialogVisible" title="编辑地铁站" width="700px" :close-on-click-modal="false" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="国家" prop="countryId">
              <el-select v-model="editForm.countryId" placeholder="请选择国家" filterable style="width:100%" @change="onEditCountryChange">
                <el-option v-for="c in countryOptions" :key="c.id" :label="c.countryName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市" prop="cityId">
              <el-select v-model="editForm.cityId" placeholder="请选择城市" filterable style="width:100%">
                <el-option v-for="c in editCityOptions" :key="c.id" :label="c.cityName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="站点名">
              <el-input v-model="editForm.stationName" :disabled="!canEditAllFields" placeholder="中文站名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名">
              <el-input v-model="editForm.stationNameEn" placeholder="英文站名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="经度" prop="longitude">
              <el-input v-model="editForm.longitude" placeholder="经度" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="纬度" prop="latitude">
              <el-input v-model="editForm.latitude" placeholder="纬度" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="别称">
              <el-input v-model="editForm.stationAlias" placeholder="别名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="站点类型">
              <el-select v-model="editForm.stationType" style="width:100%">
                <el-option v-for="(v,k) in stationTypeMap" :key="Number(k)" :label="v" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换乘站">
              <el-switch v-model="editForm.isTransfer" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出口数">
              <el-input-number v-model="editForm.exitCount" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="厕所">
              <el-switch v-model="editForm.hasToilet" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开通日期">
              <el-date-picker v-model="editForm.openDate" type="date" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="editForm.statusCode" style="width:100%">
                <el-option v-for="(v,k) in statusMap" :key="Number(k)" :label="v" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="首班车">
              <el-time-picker v-model="editForm.firstTime" format="HH:mm:ss" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="末班车">
              <el-time-picker v-model="editForm.lastTime" format="HH:mm:ss" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="所属线路">
              <el-select v-model="detailSelectedLines" multiple filterable placeholder="请先选择城市，再选择线路" style="width:100%">
                <el-option v-for="line in detailLineOptions" :key="line.id" :label="line.lineName" :value="line.id">
                  <span class="line-opt-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
                  <span>{{ line.lineName }}</span>
                  <span v-if="line.lineNo" style="color:#909399;font-size:12px;margin-left:4px">({{ line.lineNo }})</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="候选线路筛选">
              <el-select v-model="detailCandidateLineFilter" clearable placeholder="筛选候选站所属线路" style="width:100%">
                <el-option v-for="line in detailLineOptions" :key="line.id" :label="line.lineName" :value="line.id">
                  <span class="line-opt-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
                  <span>{{ line.lineName }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="detailLatLngWarnings.length > 0"
          :title="'以下选定站点缺少经纬度，无法计算距离：' + detailLatLngWarnings.join('、')"
          type="warning" show-icon :closable="false" style="margin-bottom:12px; font-size:12px;"
        />
        <el-form-item label="前序站">
          <StationSelector
            :station-options="filteredDetailCandidates"
            :selected="detailPrevSelected"
            :is-transfer="editForm.isTransfer"
            @update:selected="detailPrevSelected = $event"
            @recalculate="recalculateDetailDistances('prev')"
          />
        </el-form-item>
        <el-form-item label="后序站">
          <StationSelector
            :station-options="filteredDetailCandidates"
            :selected="detailNextSelected"
            :is-transfer="editForm.isTransfer"
            @update:selected="detailNextSelected = $event"
            @recalculate="recalculateDetailDistances('next')"
          />
        </el-form-item>
        <el-form-item label="扩展数据">
          <el-input v-model="editForm.extra" type="textarea" :rows="2" placeholder="JSON扩展" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleEditSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, MapLocation, Edit, Delete, InfoFilled, Location, Clock, Connection, Sort, DArrowLeft, DArrowRight, Odometer, Timer } from '@element-plus/icons-vue'
import { getMetroStationDetail, updateMetroStation, deleteMetroStation } from '@/api/metroStation'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { getMetroLineList } from '@/api/metroLine'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { normalizeList, ensureString } from '@/utils/normalize'
import StationSelector from './components/StationSelector.vue'
import { haversineDistance } from '@/utils/geo'

const route = useRoute()
const router = useRouter()
const { isRootAdmin, isSuperAdmin, canEditAllFields } = usePermission()
const { state: config } = useSystemConfig()

const stationTypeMap = config.typeMap
const statusMap = config.statusMap
const statusTagColors = { 0: '#909399', 1: '#409EFF', 2: '#E6A23C', 3: '#909399', 4: '#F56C6C' }

const loading = ref(false)
const submitting = ref(false)
const station = ref({})

const editDialogVisible = ref(false)
const editFormRef = ref(null)
const countryOptions = ref([])
const allCityOptions = ref([])
const detailLineOptions = ref([])
const detailSelectedLines = ref([])

const editForm = reactive({
  countryId: null, cityId: null, stationName: '', stationNameEn: '', stationAlias: '',
  longitude: '', latitude: '', isTransfer: 0, lineIds: '', lineNames: '',
  exitCount: 0, hasToilet: 0, stationType: 0,
  openDate: '', firstTime: '', lastTime: '',
  statusCode: 0, extra: '',
})

const detailCandidateStations = ref([])
const detailCandidateLineFilter = ref(null)
const detailPrevSelected = ref([])
const detailNextSelected = ref([])
const detailLatLngWarnings = ref([])

const filteredDetailCandidates = computed(() => {
  let list = detailCandidateStations.value
  if (detailCandidateLineFilter.value) {
    list = list.filter(s => {
      try {
        const ids = JSON.parse(s.lineIds || '[]')
        return ids.includes(detailCandidateLineFilter.value)
      } catch { return false }
    })
  }
  return list
})

const fetchDetailLinesByCity = async (cityId) => {
  if (!cityId) { detailLineOptions.value = []; detailCandidateStations.value = []; return }
  try {
    const [lineRes, stationRes] = await Promise.all([
      getMetroLineList({ cityId, pageSize: 999 }),
      getMetroStationList({ cityId, pageSize: 999 }),
    ])
    detailLineOptions.value = lineRes.data?.records || []
    detailCandidateStations.value = stationRes.data?.records || []
  } catch { detailLineOptions.value = []; detailCandidateStations.value = [] }
}

watch(() => editForm.cityId, (val) => { fetchDetailLinesByCity(val) })

watch([detailPrevSelected, detailNextSelected], () => { checkDetailLatLngWarnings() }, { deep: true })

const editRules = {
  countryId: [{ required: true, message: '请选择国家', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  longitude: [{ required: true, message: '请输入经度', trigger: 'blur' }],
  latitude: [{ required: true, message: '请输入纬度', trigger: 'blur' }],
}

const editCityOptions = computed(() => {
  if (!editForm.countryId) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === editForm.countryId)
})
const onEditCountryChange = () => {
  if (editForm.cityId && !editCityOptions.value.find(c => c.id === editForm.cityId)) editForm.cityId = null
}

const parseJsonArr = (val) => {
  if (!val) return []
  try { const arr = JSON.parse(val); return Array.isArray(arr) ? arr : [] }
  catch { return [] }
}

const lineNames = computed(() => parseJsonArr(station.value.lineNames))
const hasLineInfo = computed(() => lineNames.value.length > 0)

const prevNames = computed(() => parseJsonArr(station.value.prevStationNames))
const prevDistances = computed(() => parseJsonArr(station.value.prevStationDistances))
const nextNames = computed(() => parseJsonArr(station.value.nextStationNames))
const nextDistances = computed(() => parseJsonArr(station.value.nextStationDistances))
const hasPrevNext = computed(() => station.value.prevStationNames || station.value.nextStationNames)

const heroStyle = computed(() => ({
  background: `linear-gradient(135deg, #409EFF 0%, #337ECC 50%, #1a5276 100%)`,
}))
const tagBgStyle = computed(() => {
  const sc = station.value.statusCode
  return { background: statusTagColors[sc] || '#909399', borderColor: statusTagColors[sc] || '#909399' }
})

const stats = computed(() => [
  { label: '出口数', value: station.value.exitCount ?? '-', bg: 'rgba(103,194,58,0.2)', icon: Odometer },
  { label: '换乘站', value: station.value.isTransfer ? '是' : '否', bg: 'rgba(230,162,60,0.2)', icon: Connection },
  { label: '厕所', value: station.value.hasToilet ? '有' : '无', bg: 'rgba(64,158,255,0.2)', icon: Timer },
  { label: '站点类型', value: stationTypeMap[station.value.stationType] || '未知', bg: 'rgba(144,147,153,0.2)', icon: MapLocation },
])

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMetroStationDetail(route.params.id)
    station.value = res.data || {}
  } catch {
    ElMessage.error('获取站点详情失败')
  } finally { loading.value = false }
}

const fetchOptions = async () => {
  try { const r = await getCountryAll(); countryOptions.value = normalizeList(r.data || [], ['id']) } catch {}
  try { const r = await getCityAll(); allCityOptions.value = normalizeList(r.data || [], ['id', 'countryId']) } catch {}
}

const goBack = () => router.back()

const buildDetailSelectedFromRaw = (idsJson, namesJson, distsJson, allStations) => {
  try {
    const ids = JSON.parse(idsJson || '[]')
    const names = JSON.parse(namesJson || '[]')
    const dists = JSON.parse(distsJson || '[]')
    if (!Array.isArray(ids)) return []
    return ids.map((id, i) => {
      const station = allStations.find(s => s.id === id)
      let lineId = null; let lineName = null
      if (station) {
        try {
          const lIds = JSON.parse(station.lineIds || '[]')
          const lNames = JSON.parse(station.lineNames || '[]')
          if (Array.isArray(lIds) && lIds.length > 0) lineId = lIds[0]
          if (Array.isArray(lNames) && lNames.length > 0) lineName = lNames[0]
        } catch { /* ignore */ }
      }
      return {
        stationId: id,
        stationName: names[i] || '',
        distance: dists[i] != null ? String(dists[i]) : '',
        lineId,
        lineName,
      }
    })
  } catch { return [] }
}

const buildDetailRawFields = (selected) => ({
  stationIds: JSON.stringify(selected.map(s => s.stationId)),
  stationNames: JSON.stringify(selected.map(s => s.stationName)),
  stationDistances: JSON.stringify(selected.map(s => {
    const d = parseFloat(s.distance)
    return isNaN(d) ? 0 : d
  })),
})

const checkDetailLatLngWarnings = () => {
  const all = [...detailPrevSelected.value, ...detailNextSelected.value]
  const missing = all.filter(s => {
    const station = detailCandidateStations.value.find(c => c.id === s.stationId)
    return station && (!station.latitude || !station.longitude)
  }).map(s => s.stationName)
  detailLatLngWarnings.value = [...new Set(missing)]
}

const recalculateDetailDistances = (dir) => {
  const selfLat = parseFloat(editForm.latitude)
  const selfLng = parseFloat(editForm.longitude)
  if (isNaN(selfLat) || isNaN(selfLng)) {
    ElMessage.warning('请先填写当前站点的经纬度')
    return
  }
  const list = dir === 'prev' ? detailPrevSelected : detailNextSelected
  list.value = list.value.map(s => {
    const station = detailCandidateStations.value.find(c => c.id === s.stationId)
    if (station && station.latitude && station.longitude) {
      return { ...s, distance: haversineDistance(selfLat, selfLng, Number(station.latitude), Number(station.longitude)) }
    }
    return s
  })
}

const openEditDialog = async () => {
  const s = station.value
  Object.assign(editForm, {
    countryId: ensureString(s.countryId), cityId: ensureString(s.cityId),
    stationName: s.stationName, stationNameEn: s.stationNameEn || '', stationAlias: s.stationAlias || '',
    longitude: s.longitude != null ? String(s.longitude) : '',
    latitude: s.latitude != null ? String(s.latitude) : '',
    isTransfer: s.isTransfer, lineIds: s.lineIds || '', lineNames: s.lineNames || '',
    exitCount: s.exitCount || 0, hasToilet: s.hasToilet || 0, stationType: s.stationType || 0,
    openDate: s.openDate || '', firstTime: s.firstTime || '', lastTime: s.lastTime || '',
    statusCode: s.statusCode != null ? s.statusCode : 0, extra: s.extra || '',
  })
  // 预选择已有线路
  detailCandidateLineFilter.value = null
  await fetchDetailLinesByCity(s.cityId)
  if (s.lineIds) {
    try {
      const ids = JSON.parse(s.lineIds)
      if (Array.isArray(ids)) {
        detailSelectedLines.value = ids.map(String).filter(id => detailLineOptions.value.some(l => String(l.id) === id))
      }
    } catch {
      if (String(s.lineIds).trim()) {
        detailSelectedLines.value = detailLineOptions.value
          .filter(l => String(s.lineIds).includes(String(l.id)))
          .map(l => l.id)
      }
    }
  }
  // 回填前序/后序站点数据
  detailPrevSelected.value = buildDetailSelectedFromRaw(s.prevStationIds, s.prevStationNames, s.prevStationDistances, detailCandidateStations.value)
  detailNextSelected.value = buildDetailSelectedFromRaw(s.nextStationIds, s.nextStationNames, s.nextStationDistances, detailCandidateStations.value)
  editDialogVisible.value = true
}

const buildDetailLinePayload = () => {
  if (!detailSelectedLines.value.length) return { lineIds: undefined, lineNames: undefined }
  const selected = detailLineOptions.value.filter(l => detailSelectedLines.value.includes(l.id))
  return {
    lineIds: JSON.stringify(selected.map(l => l.id)),
    lineNames: JSON.stringify(selected.map(l => l.lineName)),
  }
}

const handleEditSubmit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const linePayload = buildDetailLinePayload()
    const prevRaw = detailPrevSelected.value.length > 0 ? buildDetailRawFields(detailPrevSelected.value) : {}
    const nextRaw = detailNextSelected.value.length > 0 ? buildDetailRawFields(detailNextSelected.value) : {}
    await updateMetroStation(route.params.id, {
      countryId: editForm.countryId,
      cityId: editForm.cityId,
      stationName: canEditAllFields.value ? editForm.stationName : undefined,
      stationNameEn: editForm.stationNameEn || undefined,
      stationAlias: editForm.stationAlias || undefined,
      longitude: parseFloat(editForm.longitude), latitude: parseFloat(editForm.latitude),
      isTransfer: editForm.isTransfer,
      ...linePayload,
      exitCount: editForm.exitCount, hasToilet: editForm.hasToilet, stationType: editForm.stationType,
      openDate: editForm.openDate || undefined, firstTime: editForm.firstTime || undefined, lastTime: editForm.lastTime || undefined,
      prevStationIds: prevRaw.stationIds || undefined,
      prevStationNames: prevRaw.stationNames || undefined,
      prevStationDistances: prevRaw.stationDistances || undefined,
      nextStationIds: nextRaw.stationIds || undefined,
      nextStationNames: nextRaw.stationNames || undefined,
      nextStationDistances: nextRaw.stationDistances || undefined,
      statusCode: editForm.statusCode, extra: editForm.extra || undefined,
    })
    ElMessage.success('更新成功')
    editDialogVisible.value = false
    fetchData()
  } catch {} finally { submitting.value = false }
}

const handleDelete = async () => {
  try {
    await deleteMetroStation(route.params.id)
    ElMessage.success('已删除')
    router.push('/metro-stations')
  } catch {}
}

watch(() => route.params.id, () => fetchData())
onMounted(() => { fetchData(); fetchOptions() })
</script>

<style scoped>
.station-detail { animation: fadeUp 0.4s ease-out; }
@keyframes fadeUp { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }

.hero-banner {
  position: relative; border-radius: 16px; overflow: hidden;
  padding: 32px 36px; margin-bottom: 20px;
  color: #fff; background-size: 200% 200%;
  animation: bgShift 8s ease-in-out infinite alternate;
}
@keyframes bgShift { 0% { background-position: 0% 50%; } 100% { background-position: 100% 50%; } }
.hero-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.08); }
.hero-content {
  position: relative; z-index: 1;
  display: flex; align-items: center; gap: 24px; flex-wrap: wrap;
}
.back-btn { background: rgba(255,255,255,0.2); border: none; color: #fff; }
.back-btn:hover { background: rgba(255,255,255,0.35); }
.hero-main { display: flex; align-items: center; gap: 20px; flex: 1; }
.hero-station-icon {
  width: 64px; height: 64px; border-radius: 50%;
  background: rgba(255,255,255,0.2); display: flex;
  align-items: center; justify-content: center;
}
.hero-name { margin: 0; font-size: 26px; font-weight: 700; }
.hero-sub { display: flex; align-items: center; gap: 12px; margin-top: 6px; font-size: 13px; opacity: 0.9; }
.hero-location { opacity: 0.85; }
.hero-actions { display: flex; gap: 8px; }
.line-opt-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }

.detail-body { padding: 4px 0; }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px; }
@media (max-width: 900px) { .stats-row { grid-template-columns: repeat(2, 1fr); } }
.stat-card {
  background: #fff; border-radius: 12px; padding: 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
.stat-icon { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.stat-val { font-size: 18px; font-weight: 700; color: #303133; }
.stat-label { font-size: 12px; color: #909399; display: block; margin-top: 2px; }

.cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(400px, 1fr)); gap: 16px; }
@media (max-width: 900px) { .cards-grid { grid-template-columns: 1fr; } }
.detail-card { border-radius: 12px; }
.card-hd { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 15px; }
.info-list { display: flex; flex-direction: column; gap: 12px; }
.info-item { display: flex; justify-content: space-between; align-items: center; font-size: 14px; }
.info-label { color: #909399; min-width: 70px; }
.info-val { color: #303133; text-align: right; word-break: break-all; }
.info-val.num { font-family: monospace; font-size: 14px; color: #409EFF; }

.line-tags { display: flex; flex-wrap: wrap; gap: 4px; justify-content: flex-end; }

.prev-next-section {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
}
.pn-block { flex: 1; min-width: 120px; }
.pn-block h4 { margin: 0 0 8px; font-size: 13px; color: #909399; }
.pn-stations { display: flex; flex-direction: column; gap: 6px; }
.pn-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #303133; }
.pn-dist { color: #909399; font-size: 11px; }
.pn-arrow { color: #c0c4cc; }
.current-station-block { display: flex; align-items: center; justify-content: center; padding: 8px 0; }
</style>
