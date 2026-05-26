<template>
  <div class="manage-page">
    <div class="page-header">
      <h2><el-icon><MapLocation /></el-icon> 地铁站管理</h2>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增地铁站</el-button>
        <el-button :icon="Upload" @click="openImportDialog">批量导入</el-button>
        <el-button type="danger" :icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除 ({{ selectedIds.length }})
        </el-button>
        <el-button type="success" :icon="Connection" @click="$router.push('/station-line-assign')">线路分配</el-button>
        <el-button type="warning" :icon="Download" :disabled="selectedIds.length === 0" @click="exportSelected">
          导出选中 ({{ selectedIds.length }})
        </el-button>
        <el-button type="warning" plain :icon="Download" @click="exportAll">导出全部</el-button>
      </div>
    </div>

    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索站名/英文名/别称..." :prefix-icon="Search" clearable style="width:240px" @clear="handleSearch" @keyup.enter="handleSearch" />

      <el-select v-model="filterCountryId" placeholder="按国家筛选" clearable style="width:180px" @change="handleCountryFilterChange">
        <el-option v-for="c in countryOptions" :key="c.id" :label="c.countryName" :value="c.id" />
      </el-select>

      <el-select v-model="filterCityId" placeholder="按城市筛选" clearable filterable style="width:180px" @change="handleSearch">
        <el-option v-for="c in filteredCityOptions" :key="c.id" :label="c.cityName" :value="c.id" />
      </el-select>

      <el-select v-model="filterIsTransfer" placeholder="换乘站" clearable style="width:110px" @change="handleSearch">
        <el-option label="换乘站" :value="1" />
        <el-option label="非换乘" :value="0" />
      </el-select>

      <el-select v-model="filterStatusCode" placeholder="状态" clearable style="width:110px" @change="handleSearch">
        <el-option v-for="(label, code) in statusMap" :key="Number(code)" :value="Number(code)" :label="label" />
      </el-select>

      <el-select v-model="filterLineId" placeholder="线路筛选" clearable filterable style="width:180px" @change="handleSearch">
        <el-option v-for="line in filteredLineOptions" :key="line.id" :label="line.lineName" :value="line.id">
          <span class="line-opt-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
          <span>{{ line.lineName }}</span>
        </el-option>
      </el-select>

      <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
      <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
    </div>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" style="margin-bottom:12px" />

    <el-table
      ref="tableRef"
      :data="tableData"
      v-loading="loading"
      border
      stripe
      style="width:100%"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="stationName" label="站名" min-width="130" sortable="custom" show-overflow-tooltip>
        <template #default="{ row }">
          <b class="station-name-link" @click="goToDetail(row.id)">{{ row.stationName }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="stationNameEn" label="英文名" min-width="150" show-overflow-tooltip />
      <el-table-column label="国家" width="90">
        <template #default="{ row }">
          {{ row.countryName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="cityName" label="城市" width="80" sortable="custom" />
      <el-table-column prop="longitude" label="经度" width="95" sortable="custom" align="right">
        <template #default="{ row }">
          <span style="font-size:12px;font-family:monospace">{{ row.longitude }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="latitude" label="纬度" width="95" sortable="custom" align="right">
        <template #default="{ row }">
          <span style="font-size:12px;font-family:monospace">{{ row.latitude }}</span>
        </template>
      </el-table-column>
      <el-table-column label="换乘" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isTransfer ? 'warning' : 'info'" size="small">{{ row.isTransfer ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lineNames" label="所属线路" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.lineNames">{{ formatJsonArr(row.lineNames) }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="75" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ stationTypeMap[row.stationType] || '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center" sortable="custom" prop="statusCode">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.statusCode)" size="small">{{ row.status || statusMap[row.statusCode] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
          <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      class="pagination-bar"
      :current-page="pageNum"
      :page-size="pageSize"
      :page-sizes="config.pageSizeOptions"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />

    <el-dialog
      v-model="dialogVisible" :title="dialogTitle" width="700px"
      :close-on-click-modal="false" destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="国家" prop="countryId">
              <el-select v-model="form.countryId" :disabled="!canEditAllFields" placeholder="请选择国家" filterable style="width:100%" @change="onCountryChange">
                <el-option v-for="c in countryOptions" :key="c.id" :label="c.countryName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="城市" prop="cityId">
              <el-select v-model="form.cityId" :disabled="!canEditAllFields" placeholder="请选择城市" filterable style="width:100%">
                <el-option v-for="c in dialogCityOptions" :key="c.id" :label="c.cityName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="站点名" prop="stationName">
              <el-input v-model="form.stationName" :disabled="!canEditAllFields" placeholder="中文站名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名">
              <el-input v-model="form.stationNameEn" placeholder="英文站名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="经度" prop="longitude">
              <el-input v-model="form.longitude" placeholder="如 116.397428" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="纬度" prop="latitude">
              <el-input v-model="form.latitude" placeholder="如 39.90923" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="别称">
              <el-input v-model="form.stationAlias" placeholder="别名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="站点类型">
              <el-select v-model="form.stationType" placeholder="类型" style="width:100%">
                <el-option v-for="(v,k) in stationTypeMap" :key="Number(k)" :label="v" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="换乘站">
              <el-switch v-model="form.isTransfer" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出口数">
              <el-input-number v-model="form.exitCount" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="厕所">
              <el-switch v-model="form.hasToilet" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开通日期">
              <el-date-picker v-model="form.openDate" type="date" placeholder="选择日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.statusCode" placeholder="状态" style="width:100%">
                <el-option v-for="(v,k) in statusMap" :key="Number(k)" :label="v" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="首班车">
              <el-time-picker v-model="form.firstTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="首班车" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="末班车">
              <el-time-picker v-model="form.lastTime" format="HH:mm:ss" value-format="HH:mm:ss" placeholder="末班车" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="所属线路">
              <el-select v-model="selectedLines" multiple filterable placeholder="请先选择城市，再选择线路" style="width:100%">
                <el-option v-for="line in metroLineOptions" :key="line.id" :label="line.lineName" :value="line.id">
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
              <el-select v-model="candidateLineFilter" clearable placeholder="筛选候选站所属线路" style="width:100%">
                <el-option v-for="line in metroLineOptions" :key="line.id" :label="line.lineName" :value="line.id">
                  <span class="line-opt-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
                  <span>{{ line.lineName }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="latLngWarnings.length > 0"
          :title="'以下选定站点缺少经纬度，无法计算距离：' + latLngWarnings.join('、')"
          type="warning" show-icon :closable="false" style="margin-bottom:12px; font-size:12px;"
        />
        <el-form-item label="前序站">
          <StationSelector
            :station-options="filteredCandidateStations"
            :selected="prevSelectedStations"
            :is-transfer="form.isTransfer"
            @update:selected="prevSelectedStations = $event"
            @recalculate="recalculateDistances('prev')"
          />
        </el-form-item>
        <el-form-item label="后序站">
          <StationSelector
            :station-options="filteredCandidateStations"
            :selected="nextSelectedStations"
            :is-transfer="form.isTransfer"
            @update:selected="nextSelectedStations = $event"
            @recalculate="recalculateDistances('next')"
          />
        </el-form-item>
        <el-form-item label="扩展数据">
          <el-input v-model="form.extra" type="textarea" :rows="2" placeholder='JSON格式扩展数据' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ImportDialog ref="importDialogRef" title="批量导入地铁站" :import-fn="batchImportMetroStations" excel-headers="Excel列: 国家|城市|站名|英文名|别称|经度|纬度|换乘|线路ID|线路名|出口数|厕所|类型|开通日期|首班|末班|状态码|扩展（国家/城市使用名称，系统自动匹配）" @success="fetchData" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Upload, Delete, RefreshRight, Edit, MapLocation, Download, Connection } from '@element-plus/icons-vue'
import { getMetroStationList, createMetroStation, updateMetroStation, deleteMetroStation, batchDeleteMetroStations, batchImportMetroStations } from '@/api/metroStation'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { getMetroLineList } from '@/api/metroLine'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'
import ImportDialog from '@/components/ImportDialog.vue'
import StationSelector from './components/StationSelector.vue'
import { haversineDistance } from '@/utils/geo'
import { normalizeList, ensureString } from '@/utils/normalize'
import { downloadExcel } from '@/utils/download'

const { isSuperAdmin, isRootAdmin, canEditAllFields } = usePermission()
const { state: config } = useSystemConfig()
const router = useRouter()

const stationTypeMap = config.typeMap
const statusMap = config.statusMap
const statusTagType = (code) => {
  const map = { 0: 'info', 1: 'success', 2: 'warning', 3: '', 4: 'danger' }
  return map[code] || 'info'
}

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const pageNum = ref(1)
const pageSize = ref(config.defaultPageSize)
const pageSizeInited = ref(false)
const total = ref(0)
const sortField = ref('createdAt')
const sortOrder = ref('desc')
const keyword = ref('')
const filterCountryId = ref(null)
const filterCityId = ref(null)
const filterIsTransfer = ref(null)
const filterStatusCode = ref(null)
const filterLineId = ref(null)
const selectedIds = ref([])
const errorMsg = ref('')

const countryOptions = ref([])
const allCityOptions = ref([])
const allLineOptions = ref([])
const metroLineOptions = ref([])
const selectedLines = ref([])

const filteredCityOptions = computed(() => {
  if (!filterCountryId.value) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === filterCountryId.value)
})

const filteredLineOptions = computed(() => {
  if (!filterCityId.value) return allLineOptions.value
  return allLineOptions.value.filter(l => String(l.cityId) === String(filterCityId.value))
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增地铁站')
const isEdit = ref(false)
const editId = ref(null)
const formRef = ref(null)
const importDialogRef = ref(null)

const form = reactive({
  countryId: null, cityId: null, stationName: '', stationNameEn: '', stationAlias: '',
  longitude: '', latitude: '', isTransfer: 0, lineIds: '', lineNames: '',
  exitCount: 0, hasToilet: 0, stationType: 0,
  openDate: '', firstTime: '', lastTime: '',
  statusCode: 0, extra: '',
})

const candidateStations = ref([])
const candidateLineFilter = ref(null)
const prevSelectedStations = ref([])
const nextSelectedStations = ref([])
const latLngWarnings = ref([])

const filteredCandidateStations = computed(() => {
  let list = candidateStations.value
  if (candidateLineFilter.value) {
    list = list.filter(s => {
      try {
        const ids = JSON.parse(s.lineIds || '[]')
        return ids.includes(candidateLineFilter.value)
      } catch { return false }
    })
  }
  return list
})

const fetchLinesByCity = async (cityId) => {
  if (!cityId) { metroLineOptions.value = []; candidateStations.value = []; return }
  try {
    const [lineRes, stationRes] = await Promise.all([
      getMetroLineList({ cityId, pageSize: 999 }),
      getMetroStationList({ cityId, pageSize: 999 }),
    ])
    metroLineOptions.value = lineRes.data?.records || []
    candidateStations.value = stationRes.data?.records || []
  } catch { metroLineOptions.value = []; candidateStations.value = [] }
}

watch(() => form.cityId, (val) => { fetchLinesByCity(val) })

watch([prevSelectedStations, nextSelectedStations], () => { checkLatLngWarnings() }, { deep: true })

const rules = {
  countryId: [{ required: true, message: '请选择国家', trigger: 'change' }],
  cityId: [{ required: true, message: '请选择城市', trigger: 'change' }],
  stationName: [{ required: true, message: '请输入站名', trigger: 'blur' }],
  longitude: [{ required: true, message: '请输入经度', trigger: 'blur' }],
  latitude: [{ required: true, message: '请输入纬度', trigger: 'blur' }],
}

const dialogCityOptions = computed(() => {
  if (!form.countryId) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === form.countryId)
})

const onCountryChange = () => {
  if (form.cityId && !dialogCityOptions.value.find(c => c.id === form.cityId)) form.cityId = null
}
const handleCountryFilterChange = () => { filterCityId.value = null; filterLineId.value = null; handleSearch() }

watch(filterCityId, (val, oldVal) => {
  if (val !== oldVal) {
    // 城市变更时重置线路筛选（如果当前选中的线路不属于新城市）
    if (filterLineId.value && val) {
      const match = allLineOptions.value.find(l => String(l.id) === String(filterLineId.value) && String(l.cityId) === String(val))
      if (!match) filterLineId.value = null
    } else if (!val) {
      filterLineId.value = null
    }
  }
})

const formatJsonArr = (val) => {
  if (!val) return ''
  try { const arr = JSON.parse(val); return Array.isArray(arr) ? arr.join(' / ') : val }
  catch { return val }
}

const fetchData = async () => {
  loading.value = true; errorMsg.value = ''
  try {
    const params = {
      keyword: keyword.value || undefined,
      countryId: filterCountryId.value || undefined,
      cityId: filterCityId.value || undefined,
      isTransfer: filterIsTransfer.value != null ? filterIsTransfer.value : undefined,
      statusCode: filterStatusCode.value != null ? filterStatusCode.value : undefined,
      lineId: filterLineId.value || undefined,
      sortField: sortField.value, sortOrder: sortOrder.value,
      pageNum: pageNum.value, pageSize: pageSize.value,
    }
    const res = await getMetroStationList(params)
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total) || 0
  } catch (e) {
    if (e?.response?.data?.message) errorMsg.value = e.response.data.message
  } finally { loading.value = false }
}

const fetchOptions = async () => {
  try {
    const r = await getCountryAll()
    countryOptions.value = normalizeList(r.data || [], ['id'])
  } catch {}
  try {
    const r = await getCityAll()
    allCityOptions.value = normalizeList(r.data || [], ['id', 'countryId'])
  } catch {}
}

const fetchLines = async () => {
  try {
    const res = await getMetroLineList({ pageSize: 9999 })
    allLineOptions.value = normalizeList(res.data?.records || [], ['id'])
  } catch { allLineOptions.value = [] }
}

const handleCurrentChange = (v) => { pageNum.value = v; fetchData() }
const handleSizeChange = (v) => { pageSize.value = v; pageNum.value = 1; fetchData() }
const handleSortChange = ({ prop, order }) => {
  if (!prop || !order) {
    sortField.value = 'createdAt'
    sortOrder.value = 'desc'
  } else if (prop === 'longitude' || prop === 'latitude') {
    sortField.value = 'longitude,latitude'
    sortOrder.value = order === 'ascending' ? 'asc,asc' : 'desc,desc'
  } else {
    sortField.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  }
  fetchData()
}
const handleSearch = () => { pageNum.value = 1; fetchData() }
const handleReset = () => {
  keyword.value = ''; filterCountryId.value = null; filterCityId.value = null
  filterIsTransfer.value = null; filterStatusCode.value = null; filterLineId.value = null; errorMsg.value = ''
  handleSearch()
}

const handleSelectionChange = (rows) => { selectedIds.value = rows.map(r => r.id) }

const initForm = () => {
  selectedLines.value = []
  prevSelectedStations.value = []
  nextSelectedStations.value = []
  candidateLineFilter.value = null
  Object.assign(form, {
    countryId: null, cityId: null, stationName: '', stationNameEn: '', stationAlias: '',
    longitude: '', latitude: '', isTransfer: 0, lineIds: '', lineNames: '',
    exitCount: 0, hasToilet: 0, stationType: 0,
    openDate: '', firstTime: '', lastTime: '',
    statusCode: 0, extra: '',
  })
}

const openCreateDialog = () => { isEdit.value = false; editId.value = null; dialogTitle.value = '新增地铁站'; initForm(); dialogVisible.value = true }
const buildSelectedFromRaw = (idsJson, namesJson, distsJson, allStations) => {
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

const openEditDialog = async (row) => {
  isEdit.value = true; editId.value = row.id; dialogTitle.value = '编辑地铁站'
  Object.assign(form, {
    countryId: ensureString(row.countryId), cityId: ensureString(row.cityId),
    stationName: row.stationName, stationNameEn: row.stationNameEn || '', stationAlias: row.stationAlias || '',
    longitude: row.longitude != null ? String(row.longitude) : '',
    latitude: row.latitude != null ? String(row.latitude) : '',
    isTransfer: row.isTransfer, lineIds: row.lineIds || '', lineNames: row.lineNames || '',
    exitCount: row.exitCount || 0, hasToilet: row.hasToilet || 0, stationType: row.stationType || 0,
    openDate: row.openDate || '', firstTime: row.firstTime || '', lastTime: row.lastTime || '',
    statusCode: row.statusCode != null ? row.statusCode : 0, extra: row.extra || '',
  })
  // 预选择已有线路
  await fetchLinesByCity(row.cityId)
  if (row.lineIds) {
    try {
      const ids = JSON.parse(row.lineIds)
      if (Array.isArray(ids)) {
        selectedLines.value = ids.map(String).filter(id => metroLineOptions.value.some(l => String(l.id) === id))
      }
    } catch {
      if (String(row.lineIds).trim()) {
        selectedLines.value = metroLineOptions.value
          .filter(l => String(row.lineIds).includes(String(l.id)))
          .map(l => l.id)
      }
    }
  }
  // 回填前序/后序站点数据
  candidateLineFilter.value = null
  prevSelectedStations.value = buildSelectedFromRaw(row.prevStationIds, row.prevStationNames, row.prevStationDistances, candidateStations.value)
  nextSelectedStations.value = buildSelectedFromRaw(row.nextStationIds, row.nextStationNames, row.nextStationDistances, candidateStations.value)
  dialogVisible.value = true
}

const buildLinePayload = () => {
  if (!selectedLines.value.length) return { lineIds: undefined, lineNames: undefined }
  const selected = metroLineOptions.value.filter(l => selectedLines.value.includes(l.id))
  return {
    lineIds: JSON.stringify(selected.map(l => l.id)),
    lineNames: JSON.stringify(selected.map(l => l.lineName)),
  }
}

const buildRawFields = (selected) => ({
  stationIds: JSON.stringify(selected.map(s => s.stationId)),
  stationNames: JSON.stringify(selected.map(s => s.stationName)),
  stationDistances: JSON.stringify(selected.map(s => {
    const d = parseFloat(s.distance)
    return isNaN(d) ? 0 : d
  })),
})

const checkLatLngWarnings = () => {
  const all = [...prevSelectedStations.value, ...nextSelectedStations.value]
  const missing = all.filter(s => {
    const station = candidateStations.value.find(c => c.id === s.stationId)
    return station && (!station.latitude || !station.longitude)
  }).map(s => s.stationName)
  latLngWarnings.value = [...new Set(missing)]
}

const recalculateDistances = (dir) => {
  const selfLat = parseFloat(form.latitude)
  const selfLng = parseFloat(form.longitude)
  if (isNaN(selfLat) || isNaN(selfLng)) {
    ElMessage.warning('请先填写当前站点的经纬度')
    return
  }
  const list = dir === 'prev' ? prevSelectedStations : nextSelectedStations
  list.value = list.value.map(s => {
    const station = candidateStations.value.find(c => c.id === s.stationId)
    if (station && station.latitude && station.longitude) {
      return { ...s, distance: haversineDistance(selfLat, selfLng, Number(station.latitude), Number(station.longitude)) }
    }
    return s
  })
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const linePayload = buildLinePayload()
    const prevRaw = prevSelectedStations.value.length > 0 ? buildRawFields(prevSelectedStations.value) : {}
    const nextRaw = nextSelectedStations.value.length > 0 ? buildRawFields(nextSelectedStations.value) : {}
    const payload = {
      countryId: form.countryId,
      cityId: form.cityId,
      stationName: form.stationName,
      stationNameEn: form.stationNameEn || undefined,
      stationAlias: form.stationAlias || undefined,
      longitude: parseFloat(form.longitude), latitude: parseFloat(form.latitude),
      isTransfer: form.isTransfer,
      ...linePayload,
      exitCount: form.exitCount, hasToilet: form.hasToilet, stationType: form.stationType,
      openDate: form.openDate || undefined, firstTime: form.firstTime || undefined, lastTime: form.lastTime || undefined,
      prevStationIds: prevRaw.stationIds || undefined,
      prevStationNames: prevRaw.stationNames || undefined,
      prevStationDistances: prevRaw.stationDistances || undefined,
      nextStationIds: nextRaw.stationIds || undefined,
      nextStationNames: nextRaw.stationNames || undefined,
      nextStationDistances: nextRaw.stationDistances || undefined,
      statusCode: form.statusCode, extra: form.extra || undefined,
    }
    if (isEdit.value) {
      await updateMetroStation(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createMetroStation(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false; fetchData()
  } catch (e) { /* handled */ }
  finally { submitting.value = false }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除站点「${row.stationName}」吗？`, '确认删除', { type: 'warning' })
    await deleteMetroStation(row.id)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) { if (e !== 'cancel') { /* */ } }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定删除 ${selectedIds.value.length} 个站点吗？`, '批量删除', { type: 'warning' })
    await batchDeleteMetroStations(selectedIds.value)
    ElMessage.success('批量删除完成')
    selectedIds.value = []
    fetchData()
  } catch (e) { if (e !== 'cancel') { /* */ } }
}

const exportSelected = () => {
  if (selectedIds.value.length === 0) return
  downloadExcel('/manage/metro-station/export', { ids: selectedIds.value.join(',') }, 'metro-stations.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

const exportAll = () => {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (filterCountryId.value) params.countryId = filterCountryId.value
  if (filterCityId.value) params.cityId = filterCityId.value
  if (filterIsTransfer.value != null) params.isTransfer = filterIsTransfer.value
  if (filterStatusCode.value != null) params.statusCode = filterStatusCode.value
  if (filterLineId.value) params.lineId = filterLineId.value
  downloadExcel('/manage/metro-station/export', params, 'metro-stations.xlsx')
    .catch(() => ElMessage.error('导出失败'))
}

const openImportDialog = () => { importDialogRef.value?.open() }

const goToDetail = (id) => { router.push(`/metro-station-detail/${id}`) }

onMounted(() => { fetchData(); fetchOptions(); fetchLines() })

watch(() => config.defaultPageSize, (val) => {
  if (!pageSizeInited.value && val !== pageSize.value) {
    pageSize.value = val
    fetchData()
  }
  pageSizeInited.value = true
})
</script>

<style scoped>
.manage-page { padding: 4px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
.page-header h2 { margin: 0; font-size: 20px; display: flex; align-items: center; gap: 8px; }
.header-actions { display: flex; gap: 8px; }
.search-bar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.pagination-bar { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; }
.station-name-link { cursor: pointer; color: #409EFF; }
.station-name-link:hover { text-decoration: underline; color: #337ECC; }
.line-opt-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
</style>
