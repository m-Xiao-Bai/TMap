<template>
  <div class="metro-map-wrapper" :style="{ height }">
    <svg
      ref="svgRef"
      class="metro-svg"
      @mousedown="onSvgMouseDown"
    >
      <g ref="zoomGroupRef">
        <defs>
          <filter id="glow">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <filter id="glow-strong">
            <feGaussianBlur stdDeviation="5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <!-- 线路 -->
        <g v-for="line in renderedLines" :key="'line-' + line.name"
           :class="{ dimmed: selectedLine && selectedLine !== line.name }">
          <path
            v-if="line.connected && line.path"
            :d="line.path"
            fill="none"
            :stroke="line.color"
            :stroke-width="selectedLine === line.name ? 12 : 8"
            :stroke-opacity="selectedLine === line.name ? 0.25 : 0.12"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-if="line.connected && line.path"
            :d="line.path"
            fill="none"
            :stroke="line.color"
            :stroke-width="selectedLine === line.name ? 7 : 4.5"
            :stroke-opacity="selectedLine === line.name ? 1 : 0.92"
            stroke-linecap="round"
            stroke-linejoin="round"
            :filter="selectedLine === line.name ? 'url(#glow-strong)' : 'url(#glow)'"
          />
        </g>

        <!-- 最短路径高亮 -->
        <g v-if="pathRoute.length > 1" class="path-highlight">
          <path
            :d="routePathD"
            fill="none"
            stroke="#ff6b35"
            stroke-width="10"
            stroke-opacity="0.18"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            :d="routePathD"
            fill="none"
            stroke="#ff6b35"
            stroke-width="6"
            stroke-opacity="0.95"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-dasharray="12 6"
            class="route-flow"
            filter="url(#glow-strong)"
          />
        </g>

        <!-- 站点 -->
        <g v-for="station in layoutStations" :key="'station-' + station._id"
           :class="{
             dimmed: selectedLine && !(station._lineNames || [station._lineName]).includes(selectedLine) && !pathRoute.includes(station._id),
             'path-station': pathRoute.includes(station._id),
           }">
          <!-- 普通站 -->
          <circle
            v-if="station.isTransfer !== 1"
            :cx="station._x" :cy="station._y"
            :r="pathRoute.includes(station._id) ? 9 : 7"
            :fill="isPathEndpoint(station._id) ? '#ff6b35' : '#fff'"
            :stroke="isPathEndpoint(station._id) ? '#ff6b35' : station._lineColor"
            :stroke-width="pathRoute.includes(station._id) ? 3.5 : 2.5"
            class="station-dot"
            @click="onStationClick(station, $event)"
            @mouseenter="showTooltip(station, $event)"
            @mouseleave="hideTooltip"
          />
          <!-- 换乘站 -->
          <circle
            v-else
            :cx="station._x" :cy="station._y"
            :r="pathRoute.includes(station._id) ? 13 : 10"
            :fill="isPathEndpoint(station._id) ? '#ff6b35' : '#fff'"
            :stroke="isPathEndpoint(station._id) ? '#ff6b35' : '#303133'"
            :stroke-width="pathRoute.includes(station._id) ? 4 : 3"
            class="station-dot transfer"
            @click="onStationClick(station, $event)"
            @mouseenter="showTooltip(station, $event)"
            @mouseleave="hideTooltip"
          />
          <!-- 路径编号 -->
          <text v-if="pathRoute.includes(station._id) && !isPathEndpoint(station._id)"
            :x="station._x" :y="station._y + 1"
            text-anchor="middle" dominant-baseline="central"
            class="path-index"
          >{{ pathRoute.indexOf(station._id) }}</text>
        </g>

        <!-- 站名标签 -->
        <g v-for="station in labeledStations" :key="'label-' + station._id"
           :class="{
             dimmed: selectedLine && !(station._lineNames || [station._lineName]).includes(selectedLine) && !pathRoute.includes(station._id),
           }">
          <text
            v-if="station._labelLines && station._labelLines.length > 1"
            :x="station._labelX" :y="station._labelY"
            class="station-label"
            :text-anchor="station._labelAnchor"
            :class="{ transfer: station.isTransfer === 1 }"
          >
            <tspan
              v-for="(ln, i) in station._labelLines" :key="i"
              :x="station._labelX"
              :dy="i === 0 ? '0em' : '1.15em'"
            >{{ ln }}</tspan>
          </text>
          <text
            v-else
            :x="station._labelX" :y="station._labelY"
            class="station-label"
            :text-anchor="station._labelAnchor"
            :class="{ transfer: station.isTransfer === 1 }"
          >{{ station.stationName }}</text>
        </g>
      </g>
    </svg>

    <!-- 缩放控件 -->
    <div class="zoom-controls">
      <button class="zoom-btn" @click="zoomIn" title="放大">
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 4v16m-8-8h16" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" fill="none"/></svg>
      </button>
      <button class="zoom-btn" @click="zoomOut" title="缩小">
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M5 12h14" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" fill="none"/></svg>
      </button>
      <button class="zoom-btn" @click="zoomReset" title="重置">
        <svg viewBox="0 0 24 24" width="18" height="18"><path d="M4 8V4h4M20 8V4h-4M4 16v4h4M20 16v4h-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
      </button>
    </div>

    <!-- 路径查找模式提示 -->
    <div v-if="pathMode" class="path-mode-bar">
      <span class="path-mode-icon">
        <svg viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" fill="none"/><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>
      </span>
      <span v-if="!pathStart">请点选起点站</span>
      <span v-else>请点选终点站</span>
      <button class="path-mode-cancel" @click="exitPathMode">取消</button>
    </div>

    <!-- 路径结果面板 -->
    <div v-if="routeInfo" class="route-info-panel">
      <div class="route-header">
        <div class="route-title">
          <svg viewBox="0 0 24 24" width="18" height="18" style="flex-shrink:0"><path d="M3 12h4l3-9 4 18 3-9h4" stroke="#ff6b35" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
          <span>最短路径</span>
        </div>
        <button class="route-close" @click="clearPath">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg>
        </button>
      </div>
      <div class="route-endpoints">
        <div class="route-endpoint start">
          <span class="endpoint-dot"></span>
          <span class="endpoint-name">{{ routeInfo.start.stationName }}</span>
        </div>
        <div class="route-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M5 12h14m-4-4l4 4-4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
        </div>
        <div class="route-endpoint end">
          <span class="endpoint-dot"></span>
          <span class="endpoint-name">{{ routeInfo.end.stationName }}</span>
        </div>
      </div>
      <div class="route-stats">
        <div class="route-stat">
          <span class="stat-num">{{ routeInfo.totalStops }}</span>
          <span class="stat-label">站</span>
        </div>
      </div>
      <div class="route-stations-list">
        <div v-for="(sid, idx) in pathRoute" :key="sid" class="route-station-item">
          <span class="rsi-num" :class="{ start: idx === 0, end: idx === pathRoute.length - 1 }">{{ idx === 0 ? '起' : idx === pathRoute.length - 1 ? '终' : idx }}</span>
          <span class="rsi-line" :style="{ background: getStationLineById(sid)?.color || '#ccc' }"></span>
          <span class="rsi-name">{{ getStationById(sid)?.stationName || sid }}</span>
        </div>
      </div>
    </div>

    <!-- 图例（可点击选线）-->
    <div v-if="showLegend && legendItems.length" class="map-legend">
      <div class="legend-header">
        <span class="legend-icon">🚇</span>
        <span>线路图例</span>
      </div>
      <div class="legend-list">
        <div
          v-for="item in legendItems" :key="item.name"
          class="legend-item"
          :class="{ active: selectedLine === item.name }"
          @click="onLegendClick(item.name)"
        >
          <span class="legend-line" :style="{ background: item.color }"></span>
          <span class="legend-name">{{ item.name }}</span>
          <span v-if="item.stationCount" class="legend-count">{{ item.stationCount }}站</span>
        </div>
      </div>
    </div>

    <!-- 操作提示 -->
    <div class="action-hint" v-if="!pathMode && !selectedLine">
      <span>滚轮缩放 · 拖拽平移 · 点击线路高亮 · 站点右键查路径</span>
    </div>

    <!-- 统计 -->
    <div v-if="totalStations > 0" class="map-stats">
      <span class="stats-dot"></span>
      <span>{{ totalStations }} 个站点</span>
      <span v-if="transferCount > 0" class="stats-divider">·</span>
      <span v-if="transferCount > 0">{{ transferCount }} 个换乘站</span>
    </div>

    <!-- 悬浮信息卡 -->
    <Teleport to="body">
      <div v-if="tooltip.visible" class="metro-tooltip-card" :style="tooltip.style">
        <div class="tooltip-name">{{ tooltip.station?.stationName }}</div>
        <div v-if="tooltip.station?.stationNameEn" class="tooltip-en">{{ tooltip.station.stationNameEn }}</div>
        <div v-if="tooltip.station?._lineNames" class="tooltip-lines">{{ tooltip.station._lineNames }}</div>
        <div v-if="tooltip.station?.isTransfer === 1" class="tooltip-transfer">换乘站</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { zoom as d3Zoom, zoomIdentity } from 'd3-zoom'
