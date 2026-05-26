<template>
  <!-- 权限不足提示 -->
  <div v-if="!canAccess" class="no-permission">
    <el-result icon="warning" title="权限不足" sub-title="站点排序功能仅超级管理员及以上可操作">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>

  <div v-else class="station-reorder" v-loading="loading">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      <span class="page-title">站点排序 — {{ line.lineName || '选择线路' }}</span>
      <div class="header-actions">
        <el-button
          type="primary"
          :icon="Sort"
          :disabled="!stations.length"
          :loading="saving"
          @click="handleSave"
        >保存排序</el-button>
      </div>
    </div>

    <!-- 线路选择（国家 → 城市 → 线路 级联筛选） -->
    <el-card shadow="never" class="select-card" v-if="!route.params.lineId">
      <div class="select-row">
        <el-select
          v-model="filterCountryId"
          placeholder="国家"
          clearable
          style="width: 160px"
          @change="onCountryChange"
        >
          <el-option v-for="c in countryOptions" :key="c.id" :value="c.id" :label="c.countryName" />
        </el-select>
        <el-select
          v-model="filterCityId"
          placeholder="城市"
          clearable
          filterable
          style="width: 180px"
          @change="onCityChange"
        >
          <el-option v-for="c in filteredCityOptions" :key="c.id" :value="c.id" :label="c.cityName" />
        </el-select>
        <el-select
          v-model="selectedLineId"
          placeholder="地铁线路"
          filterable
          style="width: 280px"
          @change="onLineChange"
        >
          <el-option
            v-for="l in filteredLineOptions" :key="l.id"
            :value="l.id"
            :label="`${l.lineName} (${l.lineNo})`"
          />
        </el-select>
      </div>
    </el-card>

    <template v-if="stations.length">
      <!-- 操作提示 -->
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          拖拽站点卡片调整顺序，距离会自动计算。换乘站的其他线路数据不会被修改。
        </template>
      </el-alert>

      <!-- 站点统计 -->
      <div class="stats-bar">
        <span class="stats-item">
          <span class="stats-num">{{ stations.length }}</span> 个站点
        </span>
        <span class="stats-item">
          <span class="stats-num">{{ transferCount }}</span> 个换乘站
        </span>
        <span class="stats-item" v-if="totalDistance > 0">
          总里程 <span class="stats-num">{{ totalDistance.toFixed(2) }}</span> km
        </span>
        <el-button size="small" @click="recalcAllDistances" :icon="RefreshRight" style="margin-left: auto">
          重新计算距离
        </el-button>
      </div>

      <!-- 拖拽列表 -->
      <draggable
        v-model="stations"
        item-key="id"
        handle=".drag-handle"
        animation="200"
        ghost-class="station-ghost"
        drag-class="station-drag"
        @end="onDragEnd"
      >
        <template #item="{ element, index }">
          <div class="station-card" :class="{ transfer: element.isTransfer === 1 }">
            <div class="drag-handle">
              <svg viewBox="0 0 24 24" width="20" height="20">
                <path d="M3 15h18v-2H3v2zm0 4h18v-2H3v2zm0-8h18V9H3v2zm0-6v2h18V5H3z" fill="#c0c4cc"/>
              </svg>
            </div>
            <input
              class="station-index-input"
              type="text"
              inputmode="numeric"
              :value="index + 1"
              @keydown="onIndexKeydown"
              @keyup.enter="onIndexSubmit($event, index)"
              @blur="onIndexSubmit($event, index)"
              @focus="onIndexFocus($event)"
            />
            <div class="station-info">
              <div class="station-name">
                {{ element.stationName }}
                <el-tag v-if="element.isTransfer === 1" size="small" type="warning" effect="plain" class="transfer-tag">换乘</el-tag>
              </div>
              <div class="station-name-en" v-if="element.stationNameEn">{{ element.stationNameEn }}</div>
            </div>
            <div class="station-distance">
              <template v-if="index > 0">
                <span class="distance-label">距上一站</span>
                <el-input-number
                  v-model="element._distance"
                  :min="0"
                  :max="999"
                  :precision="3"
                  :step="0.1"
                  size="small"
                  controls-position="right"
                  style="width: 120px"
                />
                <span class="distance-unit">km</span>
              </template>
              <template v-else>
                <el-tag size="small" effect="plain" type="info">起点站</el-tag>
              </template>
            </div>
            <div class="station-lines" v-if="element._lineNames && element._lineNames.length > 1">
              <el-tag
                v-for="ln in element._lineNames" :key="ln"
                size="small"
                :type="ln === line.lineName ? '' : 'info'"
                effect="plain"
                class="line-tag"
              >{{ ln }}</el-tag>
            </div>
          </div>
        </template>
      </draggable>
    </template>

    <el-empty v-else-if="!loading && selectedLineId" description="该线路暂无站点数据" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePermission } from '@/composables/usePermission'

