<template>
  <div class="station-line-assign" v-loading="loading">
    <!-- 页头 -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="$router.back()">返回</el-button>
      <span class="page-title">站点线路分配</span>
      <div class="header-actions">
        <el-button :icon="RefreshRight" @click="initBoard">刷新</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" :disabled="!hasChanges" @click="handleSave">
          保存变更
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="filterCountryId" placeholder="按国家筛选" clearable style="width:180px">
          <el-option v-for="c in countryOptions" :key="c.id" :label="c.countryName" :value="c.id" />
        </el-select>
        <el-select v-model="filterCityId" placeholder="按城市筛选（必选）" clearable filterable style="width:200px" @change="onCityChange">
          <el-option v-for="c in filteredCityOptions" :key="c.id" :label="c.cityName" :value="c.id" />
        </el-select>
        <el-tag type="info" effect="plain" size="small" v-if="!filterCityId">请先选择城市以加载站点数据</el-tag>
      </div>
    </el-card>

    <!-- 看板主体 -->
    <div class="kanban-wrapper" v-if="filterCityId">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px">
        <template #title>
          拖拽站点卡片到目标线路列即可分配。从线路列拖回"待分配"可移除线路归属。变更需点击"保存变更"才会提交。未开通站点和已正确分配的站点不会显示。
        </template>
      </el-alert>

      <!-- 数据统计 -->
      <div class="stats-bar" v-if="loadStats.total > 0">
        <span class="stats-item">加载 <b>{{ loadStats.total }}</b> 个站点</span>
        <span class="stats-item" v-if="loadStats.filtered > 0">
          已过滤未开通 <b>{{ loadStats.filtered }}</b> 个
        </span>
        <span class="stats-item">待分配 <b>{{ poolStations.length }}</b> 个</span>
        <span class="stats-item">已分配 <b>{{ assignedCount }}</b> 个</span>
      </div>

      <div class="kanban-scroll">
        <div class="kanban-board">
          <!-- 待分配列 -->
          <div class="kanban-column pool-column">
            <div class="column-header pool-header">
              <div class="column-title">
                <el-icon><QuestionFilled /></el-icon>
                <span>待分配</span>
              </div>
              <div class="header-right-area">
                <el-dropdown trigger="click" @command="handlePoolSort">
                  <el-icon class="sort-btn" title="排序"><Sort /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="name_asc" :class="{ active: poolSortBy === 'name_asc' }">站名 A-Z</el-dropdown-item>
                      <el-dropdown-item command="lng_asc" :class="{ active: poolSortBy === 'lng_asc' }">经度 升序</el-dropdown-item>
                      <el-dropdown-item command="lng_desc" :class="{ active: poolSortBy === 'lng_desc' }">经度 降序</el-dropdown-item>
                      <el-dropdown-item command="lat_asc" :class="{ active: poolSortBy === 'lat_asc' }">纬度 升序</el-dropdown-item>
                      <el-dropdown-item command="lat_desc" :class="{ active: poolSortBy === 'lat_desc' }">纬度 降序</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-badge :value="poolStations.length" type="info" />
              </div>
            </div>
            <draggable
              :list="poolStations"
              :group="{ name: 'stations', pull: true, put: true }"
              item-key="id"
              :animation="200"
              ghost-class="card-ghost"
              drag-class="card-drag"
              class="column-body"
              @add="onPoolAdd"
            >
              <template #item="{ element }">
                <div class="station-card">
                  <div class="card-name">{{ element.stationName }}</div>
                  <div class="card-en" v-if="element.stationNameEn">{{ element.stationNameEn }}</div>
                  <div class="card-coord" v-if="element.longitude != null && element.latitude != null">
                    {{ Number(element.longitude).toFixed(5) }}, {{ Number(element.latitude).toFixed(5) }}
                  </div>
                </div>
              </template>
              <template #footer>
                <div v-if="poolStations.length === 0" class="empty-hint">
                  <el-icon :size="20"><CircleCheck /></el-icon>
                  <span>所有站点已分配</span>
                </div>
              </template>
            </draggable>
          </div>

          <!-- 线路列 -->
          <div
            v-for="line in metroLines"
            :key="line.id"
            class="kanban-column line-column"
            :data-line-id="line.id"
          >
            <div class="column-header" :style="{ background: line.lineColor || '#409EFF', color: '#fff' }">
              <div class="column-title">
                <el-icon><Guide /></el-icon>
                <span>{{ line.lineName }}</span>
                <span class="line-no" v-if="line.lineNo">{{ line.lineNo }}</span>
              </div>
              <el-badge :value="(lineBoard[line.id] || []).length" style="--el-badge-bg-color: rgba(255,255,255,0.3)" />
            </div>
            <draggable
              :list="lineBoard[line.id]"
              :group="{ name: 'stations', pull: true, put: true }"
              item-key="id"
              :animation="200"
              ghost-class="stacked-ghost"
              drag-class="stacked-drag"
              class="stacked-body"
              :style="{ '--line-color': line.lineColor || '#409EFF' }"
            >
              <template #item="{ element, index }">
                <div
                  class="stacked-card"
                  :style="{ '--i': index, '--total': (lineBoard[line.id] || []).length }"
                  :title="element.stationName + (element.stationNameEn ? ' (' + element.stationNameEn + ')' : '')"
                >
                  <span class="stacked-name">{{ element.stationName }}</span>
                </div>
              </template>
              <template #footer>
                <div v-if="(lineBoard[line.id] || []).length === 0" class="empty-hint">
                  <el-icon :size="20"><Plus /></el-icon>
                  <span>拖入站点</span>
                </div>
              </template>
            </draggable>
          </div>
        </div>
      </div>
    </div>

    <!-- 未选城市的空状态 -->
    <el-empty v-else-if="!loading" description="请先选择城市以加载站点和线路数据" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, RefreshRight, QuestionFilled, Guide, Plus, CircleCheck, Sort } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import { getStationsByCityId, batchAssignLine, batchRemoveLine } from '@/api/metroStation'