import { select as d3Select } from 'd3-selection'

const props = defineProps({
  stations:  { type: Array, default: () => [] },
  lines:     { type: Array, default: () => [] },
  height:    { type: String, default: '520px' },
  showLegend:{ type: Boolean, default: true },
})

defineEmits(['station-click'])

const svgW = 1200, svgH = 800, PAD = 70

const svgRef = ref(null)
const zoomGroupRef = ref(null)
let zoomBehavior = null
let isDragging = false

// ── 缩放 ──
const currentTransform = ref(zoomIdentity)

function getZoomExtent() {
  const positions = stationPositions.value
  const vals = Object.values(positions)
  if (vals.length > 0) {
    const xs = vals.map(p => p.x)
    const ys = vals.map(p => p.y)
    const minX = Math.min(...xs) - PAD * 2
    const maxX = Math.max(...xs) + PAD * 2
    const minY = Math.min(...ys) - PAD * 2
    const maxY = Math.max(...ys) + PAD * 2
    return [[minX, minY], [maxX, maxY]]
  }
  return [[0, 0], [svgW, svgH]]
}

function initZoom() {
  if (!svgRef.value) return
  const svg = d3Select(svgRef.value)
  zoomBehavior = d3Zoom()
    .scaleExtent([0.3, 5])
    .translateExtent(getZoomExtent())
    .on('zoom', (event) => {
      currentTransform.value = event.transform
      if (zoomGroupRef.value) {
        zoomGroupRef.value.setAttribute('transform', event.transform)
      }
    })
  svg.call(zoomBehavior)
  svg.call(zoomBehavior.transform, zoomIdentity)
}