const { canEditAllFields } = usePermission()
const canAccess = computed(() => canEditAllFields.value)
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Sort, RefreshRight } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { getMetroLineList, getMetroLineDetail, getLineOrderedStations } from '@/api/metroLine'
import { updateMetroStation } from '@/api/metroStation'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { haversineDistance } from '@/utils/geo'
import { normalizeList } from '@/utils/normalize'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const line = ref({})
const stations = ref([])
const lineOptions = ref([])
const selectedLineId = ref(null)

// ── 国家/城市级联筛选 ──
const filterCountryId = ref(null)
const filterCityId = ref(null)
const countryOptions = ref([])
const allCityOptions = ref([])

const filteredCityOptions = computed(() => {
  if (!filterCountryId.value) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === filterCountryId.value)
})

const filteredLineOptions = computed(() => {
  if (!filterCityId.value) return lineOptions.value
  return lineOptions.value.filter(l => String(l.cityId) === String(filterCityId.value))
})

function onCountryChange() {
  filterCityId.value = null
  selectedLineId.value = null
  stations.value = []
  line.value = {}
}

function onCityChange() {
  selectedLineId.value = null
  stations.value = []
  line.value = {}
  if (filterCityId.value) fetchLineOptions()
}

const transferCount = computed(() => stations.value.filter(s => s.isTransfer === 1).length)
const totalDistance = computed(() => stations.value.reduce((sum, s) => sum + (s._distance || 0), 0))

function goBack() {
  if (route.params.lineId) {
    router.push(`/metro-line-detail/${route.params.lineId}`)
  } else {
    router.push('/metro-lines')
  }
}

// ── 获取线路列表 ──
async function fetchLineOptions() {
  try {
    const params = { pageSize: 999 }
    if (filterCityId.value) params.cityId = filterCityId.value
    const res = await getMetroLineList(params)
    lineOptions.value = res.data?.records || []
  } catch { lineOptions.value = [] }
}

// ── 获取国家/城市选项 ──
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

// ── 加载线路和站点 ──
async function loadLineData(lineId) {
  if (!lineId) return
  loading.value = true
  try {
    const [lineRes, stationsRes] = await Promise.all([
      getMetroLineDetail(lineId),
      getLineOrderedStations(lineId),
    ])
    line.value = lineRes.data || {}
    const ordered = stationsRes.data?.stations || []
    stations.value = ordered.map((s, i) => ({
      ...s,
      _id: String(s.id),
      _distance: 0,
      _lineNames: parseLineNames(s.lineNames),
    }))
    // 计算初始距离
    recalcAllDistances()
    nextTick(() => syncIndexInputs())
  } catch (e) {
    console.error('loadLineData error:', e)
    stations.value = []
  } finally {
    loading.value = false
  }
}

function parseLineNames(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch { return [raw] }
}

function onLineChange(lineId) {
  loadLineData(lineId)
}

// ── 拖拽结束后重新计算距离 ──
function onDragEnd() {
  recalcAllDistances()
  nextTick(() => syncIndexInputs())
}

// ── 同步所有序号输入框的显示值 ──
function syncIndexInputs() {
  const inputs = document.querySelectorAll('.station-index-input')
  inputs.forEach((input, i) => { input.value = i + 1 })
}

// ── 序号输入：聚焦时选中文本 ──
function onIndexFocus(e) {
  // 用 setTimeout 确保 select 在 focus 事件之后执行
  setTimeout(() => e.target.select(), 0)
}

// ── 序号输入：拦截非法按键 ──
function onIndexKeydown(e) {
  // 允许：数字、退格、删除、Tab、回车、方向键、Home、End、Esc
  const allow = [
    'Backspace', 'Delete', 'Tab', 'Enter', 'Escape',
    'ArrowLeft', 'ArrowRight', 'Home', 'End',
  ]
  if (allow.includes(e.key)) return
  if (e.ctrlKey || e.metaKey) return // Ctrl+A/C/V/X
  // 拦截非数字
  if (!/^\d$/.test(e.key)) {
    e.preventDefault()
  }
}