import { getMetroLineList } from '@/api/metroLine'
import { getCountryAll } from '@/api/country'
import { getCityAll } from '@/api/city'
import { normalizeList } from '@/utils/normalize'

const loading = ref(false)
const saving = ref(false)

// ── 筛选 ──
const filterCountryId = ref(null)
const filterCityId = ref(null)
const countryOptions = ref([])
const allCityOptions = ref([])
const metroLines = ref([])
const poolSortBy = ref('name_asc')

const filteredCityOptions = computed(() => {
  if (!filterCountryId.value) return allCityOptions.value
  return allCityOptions.value.filter(c => c.countryId === filterCountryId.value)
})

// ── 看板数据 ──
const poolStations = ref([])
const lineBoard = reactive({})
let initialLineBoard = {}
const loadStats = reactive({ total: 0, filtered: 0 })

const assignedCount = computed(() => {
  let count = 0
  metroLines.value.forEach(line => {
    count += (lineBoard[line.id] || []).length
  })
  return count
})

// ── 计算属性 ──
const stationMap = computed(() => {
  const map = {}
  poolStations.value.forEach(s => { map[s.id] = s })
  metroLines.value.forEach(line => {
    (lineBoard[line.id] || []).forEach(s => { map[s.id] = s })
  })
  return map
})

const hasChanges = computed(() => {
  for (const lineId of Object.keys(lineBoard)) {
    const current = lineBoard[lineId].map(s => s.id).sort().join(',')
    const initial = (initialLineBoard[lineId] || []).map(s => s.id).sort().join(',')
    if (current !== initial) return true
  }
  const currentPool = poolStations.value.map(s => s.id).sort().join(',')
  const initialPool = (initialLineBoard['pool'] || []).map(s => s.id).sort().join(',')
  return currentPool !== initialPool
})