function updateZoomExtent() {
  if (!zoomBehavior || !svgRef.value) return
  zoomBehavior.translateExtent(getZoomExtent())
}

function zoomIn() {
  if (!svgRef.value) return
  d3Select(svgRef.value).transition().duration(300).call(zoomBehavior.scaleBy, 1.4)
}

function zoomOut() {
  if (!svgRef.value) return
  d3Select(svgRef.value).transition().duration(300).call(zoomBehavior.scaleBy, 0.7)
}

function zoomReset() {
  if (!svgRef.value) return
  d3Select(svgRef.value).transition().duration(500).call(zoomBehavior.transform, zoomIdentity)
}

function onSvgMouseDown(e) {
  isDragging = false
  const onMove = () => { isDragging = true }
  const onUp = () => {
    svgRef.value.removeEventListener('mousemove', onMove)
    svgRef.value.removeEventListener('mouseup', onUp)
  }
  svgRef.value.addEventListener('mousemove', onMove)
  svgRef.value.addEventListener('mouseup', onUp)
}

// ── 合并所有站点 ──
const allStations = computed(() => {
  const map = new Map()
  const source = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []).map(s => ({ ...s, _lineColor: l.color, _lineName: l.name })))
    : props.stations.map(s => ({ ...s, _lineColor: s.lineColor || '#409EFF' }))
  for (const s of source) {
    const id = String(s.id)
    if (!map.has(id)) {
      map.set(id, { ...s, _id: id, _lineNames: [s._lineName] })
    } else {
      const existing = map.get(id)
      if (!existing._lineNames.includes(s._lineName)) {
        existing._lineNames.push(s._lineName)
      }
    }
  }
  return [...map.values()]
})

// ── 经纬度 → SVG 坐标 ──
const stationPositions = computed(() => {
  const valid = allStations.value.filter(s =>
    s.longitude != null && s.latitude != null &&
    !isNaN(Number(s.longitude)) && !isNaN(Number(s.latitude))
  )
  if (!valid.length) return {}
  const lngs = valid.map(s => Number(s.longitude))
  const lats = valid.map(s => Number(s.latitude))
  let minLng = Math.min(...lngs), maxLng = Math.max(...lngs)
  let minLat = Math.min(...lats), maxLat = Math.max(...lats)
  const lngSpan = maxLng - minLng || 0.01
  const latSpan = maxLat - minLat || 0.01
  minLng -= lngSpan * 0.08; maxLng += lngSpan * 0.08
  minLat -= latSpan * 0.08; maxLat += latSpan * 0.08
  const usableW = svgW - PAD * 2, usableH = svgH - PAD * 2
  const scaleX = usableW / (maxLng - minLng)
  const scaleY = usableH / (maxLat - minLat)
  const pos = {}
  for (const s of valid) {
    pos[s._id] = {
      x: PAD + (Number(s.longitude) - minLng) * scaleX,
      y: PAD + (maxLat - Number(s.latitude)) * scaleY,
    }
  }
  return pos
})