// ── 序号输入：提交验证并移动站点 ──
function onIndexSubmit(event, currentIndex) {
  const raw = event.target.value.trim()
  const total = stations.value.length

  // 空值或非纯数字 → 恢复原位
  if (!raw || !/^\d+$/.test(raw)) {
    event.target.value = currentIndex + 1
    return
  }

  const newVal = parseInt(raw, 10)

  // 范围校验
  if (newVal < 1 || newVal > total) {
    ElMessage.warning(`序号范围：1 ~ ${total}`)
    event.target.value = currentIndex + 1
    return
  }

  const newIndex = newVal - 1
  if (newIndex === currentIndex) {
    event.target.value = currentIndex + 1
    return
  }

  // 移动站点：取出 → 插入目标位置，其余站点自动顺移
  const item = stations.value.splice(currentIndex, 1)[0]
  stations.value.splice(newIndex, 0, item)
  recalcAllDistances()

  // DOM 更新后刷新所有输入框显示值
  nextTick(() => syncIndexInputs())
}

function recalcAllDistances() {
  for (let i = 0; i < stations.value.length; i++) {
    if (i === 0) {
      stations.value[i]._distance = 0
      continue
    }
    const prev = stations.value[i - 1]
    const curr = stations.value[i]
    if (prev.latitude != null && prev.longitude != null && curr.latitude != null && curr.longitude != null) {
      stations.value[i]._distance = haversineDistance(
        Number(prev.latitude), Number(prev.longitude),
        Number(curr.latitude), Number(curr.longitude)
      )
    }
  }
}

// ── 保存排序 ──
async function handleSave() {
  const order = stations.value
  if (order.length < 2) {
    ElMessage.warning('至少需要2个站点')
    return
  }

  // 校验距离值
  for (let i = 1; i < order.length; i++) {
    const d = order[i]._distance
    if (d == null || isNaN(d) || d < 0) {
      ElMessage.warning(`第 ${i + 1} 站「${order[i].stationName}」的距离值无效，请检查`)
      return
    }
  }

  await ElMessageBox.confirm(
    `将更新 ${order.length} 个站点的前后站关系和距离，确定保存？`,
    '保存排序',
    { confirmButtonText: '确定保存', cancelButtonText: '取消', type: 'warning' }
  )

  saving.value = true
  let successCount = 0
  let errorCount = 0

  try {
    // 逐个更新站点
    for (let i = 0; i < order.length; i++) {
      const station = order[i]
      const updateData = buildStationUpdate(order, i)
      if (!updateData) continue // 无需更新

      try {
        await updateMetroStation(station.id, updateData)
        successCount++
      } catch (e) {
        console.error(`更新站点 ${station.stationName} 失败:`, e)
        errorCount++
      }
    }

    if (errorCount === 0) {
      ElMessage.success(`排序保存成功，已更新 ${successCount} 个站点`)
      // 重新加载数据
      await loadLineData(route.params.lineId || selectedLineId.value)
    } else {
      ElMessage.warning(`部分更新失败：成功 ${successCount}，失败 ${errorCount}`)
    }
  } finally {
    saving.value = false
  }
}

/**
 * 构建单个站点的更新数据
 * 关键：只修改当前线路在 lineIds 中对应索引的 prev/next，保留其他线路数据
 */
function buildStationUpdate(order, index) {
  const station = order[index]
  const lineId = String(line.value.id)

  // 解析当前站点的 lineIds，找到当前线路的索引
  let lineIds = parseJsonArray(station.lineIds)
  if (!lineIds.length) {
    // 如果没有 lineIds，尝试从 lineNames 推断
    lineIds = [lineId]
  }
  let lineIndex = lineIds.indexOf(lineId)
  if (lineIndex === -1) {
    // 当前线路不在 lineIds 中，追加到末尾
    lineIndex = lineIds.length
    lineIds.push(lineId)
  }

  // 当前线路的前后站
  const isFirst = index === 0
  const isLast = index === order.length - 1
  const prevStation = isFirst ? null : order[index - 1]
  const nextStation = isLast ? null : order[index + 1]

  // 解析现有的 prev/next 数据
  let prevIds = parseJsonArray(station.prevStationIds)
  let prevNames = parseJsonArray(station.prevStationNames)
  let prevDists = parseJsonNumArray(station.prevStationDistances)
  let nextIds = parseJsonArray(station.nextStationIds)
  let nextNames = parseJsonArray(station.nextStationNames)
  let nextDists = parseJsonNumArray(station.nextStationDistances)

  // 扩展数组到 lineIndex 位置（如果需要）
  while (prevIds.length <= lineIndex) prevIds.push('')
  while (prevNames.length <= lineIndex) prevNames.push('')
  while (prevDists.length <= lineIndex) prevDists.push(0)
  while (nextIds.length <= lineIndex) nextIds.push('')
  while (nextNames.length <= lineIndex) nextNames.push('')
  while (nextDists.length <= lineIndex) nextDists.push(0)

  // 更新当前线路索引位置的数据
  prevIds[lineIndex] = prevStation ? String(prevStation.id) : ''
  prevNames[lineIndex] = prevStation ? (prevStation.stationName || '') : ''
  prevDists[lineIndex] = (prevStation && station._distance) ? station._distance : 0

  nextIds[lineIndex] = nextStation ? String(nextStation.id) : ''
  nextNames[lineIndex] = nextStation ? (nextStation.stationName || '') : ''
  // nextDistance 是下一站到当前站的距离（即下一站的 _distance）
  nextDists[lineIndex] = nextStation ? (nextStation._distance || 0) : 0

  // 同步更新 lineIds（如果之前没有当前线路）
  const finalLineIds = [...lineIds]
  if (finalLineIds.length <= lineIndex) finalLineIds.push(lineId)

  return {
    lineIds: JSON.stringify(finalLineIds),
    prevStationIds: JSON.stringify(prevIds),
    prevStationNames: JSON.stringify(prevNames),
    prevStationDistances: JSON.stringify(prevDists),
    nextStationIds: JSON.stringify(nextIds),
    nextStationNames: JSON.stringify(nextNames),
    nextStationDistances: JSON.stringify(nextDists),
  }
}