// ── 解析辅助 ──
function parseJsonArr(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

// ── 数据加载 ──
async function fetchOptions() {
  try {
    const r = await getCountryAll()
    countryOptions.value = normalizeList(r.data || [], ['id'])
  } catch {}
  try {
    const r = await getCityAll()
    allCityOptions.value = normalizeList(r.data || [], ['id', 'countryId'])
  } catch {}
}

async function initBoard() {
  if (!filterCityId.value) return
  loading.value = true
  try {
    const [lineRes, stationRes] = await Promise.all([
      getMetroLineList({ cityId: filterCityId.value, pageSize: 999 }),
      getStationsByCityId(filterCityId.value),
    ])
    metroLines.value = normalizeList(lineRes.data?.records || [], ['id']).map(l => ({ ...l, id: String(l.id) }))
    const allStations = stationRes.data || []

    // 初始化线路列
    const cityLineIds = new Set(metroLines.value.map(l => l.id))
    metroLines.value.forEach(line => {
      lineBoard[line.id] = []
    })

    // 将站点分配到对应线路列或待分配池
    loadStats.total = allStations.length
    loadStats.filtered = 0
    allStations.forEach(station => {
      // 1. 去除未开通站点
      if (station.statusCode !== 1) { loadStats.filtered++; return }

      const lineIds = parseJsonArr(station.lineIds).map(String)

      // 2. 没有所属线路 → 放入待分配池
      if (lineIds.length === 0) {
        poolStations.value.push({ ...station })
        return
      }

      // 3. 分离：属于该城市的线路 vs 外部线路
      const cityLids = lineIds.filter(lid => cityLineIds.has(lid))

      if (cityLids.length === 0) {
        // 没有任何线路属于该城市 → 进待分配池
        poolStations.value.push({ ...station })
        return
      }

      // 4. 有属于该城市的线路 → 放入对应线路列，不进待分配池
      for (const lid of cityLids) {
        if (lineBoard[lid]) {
          lineBoard[lid].push({ ...station })
        }
      }
    })

    // 快照初始状态
    snapshotInitial()
    sortPool()
  } catch (e) {
    console.error('initBoard error:', e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function snapshotInitial() {
  initialLineBoard = {}
  initialLineBoard['pool'] = poolStations.value.map(s => ({ ...s }))
  metroLines.value.forEach(line => {
    initialLineBoard[line.id] = (lineBoard[line.id] || []).map(s => ({ ...s }))
  })
}

function onCityChange() {
  poolStations.value = []
  loadStats.total = 0
  loadStats.filtered = 0
  // 清空线路列
  Object.keys(lineBoard).forEach(k => { delete lineBoard[k] })
  if (filterCityId.value) {
    initBoard()
  }
}

// ── 待分配池排序 ──
function handlePoolSort(command) {
  poolSortBy.value = command
  sortPool()
}

function sortPool() {
  const arr = poolStations.value
  const key = poolSortBy.value
  arr.sort((a, b) => {
    if (key === 'name_asc') return (a.stationName || '').localeCompare(b.stationName || '', 'zh')
    if (key === 'lng_asc') return (Number(a.longitude) || 0) - (Number(b.longitude) || 0)
    if (key === 'lng_desc') return (Number(b.longitude) || 0) - (Number(a.longitude) || 0)
    if (key === 'lat_asc') return (Number(a.latitude) || 0) - (Number(b.latitude) || 0)
    if (key === 'lat_desc') return (Number(b.latitude) || 0) - (Number(a.latitude) || 0)
    return 0
  })
}

function onPoolAdd() {
  nextTick(() => sortPool())
}

// ── 保存变更 ──
async function handleSave() {
  const assignMap = {} // lineId => { lineName, stationIds: [] }
  const removeMap = {} // lineId => stationIds: []

  metroLines.value.forEach(line => {
    const currentIds = new Set((lineBoard[line.id] || []).map(s => s.id))
    const initialIds = new Set((initialLineBoard[line.id] || []).map(s => s.id))

    const toAssign = [...currentIds].filter(id => !initialIds.has(id))
    const toRemove = [...initialIds].filter(id => !currentIds.has(id))

    if (toAssign.length > 0) {
      assignMap[line.id] = { lineName: line.lineName, stationIds: toAssign }
    }
    if (toRemove.length > 0) {
      removeMap[line.id] = toRemove
    }
  })

  const assignOps = Object.entries(assignMap)
  const removeOps = Object.entries(removeMap)

  if (assignOps.length === 0 && removeOps.length === 0) {
    ElMessage.info('没有需要保存的变更')
    return
  }

  const totalOps = assignOps.length + removeOps.length
  await ElMessageBox.confirm(
    `将执行 ${totalOps} 项线路分配变更，确定保存？`,
    '保存变更',
    { confirmButtonText: '确定保存', cancelButtonText: '取消', type: 'warning' }
  )

  saving.value = true
  let success = 0
  let fail = 0
  try {
    const promises = []
    for (const [lineId, { lineName, stationIds }] of assignOps) {
      promises.push(
        batchAssignLine({ lineId: String(lineId), lineName, stationIds })
          .then(() => success++)
          .catch(e => { fail++; console.error('assign error:', e) })
      )
    }
    for (const [lineId, stationIds] of removeOps) {
      promises.push(
        batchRemoveLine({ lineId: String(lineId), stationIds })
          .then(() => success++)
          .catch(e => { fail++; console.error('assign error:', e) })
      )
    }
    await Promise.all(promises)

    if (fail === 0) {
      ElMessage.success(`保存成功，共处理 ${success} 项`)
    } else {
      ElMessage.warning(`部分失败：成功 ${success}，失败 ${fail}`)
    }
    await initBoard()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// ── 生命周期 ──
onMounted(() => {
  fetchOptions()
})
</script>

<style scoped>
.station-line-assign {
  background: var(--bg-primary, #fff);
  min-height: 100vh;
  padding: 0 0 24px 0;
}

/* ── 页头 ── */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  flex: 1;
  letter-spacing: 0.5px;
}
.header-actions { display: flex; gap: 8px; }

/* ── 筛选栏 ── */
.filter-card {
  margin-bottom: 20px;
  border-radius: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 14px 20px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

/* ── 看板容器 ── */
.kanban-wrapper {
  width: 100%;
}

/* ── 统计栏 ── */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
  padding: 10px 18px;
  background: #f8f9fb;
  border-radius: 10px;
  border: 1px solid #ebeef5;
}
.stats-item {
  font-size: 13px;
  color: #606266;
}
.stats-item b {
  color: #409EFF;
  font-size: 15px;
  margin: 0 2px;
}
.kanban-scroll {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 12px;
}
.kanban-board {
  display: flex;
  gap: 14px;
  min-width: max-content;
  align-items: flex-start;
}

/* ── 列 ── */
.kanban-column {
  flex: 0 0 240px;
  min-width: 240px;
  max-width: 260px;
  border-radius: 12px;
  background: #f8f9fb;
  border: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 260px);
  transition: box-shadow 0.2s;
}
.kanban-column:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* ── 列头 ── */
.column-header {
  padding: 12px 14px;
  border-radius: 12px 12px 0 0;
  background: #409EFF;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.pool-header {
  background: #909399;
}
.header-right-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sort-btn {
  cursor: pointer;
  font-size: 16px;
  opacity: 0.8;
  transition: opacity 0.2s;
  color: #fff;
}
.sort-btn:hover {
  opacity: 1;
}
.column-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
}
.line-no {
  font-size: 11px;
  opacity: 0.75;
  font-weight: 400;
  margin-left: 2px;
}

/* ── 列内容（待分配池 - 列表样式） ── */
.column-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
  min-height: 100px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.column-body::-webkit-scrollbar {
  width: 4px;
}
.column-body::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 2px;
}

/* ── 待分配池卡片 ── */
.station-card {
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s ease;
  user-select: none;
}
.station-card:hover {
  border-color: #b3d8ff;
  box-shadow: 0 1px 6px rgba(64, 158, 255, 0.12);
  transform: translateY(-1px);
}
.station-card:active {
  cursor: grabbing;
}
.card-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-en {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-coord {
  font-size: 10px;
  color: #b0b4bb;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  margin-top: 2px;
}

/* ── 线路列（叠排卡片样式） ── */
.line-column {
  max-height: none;
}

.stacked-body {
  flex: 1;
  padding: 10px 10px 14px 10px;
  min-height: 80px;
  position: relative;
  display: flex;
  flex-direction: column;
}

.stacked-card {
  position: relative;
  height: 34px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #e0e3e8;
  border-radius: 8px;
  cursor: grab;
  user-select: none;
  transition: transform 0.22s ease, box-shadow 0.22s ease, z-index 0s;
  margin-top: -4px;
  z-index: var(--i, 0);
}
.stacked-card:first-child {
  margin-top: 0;
}
.stacked-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.13);
  z-index: 999;
  border-color: var(--line-color, #409EFF);
}
.stacked-card:active {
  cursor: grabbing;
}

.stacked-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1;
}

.stacked-ghost {
  opacity: 0.3;
  background: #ecf5ff !important;
  border: 2px dashed var(--line-color, #409EFF) !important;
}
.stacked-drag {
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18) !important;
  transform: rotate(2deg) scale(1.04);
  z-index: 1000 !important;
}

/* ── 空状态 ── */
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: #c0c4cc;
  font-size: 13px;
  padding: 24px 0;
}

/* ── 拖拽动画 ── */
.card-ghost {
  opacity: 0.35;
  background: #ecf5ff !important;
  border: 2px dashed #409EFF !important;
}
.card-drag {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15) !important;
  transform: rotate(2deg);
  opacity: 0.9;
}

/* ── 下拉菜单激活态 ── */
:deep(.el-dropdown-menu__item.active) {
  color: #409EFF;
  font-weight: 600;
}

/* ── Badge 适配深色列头 ── */
.column-header :deep(.el-badge__content) {
  border: none;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .kanban-column {
    flex: 0 0 200px;
    min-width: 200px;
  }
}
</style>