// ── 示意图布局（保持地理角度，仅调整间距）──
const layoutStations = ref([])

function runSchematicLayout() {
  const pos = stationPositions.value
  const stations = allStations.value.filter(s => pos[s._id])
  if (!stations.length) { layoutStations.value = []; return }

  // 以地理坐标为基础，逐条线路确保相邻站点有最小间距
  // 保持原始地理角度不变，只在角度方向上推远过近的站点
  const MIN_DIST = 35 // 相邻站点最小像素距离
  const placements = {}

  // 先用地理坐标初始化所有站点位置
  for (const s of stations) {
    placements[s._id] = { x: pos[s._id].x, y: pos[s._id].y }
  }

  // 对每条线路，沿站点顺序确保相邻站点间距 >= MIN_DIST
  // 保持地理角度，只在原始方向上拉伸
  for (const line of props.lines) {
    const sList = line.stations || []
    if (sList.length < 2) continue

    // 沿线路从头到尾，逐对检查间距
    for (let i = 0; i < sList.length - 1; i++) {
      const sidA = String(sList[i].id)
      const sidB = String(sList[i + 1].id)
      const a = placements[sidA]
      const b = placements[sidB]
      if (!a || !b) continue

      const dx = b.x - a.x
      const dy = b.y - a.y
      const dist = Math.sqrt(dx * dx + dy * dy)

      if (dist < MIN_DIST && dist > 0.01) {
        // 沿原始地理方向推远B点（及后续所有站点）
        const need = MIN_DIST - dist
        const nx = dx / dist, ny = dy / dist
        // 把B及之后的站点整体沿方向推移
        for (let j = i + 1; j < sList.length; j++) {
          const sid = String(sList[j].id)
          if (placements[sid]) {
            placements[sid].x += nx * need
            placements[sid].y += ny * need
          }
        }
      }
    }
  }

  // 对非换乘站做轻量碰撞解决：推开重叠的站点（仅对不同线路的站点）
  const stationLineMap = {}
  for (const line of props.lines) {
    for (const s of (line.stations || [])) {
      const sid = String(s.id)
      if (!stationLineMap[sid]) stationLineMap[sid] = new Set()
      stationLineMap[sid].add(line.name)
    }
  }

  const allIds = Object.keys(placements)
  for (let iter = 0; iter < 15; iter++) {
    let moved = false
    for (let i = 0; i < allIds.length; i++) {
      for (let j = i + 1; j < allIds.length; j++) {
        const a = placements[allIds[i]]
        const b = placements[allIds[j]]
        if (!a || !b) continue

        const dx = b.x - a.x, dy = b.y - a.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        // 只处理真正重叠的站点（距离 < 15px）
        if (dist >= 15 || dist < 0.01) continue

        // 如果是同一线路的相邻站点，跳过（上面已处理）
        const linesA = stationLineMap[allIds[i]]
        const linesB = stationLineMap[allIds[j]]
        const shareLine = linesA && linesB && [...linesA].some(l => linesB.has(l))
        if (shareLine) {
          // 检查是否在同一线路中相邻
          let adjacent = false
          for (const line of props.lines) {
            const sList = line.stations || []
            for (let k = 0; k < sList.length - 1; k++) {
              const id1 = String(sList[k].id), id2 = String(sList[k + 1].id)
              if ((id1 === allIds[i] && id2 === allIds[j]) || (id1 === allIds[j] && id2 === allIds[i])) {
                adjacent = true; break
              }
            }
            if (adjacent) break
          }
          if (adjacent) continue
        }

        // 推开重叠站点
        const push = (15 - dist) / 2 + 1
        const nx = dx / dist, ny = dy / dist
        a.x -= nx * push; a.y -= ny * push
        b.x += nx * push; b.y += ny * push
        moved = true
      }
    }
    if (!moved) break
  }

  // 构建最终布局数据
  const result = stations.map(s => ({
    ...s,
    _x: placements[s._id]?.x ?? pos[s._id]?.x ?? 0,
    _y: placements[s._id]?.y ?? pos[s._id]?.y ?? 0,
  }))

  layoutStations.value = result
  updateZoomExtent()
}

