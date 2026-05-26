<template>
  <!-- 权限不足提示 -->
  <div v-if="!canAccess" class="no-permission">
    <el-result icon="warning" title="权限不足" sub-title="地理编码功能仅超级管理员及以上可操作">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>

  <div v-else class="manage-page">
    <div class="page-header">
      <h2><el-icon><MapLocation /></el-icon> 地理编码控制台</h2>
      <div class="header-actions">
        <el-button type="primary" :icon="Position" @click="handleBatchAll" :loading="batchLoading">
          批量编码全部
        </el-button>
        <el-button type="success" :icon="Position" :disabled="selectedIds.length === 0" @click="handleBatchSelected" :loading="batchLoading">
          批量编码选中 ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ statusData.total || 0 }}</div>
        <div class="stat-label">站点总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card stat-success">
        <div class="stat-value">{{ statusData.geocoded || 0 }}</div>
        <div class="stat-label">已编码</div>
      </el-card>
      <el-card shadow="never" class="stat-card stat-warning">
        <div class="stat-value">{{ statusData.notGeocoded || 0 }}</div>
        <div class="stat-label">未编码</div>
      </el-card>
      <el-card shadow="never" class="stat-card stat-info">
        <div class="stat-value">{{ statusData.needsUpdate || 0 }}</div>
        <div class="stat-label">需更新(>7天)</div>
      </el-card>
    </div>

    <!-- 筛选栏 -->
    <div class="search-bar">
      <el-select v-model="filterCountryId" placeholder="按国家筛选" clearable style="width:160px" @change="onCountryChange">
        <el-option v-for="c in countryOptions" :key="c.id" :label="c.countryName" :value="c.id" />
      </el-select>

      <el-select v-model="filterCityId" placeholder="按城市筛选" clearable filterable style="width:180px" @change="onCityChange">
        <el-option v-for="c in filteredCityOptions" :key="c.id" :label="c.cityName" :value="c.id" />
      </el-select>

      <el-select v-model="filterLineId" placeholder="按线路筛选" clearable filterable style="width:180px" @change="handleFilterChange">
        <el-option v-for="line in lineOptions" :key="line.id" :label="line.lineName" :value="line.id">
          <span class="line-opt-dot" :style="{ background: line.lineColor || '#409EFF' }"></span>
          <span>{{ line.lineName }}</span>
        </el-option>
      </el-select>

      <el-select v-model="filterGeocodeStatus" placeholder="编码状态" clearable style="width:140px" @change="handleFilterChange">
        <el-option label="已编码" value="geocoded" />
        <el-option label="未编码" value="not_geocoded" />
        <el-option label="需更新" value="needs_update" />
      </el-select>

      <el-checkbox v-model="skipRecent" style="margin-left: 12px">
        跳过7天内已编码
      </el-checkbox>

      <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
    </div>

    <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" style="margin-bottom:12px" />

    <!-- 编码进度 -->
    <el-card v-if="batchProgress.visible" shadow="never" class="progress-card">
      <div class="progress-header">
        <span>批量编码进度</span>
        <el-button text @click="batchProgress.visible = false">关闭</el-button>
      </div>
      <el-progress
        :percentage="batchProgress.percentage"
        :status="batchProgress.status"
        :stroke-width="20"
        striped
        striped-flow
      />
      <div class="progress-detail">
        <span>总计: {{ batchProgress.total }}</span>
        <span class="text-success">成功: {{ batchProgress.encoded }}</span>
        <span class="text-warning">跳过: {{ batchProgress.skipped }}</span>
        <span class="text-danger">失败: {{ batchProgress.failed }}</span>
      </div>
      <div v-if="batchProgress.errors.length" class="progress-errors">
        <div v-for="(err, idx) in batchProgress.errors" :key="idx" class="error-item">
          <span class="error-name">{{ err.stationName }}</span>
          <span class="error-reason">{{ err.reason }}</span>
        </div>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-table
      ref="tableRef"
      :data="filteredData"
      v-loading="loading"
      border
      stripe
      style="width:100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column prop="stationName" label="站名" min-width="130" show-overflow-tooltip />
      <el-table-column prop="cityName" label="城市" width="90" />
      <el-table-column prop="longitude" label="经度" width="110" align="right">
        <template #default="{ row }">
          <span v-if="row.longitude" style="font-size:12px;font-family:monospace">{{ row.longitude }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="latitude" label="纬度" width="110" align="right">
        <template #default="{ row }">
          <span v-if="row.latitude" style="font-size:12px;font-family:monospace">{{ row.latitude }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="编码时间" width="160">
        <template #default="{ row }">
          <template v-if="row.geocodeTime">
            <div>{{ formatTime(row.geocodeTime) }}</div>
            <div class="time-ago">{{ timeAgo(row.geocodeTime) }}</div>
          </template>
          <span v-else class="text-muted">未编码</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="!row.geocodeTime" type="info" size="small">未编码</el-tag>
          <el-tag v-else-if="isRecent(row.geocodeTime)" type="success" size="small">已编码</el-tag>
          <el-tag v-else type="warning" size="small">需更新</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="Position" @click="handleSingleGeocode(row)" :loading="row._loading">
            编码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编码结果对话框 -->
    <el-dialog v-model="resultDialog.visible" title="地理编码结果" width="500px" :close-on-click-modal="false">
      <div v-if="resultDialog.data" class="result-dialog">
        <div class="result-header" :class="resultDialog.data.status === 'success' ? 'success' : 'failed'">
          <el-icon v-if="resultDialog.data.status === 'success'" :size="24"><SuccessFilled /></el-icon>
          <el-icon v-else :size="24"><CircleCloseFilled /></el-icon>
          <span>{{ resultDialog.data.status === 'success' ? '编码成功' : '编码失败' }}</span>
        </div>

        <div v-if="resultDialog.data.status === 'success'" class="result-body">
          <div class="result-item">
            <span class="result-label">站点名称</span>
            <span class="result-value">{{ resultDialog.data.stationName }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">查询地址</span>
            <span class="result-value">{{ resultDialog.data.formattedAddress || '-' }}</span>
          </div>

          <el-divider content-position="left">坐标变化</el-divider>

          <div class="result-comparison">
            <div class="result-col">
              <div class="result-col-header">修改前</div>
              <div class="result-coord">
                <span class="coord-label">经度</span>
                <span class="coord-value old">{{ resultDialog.data.oldLongitude || '-' }}</span>
              </div>
              <div class="result-coord">
                <span class="coord-label">纬度</span>
                <span class="coord-value old">{{ resultDialog.data.oldLatitude || '-' }}</span>
              </div>
            </div>
            <div class="result-arrow">
              <el-icon :size="20"><Right /></el-icon>
            </div>
            <div class="result-col">
              <div class="result-col-header">修改后</div>
              <div class="result-coord">
                <span class="coord-label">经度</span>
                <span class="coord-value new">{{ resultDialog.data.newLongitude }}</span>
              </div>
              <div class="result-coord">
                <span class="coord-label">纬度</span>
                <span class="coord-value new">{{ resultDialog.data.newLatitude }}</span>
              </div>
            </div>
          </div>

          <div v-if="resultDialog.data.distance" class="result-distance">
            <el-icon><InfoFilled /></el-icon>
            <span>坐标偏移距离: {{ resultDialog.data.distance }} 公里</span>
          </div>
        </div>

        <div v-else class="result-body failed-body">
          <el-alert :title="resultDialog.data.message" type="error" show-icon :closable="false" />
        </div>
      </div>
      <template #footer>
        <el-button @click="resultDialog.visible = false">关闭</el-button>
        <el-button v-if="resultDialog.data?.status === 'success'" type="primary" @click="resultDialog.visible = false">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { MapLocation, Position, RefreshRight, SuccessFilled, CircleCloseFilled, Right, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMetroStationList } from '@/api/metroStation'
import { getMetroLineList } from '@/api/metroLine'
import { geocodeSingle, geocodeBatch, getGeocodeStatus } from '@/api/geocode'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { usePermission } from '@/composables/usePermission'
import { normalizeList } from '@/utils/normalize'

const { canEditAllFields } = usePermission()
const canAccess = computed(() => canEditAllFields.value)

const loading = ref(false)
const batchLoading = ref(false)
const tableData = ref([])
const selectedIds = ref([])
const errorMsg = ref('')
const skipRecent = ref(true)

// 筛选
const filterCountryId = ref(null)
const filterCityId = ref(null)
const filterLineId = ref(null)
const filterGeocodeStatus = ref('')

// 下拉选项
const countryOptions = ref([])
const allCityOptions = ref([])
const lineOptions = ref([])

const filteredCityOptions = computed(() => {
  if (!filterCountryId.value) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === filterCountryId.value)
})

// 统计
const statusData = reactive({
  total: 0,
  geocoded: 0,
  notGeocoded: 0,
  needsUpdate: 0,
})

// 编码结果对话框
const resultDialog = reactive({
  visible: false,
  data: null,
})

// 批量进度
const batchProgress = reactive({
  visible: false,
  percentage: 0,
  status: '',
  total: 0,
  encoded: 0,
  skipped: 0,
  failed: 0,
  errors: [],
})

// 过滤后的数据
const filteredData = computed(() => {
  let data = tableData.value
  if (filterGeocodeStatus.value === 'geocoded') {
    data = data.filter(s => s.geocodeTime && isRecent(s.geocodeTime))
  } else if (filterGeocodeStatus.value === 'not_geocoded') {
    data = data.filter(s => !s.geocodeTime)
  } else if (filterGeocodeStatus.value === 'needs_update') {
    data = data.filter(s => s.geocodeTime && !isRecent(s.geocodeTime))
  }
  return data
})

function isRecent(timeStr) {
  if (!timeStr) return false
  const t = new Date(timeStr)
  const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  return t.getTime() > weekAgo
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const d = new Date(timeStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function timeAgo(timeStr) {
  if (!timeStr) return ''
  const now = Date.now()
  const t = new Date(timeStr).getTime()
  const diff = now - t
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  const months = Math.floor(days / 30)
  return `${months}个月前`
}

async function fetchFilterOptions() {
  try {
    const r = await getCountryAll()
    countryOptions.value = normalizeList(r.data || [], ['id'])
  } catch {}
  try {
    const r = await getCityAll()
    allCityOptions.value = normalizeList(r.data || [], ['id', 'countryId'])
  } catch {}
}

async function fetchLines() {
  try {
    const params = {}
    if (filterCityId.value) params.cityId = filterCityId.value
    const res = await getMetroLineList(params)
    lineOptions.value = res.data?.records || res.data || []
  } catch (e) {
    // ignore
  }
}

async function fetchStations() {
  loading.value = true
  errorMsg.value = ''
  try {
    const params = { pageNum: 1, pageSize: 9999 }
    if (filterCityId.value) params.cityId = filterCityId.value
    const res = await getMetroStationList(params)
    let records = res.data?.records || res.data || []

    // 如果按线路筛选，需要过滤
    if (filterLineId.value) {
      records = records.filter(s => {
        if (!s.lineIds) return false
        try {
          const ids = JSON.parse(s.lineIds)
          return ids.includes(filterLineId.value)
        } catch {
          return false
        }
      })
    }

    tableData.value = records
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function fetchStatus() {
  try {
    const res = await getGeocodeStatus(filterCityId.value || undefined)
    Object.assign(statusData, res.data)
  } catch (e) {
    // ignore
  }
}

function handleFilterChange() {
  fetchStations()
  fetchStatus()
  if (filterCityId.value) {
    fetchLines()
  }
}

function onCountryChange() {
  filterCityId.value = null
  filterLineId.value = null
  lineOptions.value = []
  fetchStations()
  fetchStatus()
}

function onCityChange() {
  filterLineId.value = null
  fetchStations()
  fetchStatus()
  if (filterCityId.value) {
    fetchLines()
  } else {
    lineOptions.value = []
  }
}

function handleReset() {
  filterCountryId.value = null
  filterCityId.value = null
  filterLineId.value = null
  filterGeocodeStatus.value = ''
  lineOptions.value = []
  fetchStations()
  fetchStatus()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(s => s.id)
}

async function handleSingleGeocode(row) {
  row._loading = true
  try {
    const res = await geocodeSingle(row.id)
    const data = res.data

    // 计算偏移距离（如果有旧坐标）
    if (data.status === 'success' && data.oldLongitude && data.oldLatitude) {
      const dist = haversineDistance(
        Number(data.oldLatitude), Number(data.oldLongitude),
        Number(data.newLatitude), Number(data.newLongitude)
      )
      data.distance = dist.toFixed(3)
    }

    resultDialog.data = data
    resultDialog.visible = true

    if (data.status === 'success') {
      fetchStations()
      fetchStatus()
    }
  } catch (e) {
    ElMessage.error(`编码失败: ${e.message || '未知错误'}`)
  } finally {
    row._loading = false
  }
}

// Haversine 公式计算两点间距离（km）
function haversineDistance(lat1, lng1, lat2, lng2) {
  const R = 6371
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLng = (lng2 - lng1) * Math.PI / 180
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

async function handleBatchAll() {
  await ElMessageBox.confirm(
    `将对全部站点进行地理编码${skipRecent.value ? '（跳过7天内已编码）' : ''}，确定继续？`,
    '确认批量编码',
    { confirmButtonText: '开始编码', cancelButtonText: '取消', type: 'warning' }
  )
  doBatch(null)
}

async function handleBatchSelected() {
  await ElMessageBox.confirm(
    `将对选中的 ${selectedIds.value.length} 个站点进行地理编码，确定继续？`,
    '确认批量编码',
    { confirmButtonText: '开始编码', cancelButtonText: '取消', type: 'warning' }
  )
  doBatch(selectedIds.value)
}

async function doBatch(stationIds) {
  batchLoading.value = true
  batchProgress.visible = true
  batchProgress.percentage = 0
  batchProgress.status = ''
  batchProgress.total = 0
  batchProgress.encoded = 0
  batchProgress.skipped = 0
  batchProgress.failed = 0
  batchProgress.errors = []

  try {
    const data = {
      skipRecent: skipRecent.value,
    }
    if (stationIds) {
      data.stationIds = stationIds.map(String)
    } else if (filterCityId.value) {
      data.cityId = filterCityId.value
    }

    const res = await geocodeBatch(data)
    const result = res.data
    batchProgress.total = result.total
    batchProgress.encoded = result.encoded
    batchProgress.skipped = result.skipped
    batchProgress.failed = result.failed
    batchProgress.errors = result.errors || []
    batchProgress.percentage = 100
    batchProgress.status = result.failed > 0 ? 'warning' : 'success'

    // 显示详细结果
    if (result.details && result.details.length > 0) {
      const successList = result.details.map(d =>
        `${d.stationName}: (${d.oldLongitude || '-'}, ${d.oldLatitude || '-'}) → (${d.newLongitude}, ${d.newLatitude})`
      ).join('\n')

      ElMessageBox.alert(
        `<div style="max-height:400px;overflow-y:auto;">
          <p><b>编码成功 ${result.encoded} 个站点</b></p>
          <pre style="font-size:12px;background:#f5f7fa;padding:10px;border-radius:6px;white-space:pre-wrap;">${successList}</pre>
          ${result.errors.length > 0 ? `<p style="color:#f56c6c;margin-top:10px;"><b>失败 ${result.failed} 个:</b></p><pre style="font-size:12px;background:#fef0f0;padding:10px;border-radius:6px;">${result.errors.map(e => `${e.stationName}: ${e.reason}`).join('\n')}</pre>` : ''}
        </div>`,
        '批量编码结果',
        { dangerouslyUseHTMLString: true, confirmButtonText: '确定' }
      )
    }

    ElMessage.success(`编码完成: 成功${result.encoded}, 跳过${result.skipped}, 失败${result.failed}`)
    fetchStations()
    fetchStatus()
  } catch (e) {
    batchProgress.status = 'exception'
    ElMessage.error('批量编码失败: ' + (e.message || '未知错误'))
  } finally {
    batchLoading.value = false
  }
}

onMounted(() => {
  fetchFilterOptions()
  fetchStations()
  fetchStatus()
})
</script>

<style scoped>
.manage-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 20px;
}
.header-actions {
  display: flex;
  gap: 10px;
}
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  flex: 1;
  text-align: center;
  border-radius: 10px;
}
.stat-card :deep(.el-card__body) {
  padding: 16px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-card.stat-success .stat-value {
  color: #67c23a;
}
.stat-card.stat-warning .stat-value {
  color: #e6a23c;
}
.stat-card.stat-info .stat-value {
  color: #909399;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.line-opt-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}
.progress-card {
  margin-bottom: 16px;
  border-radius: 10px;
}
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}
.progress-detail {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}
.progress-errors {
  margin-top: 10px;
  max-height: 120px;
  overflow-y: auto;
}
.error-item {
  display: flex;
  gap: 12px;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}
.error-name {
  font-weight: 600;
  color: #303133;
  min-width: 80px;
}
.error-reason {
  color: #f56c6c;
}
.text-muted {
  color: #c0c4cc;
}
.text-success {
  color: #67c23a;
}
.text-warning {
  color: #e6a23c;
}
.text-danger {
  color: #f56c6c;
}
.time-ago {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

/* 编码结果对话框 */
.result-dialog {
  padding: 0 10px;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
  padding: 15px;
  border-radius: 8px;
}
.result-header.success {
  background: #f0f9ff;
  color: #67c23a;
}
.result-header.failed {
  background: #fef0f0;
  color: #f56c6c;
}
.result-body {
  margin-top: 10px;
}
.result-item {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.result-label {
  width: 80px;
  color: #909399;
  font-size: 13px;
}
.result-value {
  flex: 1;
  color: #303133;
  font-size: 14px;
}
.result-comparison {
  display: flex;
  align-items: center;
  gap: 15px;
  margin: 15px 0;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
}
.result-col {
  flex: 1;
}
.result-col-header {
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  font-size: 14px;
}
.result-coord {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.coord-label {
  width: 40px;
  color: #909399;
  font-size: 12px;
}
.coord-value {
  font-family: monospace;
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 4px;
}
.coord-value.old {
  background: #fef0f0;
  color: #f56c6c;
}
.coord-value.new {
  background: #f0f9ff;
  color: #409eff;
}
.result-arrow {
  color: #909399;
}
.result-distance {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 15px;
  padding: 10px;
  background: #fdf6ec;
  border-radius: 6px;
  color: #e6a23c;
  font-size: 13px;
}
.failed-body {
  padding: 10px 0;
}
</style>