function parseJsonArray(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return [...raw]
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? [...arr] : []
  } catch {
    return []
  }
}

function parseJsonNumArray(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(Number)
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.map(Number) : []
  } catch {
    return []
  }
}

// ── 生命周期 ──
onMounted(() => {
  fetchFilterOptions()
  if (route.params.lineId) {
    selectedLineId.value = Number(route.params.lineId)
    loadLineData(route.params.lineId)
  } else {
    fetchLineOptions()
  }
})

watch(() => route.params.lineId, (newId) => {
  if (newId) {
    selectedLineId.value = Number(newId)
    loadLineData(newId)
  }
})
</script>

<style scoped>
.station-reorder {
  background: var(--bg-primary, #fff);
  min-height: 100vh;
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.header-actions { display: flex; gap: 8px; }

.select-card { margin-bottom: 20px; border-radius: 12px; }
.select-row { display: flex; align-items: center; gap: 12px; }
.select-label { font-size: 14px; color: #606266; white-space: nowrap; }

/* ── 统计栏 ── */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
  padding: 12px 20px;
  background: #f8f9fb;
  border-radius: 10px;
  border: 1px solid #ebeef5;
}
.stats-item { font-size: 13px; color: #606266; }
.stats-num { font-size: 18px; font-weight: 700; color: #409EFF; margin-right: 2px; }

/* ── 站点卡片 ── */
.station-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: all 0.2s;
}
.station-card:hover {
  border-color: #b3d8ff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.08);
}
.station-card.transfer {
  border-left: 3px solid #e6a23c;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: 4px;
  transition: background 0.2s;
}
.drag-handle:hover { background: #f5f7fa; }
.drag-handle:active { cursor: grabbing; }

.station-index-input {
  width: 44px;
  height: 30px;
  border-radius: 15px;
  background: #f0f2f5;
  border: 1px solid transparent;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
  color: #909399;
  padding: 0;
  transition: all 0.2s;
  outline: none;
  flex-shrink: 0;
}
.station-index-input:hover {
  border-color: #c0c4cc;
}
.station-index-input:focus {
  background: #fff;
  border-color: #409EFF;
  color: #303133;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}
.station-card.transfer .station-index-input {
  background: #fdf6ec;
  color: #e6a23c;
}
.station-card.transfer .station-index-input:focus {
  background: #fff;
  color: #303133;
}

.station-info {
  flex: 1;
  min-width: 0;
}
.station-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.station-name-en {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.transfer-tag { margin-left: 4px; }

.station-distance {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.distance-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.distance-unit {
  font-size: 12px;
  color: #909399;
}

.station-lines {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.line-tag { font-size: 11px; }

/* ── 拖拽动画 ── */
.station-ghost {
  opacity: 0.4;
  background: #ecf5ff !important;
  border: 2px dashed #409EFF !important;
}
.station-drag {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12) !important;
  transform: rotate(1deg);
}

@media (max-width: 768px) {
  .station-card { flex-wrap: wrap; gap: 8px; }
  .station-distance { width: 100%; margin-left: 40px; }
  .station-lines { width: 100%; margin-left: 40px; }
}
</style>