// ── 线路选择高亮 ──
const selectedLine = ref(null)

function onLegendClick(name) {
  if (pathMode.value) return
  selectedLine.value = selectedLine.value === name ? null : name
}

// ── 最短路径 ──
const pathMode = ref(false)
const pathStart = ref(null)
const pathEnd = ref(null)
const pathRoute = ref([])
const routeInfo = ref(null)

const stationById = computed(() => {
  const m = {}
  for (const s of layoutStations.value) m[s._id] = s
  return m
})

function getStationById(id) { return stationById.value[id] }
function getStationLineById(id) {
  const s = stationById.value[id]
  return s ? { color: s._lineColor, name: s._lineName } : null
}

const adjacencyGraph = computed(() => {
  const g = {}
  function ensure(id) { if (!g[id]) g[id] = new Set() }
  function add(a, b) { ensure(a); ensure(b); g[a].add(b); g[b].add(a) }

  for (const line of props.lines) {
    if (line.connected === false) continue
    const sList = line.stations || []
    for (let i = 0; i < sList.length - 1; i++) {
      add(String(sList[i].id), String(sList[i + 1].id))
    }
  }
  return g
})

function bfs(startId, endId) {
  const g = adjacencyGraph.value
  if (!g[startId] || !g[endId]) return []
  if (startId === endId) return [startId]

  const visited = new Set([startId])
  const parent = {}
  const queue = [startId]

  while (queue.length) {
    const cur = queue.shift()
    if (cur === endId) {
      const path = []
      let n = endId
      while (n) { path.unshift(n); n = parent[n] }
      return path
    }
    for (const nb of (g[cur] || [])) {
      if (!visited.has(nb)) {
        visited.add(nb)
        parent[nb] = cur
        queue.push(nb)
      }
    }
  }
  return []
}

function isPathEndpoint(id) {
  return (pathStart.value === id) || (pathEnd.value === id)
}

function enterPathMode() {
  pathMode.value = true
  pathStart.value = null
  pathEnd.value = null
  pathRoute.value = []
  routeInfo.value = null
  selectedLine.value = null
}

function exitPathMode() {
  pathMode.value = false
  pathStart.value = null
  pathEnd.value = null
  pathRoute.value = []
  routeInfo.value = null
}

function clearPath() {
  pathRoute.value = []
  routeInfo.value = null
  pathStart.value = null
  pathEnd.value = null
  pathMode.value = false
}

function onStationClick(station, event) {
  if (isDragging) return
  event.stopPropagation()

  if (!pathMode.value) {
    enterPathMode()
    pathStart.value = station._id
    return
  }

  if (!pathStart.value) {
    pathStart.value = station._id
    return
  }

  if (station._id === pathStart.value) return

  pathEnd.value = station._id
  const route = bfs(pathStart.value, station._id)
  pathRoute.value = route

  if (route.length > 1) {
    routeInfo.value = {
      start: getStationById(pathStart.value),
      end: getStationById(station._id),
      totalStops: route.length - 1,
    }
  } else {
    routeInfo.value = null
  }
  pathMode.value = false
}

const routePathD = computed(() => {
  if (pathRoute.value.length < 2) return ''
  const pts = pathRoute.value
    .map(id => stationById.value[id])
    .filter(Boolean)
    .map(s => `${s._x},${s._y}`)
  return 'M' + pts.join('L')
})

// ── 八方向标签位置 ──
const labeledStations = computed(() => {
  const sList = layoutStations.value
  if (!sList.length) return []

  const CANDIDATES = [
    { dx: 0, dy: -16, anchor: 'middle' },
    { dx: 14, dy: -10, anchor: 'start' },
    { dx: 18, dy: 4, anchor: 'start' },
    { dx: 14, dy: 16, anchor: 'start' },
    { dx: 0, dy: 24, anchor: 'middle' },
    { dx: -14, dy: 16, anchor: 'end' },
    { dx: -18, dy: 4, anchor: 'end' },
    { dx: -14, dy: -10, anchor: 'end' },
  ]

  const placed = []

  for (const s of sList) {
    const name = s.stationName || ''
    const MAX = 8
    let lines
    if (name.length > MAX) {
      const mid = Math.ceil(name.length / 2)
      const splitChars = ['（', '(', '·', '—', '-', ' ']
      let bestIdx = -1
      for (const ch of splitChars) {
        const idx = name.indexOf(ch)
        if (idx > 0 && idx < name.length - 1) { bestIdx = idx + 1; break }
      }
      lines = bestIdx > 0 ? [name.slice(0, bestIdx), name.slice(bestIdx)] : [name.slice(0, mid), name.slice(mid)]
    } else {
      lines = [name]
    }

    const charW = s.isTransfer === 1 ? 11 : 9
    const lineH = 14
    const labelW = Math.max(...lines.map(l => l.length)) * charW
    const labelH = lines.length * lineH

    let bestDir = CANDIDATES[4] // 默认下方
    let bestScore = Infinity

    for (const dir of CANDIDATES) {
      const lx = s._x + dir.dx
      const ly = s._y + dir.dy
      const lRect = {
        x: dir.anchor === 'end' ? lx - labelW : dir.anchor === 'start' ? lx : lx - labelW / 2,
        y: ly - lineH * 0.8,
        w: labelW,
        h: labelH,
      }

      let overlap = 0
      for (const p of placed) {
        const ox = Math.max(0, Math.min(lRect.x + lRect.w, p.x + p.w) - Math.max(lRect.x, p.x))
        const oy = Math.max(0, Math.min(lRect.y + lRect.h, p.y + p.h) - Math.max(lRect.y, p.y))
        overlap += ox * oy
      }

      // 距离中心越远越好（避免标签聚集）
      const cx = svgW / 2, cy = svgH / 2
      const edgeDist = Math.sqrt((lx - cx) ** 2 + (ly - cy) ** 2)
      const score = overlap * 100 - edgeDist * 0.3

      if (score < bestScore) {
        bestScore = score
        bestDir = dir
      }
    }

    const finalX = s._x + bestDir.dx
    const finalY = s._y + bestDir.dy
    s._labelX = finalX
    s._labelY = finalY
    s._labelAnchor = bestDir.anchor
    s._labelLines = lines

    const rectX = bestDir.anchor === 'end' ? finalX - labelW : bestDir.anchor === 'start' ? finalX : finalX - labelW / 2
    placed.push({ x: rectX, y: finalY - lineH * 0.8, w: labelW, h: labelH })
  }

  return sList
})

// ── 圆角路径构建 ──
function buildSmoothPath(waypoints) {
  if (waypoints.length < 2) return ''
  if (waypoints.length === 2) {
    return `M${waypoints[0].x},${waypoints[0].y}L${waypoints[1].x},${waypoints[1].y}`
  }

  const R = 12
  let d = `M${waypoints[0].x},${waypoints[0].y}`

  for (let i = 1; i < waypoints.length - 1; i++) {
    const prev = waypoints[i - 1]
    const curr = waypoints[i]
    const next = waypoints[i + 1]

    const d1x = curr.x - prev.x, d1y = curr.y - prev.y
    const d2x = next.x - curr.x, d2y = next.y - curr.y
    const len1 = Math.sqrt(d1x * d1x + d1y * d1y) || 1
    const len2 = Math.sqrt(d2x * d2x + d2y * d2y) || 1
    const nd1x = d1x / len1, nd1y = d1y / len1
    const nd2x = d2x / len2, nd2y = d2y / len2

    const cross = nd1x * nd2y - nd1y * nd2x
    const dot = nd1x * nd2x + nd1y * nd2y
    const angle = Math.atan2(cross, dot)

    if (Math.abs(angle) < 0.15) {
      d += `L${curr.x},${curr.y}`
    } else {
      const r = Math.min(R, len1 / 2, len2 / 2)
      const sx = curr.x - nd1x * r
      const sy = curr.y - nd1y * r
      const ex = curr.x + nd2x * r
      const ey = curr.y + nd2y * r
      d += `L${sx},${sy}Q${curr.x},${curr.y},${ex},${ey}`
    }
  }

  const last = waypoints[waypoints.length - 1]
  d += `L${last.x},${last.y}`
  return d
}

// ── 线路路径 ──
const renderedLines = computed(() => {
  if (!props.lines.length) return []
  const pos = {}
  for (const s of layoutStations.value) pos[s._id] = { x: s._x, y: s._y }
  return props.lines.map(line => {
    if (line.connected === false) {
      return { name: line.name, color: line.color || '#409EFF', connected: false, path: null }
    }
    const coords = (line.stations || []).map(s => pos[String(s.id)]).filter(Boolean)
    if (coords.length < 2) {
      return { name: line.name, color: line.color || '#409EFF', connected: false, path: null }
    }
    return { name: line.name, color: line.color || '#409EFF', connected: true, path: buildSmoothPath(coords) }
  })
})

// ── 图例 ──
const legendItems = computed(() => {
  if (!props.lines.length) return []
  const seen = new Set()
  return props.lines
    .filter(l => { if (seen.has(l.name)) return false; seen.add(l.name); return true })
    .map(l => ({ name: l.name || '未知线路', color: l.color || '#409EFF', stationCount: l.stations?.length || 0 }))
})

const totalStations = computed(() => layoutStations.value.length)
const transferCount = computed(() => layoutStations.value.filter(s => s.isTransfer === 1).length)

// ── 悬浮信息卡 ──
const tooltip = ref({ visible: false, station: null, style: {} })

function showTooltip(station, event) {
  if (isDragging) return
  const rect = event.target.getBoundingClientRect()
  tooltip.value = {
    visible: true,
    station: { ...station, _lineNames: (station._lineNames || [station._lineName]).join('、') },
    style: { left: rect.left + rect.width / 2 + 'px', top: rect.top - 10 + 'px' },
  }
}

function hideTooltip() { tooltip.value.visible = false }

// ── 生命周期 ──
onMounted(() => { nextTick(() => { runSchematicLayout(); initZoom() }) })
watch(() => [props.stations, props.lines], () => {
  exitPathMode()
  selectedLine.value = null
  nextTick(() => { runSchematicLayout() })
}, { deep: true })
</script>

<style scoped>
.metro-map-wrapper {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid #e4e7ed;
  background: #f8f9fb;
}
.metro-svg {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}
.metro-svg:active { cursor: grabbing; }

/* ── 缩放控件 ── */
.zoom-controls {
  position: absolute;
  bottom: 16px;
  right: 16px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.zoom-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  transition: all 0.2s;
  padding: 0;
}
.zoom-btn:hover { background: #f0f2f5; color: #303133; border-color: #c0c4cc; }
.zoom-btn:active { transform: scale(0.92); }

/* ── 线路/站点 dimming ── */
.dimmed { opacity: 0.12; transition: opacity 0.35s; }
g:not(.dimmed) { transition: opacity 0.35s; }

/* ── 站点 ── */
.station-dot { cursor: pointer; transition: r 0.2s, stroke-width 0.2s; }
.station-dot:hover { r: 9; stroke-width: 3.5; }
.station-dot.transfer:hover { r: 13; stroke-width: 4; }
.path-station .station-dot { cursor: default; }

/* ── 路径编号 ── */
.path-index {
  font-size: 8px;
  fill: #fff;
  font-weight: 700;
  pointer-events: none;
}

/* ── 路径流动动画 ── */
.route-flow { animation: flowDash 1.2s linear infinite; }
@keyframes flowDash { to { stroke-dashoffset: -18; } }

/* ── 站名 ── */
.station-label {
  font-size: 10px; fill: #4a4a5a; font-weight: 500; pointer-events: none;
  paint-order: stroke fill; stroke: #f8f9fb; stroke-width: 3px; stroke-linejoin: round;
}
.station-label.transfer { font-size: 11px; font-weight: 700; fill: #1a1a2e; stroke-width: 3.5px; }

/* ── 路径模式提示条 ── */
.path-mode-bar {
  position: absolute; top: 12px; left: 50%; transform: translateX(-50%);
  z-index: 20;
  background: rgba(255, 107, 53, 0.95); color: #fff;
  padding: 8px 20px; border-radius: 20px;
  font-size: 13px; font-weight: 600;
  display: flex; align-items: center; gap: 8px;
  box-shadow: 0 4px 16px rgba(255, 107, 53, 0.35);
  animation: slideDown 0.3s ease;
}
@keyframes slideDown { from { transform: translateX(-50%) translateY(-20px); opacity: 0; } }
.path-mode-icon { display: flex; align-items: center; }
.path-mode-cancel {
  margin-left: 8px; padding: 2px 12px; border: 1px solid rgba(255,255,255,0.5);
  border-radius: 12px; background: transparent; color: #fff; font-size: 12px;
  cursor: pointer; transition: background 0.2s;
}
.path-mode-cancel:hover { background: rgba(255,255,255,0.2); }

/* ── 路径结果面板 ── */
.route-info-panel {
  position: absolute; top: 12px; right: 12px; z-index: 20;
  width: 260px; max-height: calc(100% - 24px);
  background: rgba(255, 255, 255, 0.97); backdrop-filter: blur(16px);
  border-radius: 14px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.8);
  overflow: hidden; display: flex; flex-direction: column;
  animation: slideIn 0.3s ease;
}
@keyframes slideIn { from { transform: translateX(20px); opacity: 0; } }
.route-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px 10px; border-bottom: 1px solid #f0f0f0;
}
.route-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 700; color: #303133; }
.route-close {
  width: 28px; height: 28px; border: none; border-radius: 8px; background: #f5f7fa;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: #909399; transition: all 0.2s;
}
.route-close:hover { background: #fef0f0; color: #f56c6c; }
.route-endpoints { padding: 12px 16px; display: flex; align-items: center; gap: 8px; }
.route-endpoint { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; }
.endpoint-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.route-endpoint.start .endpoint-dot { background: #ff6b35; }
.route-endpoint.end .endpoint-dot { background: #ff6b35; box-shadow: 0 0 0 3px rgba(255,107,53,0.2); }
.endpoint-name { font-size: 13px; font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.route-arrow { color: #c0c4cc; flex-shrink: 0; }
.route-stats { padding: 0 16px 10px; display: flex; gap: 16px; }
.route-stat { display: flex; align-items: baseline; gap: 2px; }
.stat-num { font-size: 24px; font-weight: 800; color: #ff6b35; }
.stat-label { font-size: 12px; color: #909399; }
.route-stations-list {
  flex: 1; overflow-y: auto; padding: 0 16px 14px;
  max-height: 280px;
}
.route-station-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 0; font-size: 12px; color: #606266;
}
.rsi-num {
  width: 20px; height: 20px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; color: #909399;
  background: #f5f7fa; flex-shrink: 0;
}
.rsi-num.start, .rsi-num.end { background: #fff3ec; color: #ff6b35; }
.rsi-line { width: 4px; height: 4px; border-radius: 50%; flex-shrink: 0; }
.rsi-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ── 图例 ── */
.map-legend {
  position: absolute; bottom: 16px; left: 16px; z-index: 10;
  background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(12px);
  border-radius: 12px; padding: 12px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1); border: 1px solid rgba(255, 255, 255, 0.8);
  min-width: 140px;
}
.legend-header {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #303133;
  margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0;
}
.legend-icon { font-size: 15px; }
.legend-list { display: flex; flex-direction: column; gap: 4px; }
.legend-item {
  display: flex; align-items: center; gap: 10px;
  padding: 5px 8px; margin: -5px -8px; border-radius: 8px;
  cursor: pointer; transition: all 0.2s;
}
.legend-item:hover { background: #f0f5ff; }
.legend-item.active { background: #ecf5ff; box-shadow: inset 0 0 0 1px #b3d8ff; }
.legend-line { width: 20px; height: 4px; border-radius: 2px; flex-shrink: 0; }
.legend-name { font-size: 12px; color: #303133; font-weight: 500; flex: 1; }
.legend-count { font-size: 11px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 4px; }

/* ── 操作提示 ── */
.action-hint {
  position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
  z-index: 5;
  background: rgba(48, 49, 51, 0.8); color: rgba(255, 255, 255, 0.85);
  padding: 5px 16px; border-radius: 14px;
  font-size: 11px; white-space: nowrap;
  pointer-events: none;
}

/* ── 统计 ── */
.map-stats {
  position: absolute; top: 12px; left: 12px; z-index: 10;
  background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(10px);
  border-radius: 20px; padding: 6px 14px;
  font-size: 12px; color: #606266;
  display: flex; align-items: center; gap: 6px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06); border: 1px solid rgba(255, 255, 255, 0.8);
}
.stats-dot { width: 6px; height: 6px; border-radius: 50%; background: #67c23a; animation: pulse 2s infinite; }
.stats-divider { color: #dcdfe6; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>

<style>
.metro-tooltip-card {
  position: fixed; transform: translate(-50%, -100%); z-index: 9999;
  background: rgba(26, 26, 46, 0.94); backdrop-filter: blur(12px);
  color: #fff; padding: 10px 16px; border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25); pointer-events: none;
  white-space: nowrap; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}
.tooltip-name { font-size: 14px; font-weight: 700; margin-bottom: 2px; }
.tooltip-en { font-size: 11px; opacity: 0.65; margin-bottom: 4px; }
.tooltip-lines { font-size: 11px; opacity: 0.8; }
.tooltip-transfer { font-size: 10px; margin-top: 4px; padding: 1px 6px; background: rgba(255, 200, 50, 0.2); color: #ffc832; border-radius: 4px; display: inline-block; }
</style>
