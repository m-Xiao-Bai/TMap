<template>
  <div class="metro-map-wrapper" :style="{ height }">
    <div ref="mapRef" class="metro-map"></div>

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
    <div v-if="routeInfo && showRoutePanel" class="route-info-panel">
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
          <span class="endpoint-name">{{ routeInfo.startName }}</span>
        </div>
        <div class="route-arrow">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M5 12h14m-4-4l4 4-4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
        </div>
        <div class="route-endpoint end">
          <span class="endpoint-dot"></span>
          <span class="endpoint-name">{{ routeInfo.endName }}</span>
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
          <span class="rsi-line" :style="{ background: getStationLineColor(sid) }"></span>
          <span class="rsi-name">{{ getStationName(sid) }}</span>
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
      <span>滚轮缩放 · 拖拽平移 · 顶部选站点查路径 · 点击站点快选 · 图例选线高亮</span>
    </div>

    <!-- 统计 -->
    <div v-if="totalStations > 0" class="map-stats">
      <span class="stats-dot"></span>
      <span>{{ totalStations }} 个站点</span>
      <span v-if="transferCount > 0" class="stats-divider">·</span>
      <span v-if="transferCount > 0">{{ transferCount }} 个换乘站</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps({
  stations: { type: Array, default: () => [] },
  lines: { type: Array, default: () => [] },
  height: { type: String, default: '520px' },
  showLegend: { type: Boolean, default: true },
  fitBounds: { type: Boolean, default: true },
  highlightLineName: { type: String, default: null },
  showRoutePanel: { type: Boolean, default: true },
  routeStyle: { type: Object, default: null },
  labelConfig: { type: Object, default: null },
})

// ── 配置合并（prop 覆盖默认值）──
const rs = computed(() => ({
  routeColor: '#ff6b35',
  glowWeight: 14, glowOpacity: 0.25,
  lineWeight: 6, lineOpacity: 0.95, dashArray: '12 6',
  endpointRadius: 10, endpointWeight: 4,
  midpointRadius: 7, midpointWeight: 3,
  dimLineGlowOpacity: 0.04, dimLineOpacity: 0.12,
  dimStationOpacity: 0.12, dimStationRadius: 3,
  ...props.routeStyle,
}))

const lc = computed(() => ({
  baseFontSize: 11, minFontSize: 6,
  shrinkStartZoom: 13, hideZoom: 11,
  fontWeight: 500, color: '#3a3a4a',
  ...props.labelConfig,
}))

const emit = defineEmits(['station-click', 'route-found', 'route-cleared'])

const mapRef = ref(null)
const legendItems = ref([])
let map = null
let markerGroup = null
let lineGroup = null
let routeGroup = null

// ── 合并站点 ──
const allStationsMap = computed(() => {
  const m = {}
  const source = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []).map(s => ({ ...s, _lineColor: l.color, _lineName: l.name })))
    : props.stations.map(s => ({ ...s, _lineColor: s.lineColor || '#409EFF' }))
  for (const s of source) {
    const id = String(s.id)
    if (!m[id]) {
      m[id] = { ...s, _id: id, _lineNames: [s._lineName] }
    } else {
      if (!m[id]._lineNames.includes(s._lineName)) m[id]._lineNames.push(s._lineName)
    }
  }
  return m
})

const totalStations = computed(() => Object.keys(allStationsMap.value).length)
const transferCount = computed(() => Object.values(allStationsMap.value).filter(s => s.isTransfer === 1).length)

// ── 邻接图（始终基于站点顺序建边，不依赖 connected 标记）──
const adjacencyGraph = computed(() => {
  const g = {}
  function ensure(id) { if (!g[id]) g[id] = new Set() }
  function add(a, b) { ensure(a); ensure(b); g[a].add(b); g[b].add(a) }
  for (const line of props.lines) {
    const sList = line.stations || []
    for (let i = 0; i < sList.length - 1; i++) {
      add(String(sList[i].id), String(sList[i + 1].id))
    }
  }
  return g
})

// ── BFS 最短路径 ──
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
      if (!visited.has(nb)) { visited.add(nb); parent[nb] = cur; queue.push(nb) }
    }
  }
  return []
}

// ── 路径查找状态 ──
const pathMode = ref(false)
const pathStart = ref(null)
const pathEnd = ref(null)
const pathRoute = ref([])
const routeInfo = ref(null)
const selectedLine = ref(null)
const stationMarkers = {}

function getStationName(id) { return allStationsMap.value[id]?.stationName || id }
function getStationLineColor(id) { return allStationsMap.value[id]?._lineColor || '#ccc' }

// ── 外部线路高亮联动 ──
watch(() => props.highlightLineName, (name) => {
  if (!lineGroup) return
  if (name) {
    highlightLine(name)
  } else {
    showAllLines()
  }
})

function findAndShowRoute(startId, endId) {
  if (!lineGroup || !routeGroup) { console.warn('[findRoute] map not ready'); return }
  selectedLine.value = null
  pathMode.value = false
  const graph = adjacencyGraph.value
  const route = bfs(startId, endId)
  pathRoute.value = route
  pathStart.value = startId
  pathEnd.value = endId

  if (route.length > 1) {
    routeInfo.value = {
      startName: getStationName(startId),
      endName: getStationName(endId),
      totalStops: route.length - 1,
    }
    highlightRoute(route)
    emit('route-found', { startName: getStationName(startId), endName: getStationName(endId), totalStops: route.length - 1 })
  } else {
    routeInfo.value = null
    clearRouteHighlight()
  }
}

function clearRouteOnly() {
  pathRoute.value = []
  routeInfo.value = null
  pathStart.value = null
  pathEnd.value = null
  pathMode.value = false
  clearRouteHighlight()
  emit('route-cleared')
}

defineExpose({ clearRoute: clearRouteOnly, findRoute: findAndShowRoute, pathRoute, routeInfo })

function exitPathMode() {
  pathMode.value = false
  pathStart.value = null
  pathEnd.value = null
  pathRoute.value = []
  routeInfo.value = null
  clearRouteHighlight()
}

function clearPath() {
  pathRoute.value = []
  routeInfo.value = null
  pathStart.value = null
  pathEnd.value = null
  pathMode.value = false
  clearRouteHighlight()
}

function clearRouteHighlight() {
  if (routeGroup) routeGroup.clearLayers()
  if (!lineGroup) return
  // 恢复所有地铁线路
  lineGroup.eachLayer(layer => {
    if (layer._lineName) {
      layer.setStyle({ opacity: layer._isGlow ? 0.15 : 0.9, weight: layer._isGlow ? 10 : 5 })
    }
  })
  // 恢复所有站点样式
  for (const [id, marker] of Object.entries(stationMarkers)) {
    const s = allStationsMap.value[id]
    if (!s) continue
    const isTransfer = s.isTransfer === 1
    marker.setStyle({
      fillColor: '#ffffff',
      color: isTransfer ? '#303133' : (s._lineColor || '#409EFF'),
      weight: isTransfer ? 2.5 : 2,
      radius: isTransfer ? 7 : 4.5,
      fillOpacity: 1,
      opacity: 1,
    })
    const tooltip = marker.getTooltip()
    if (tooltip) { const el = tooltip.getElement(); if (el) el.style.opacity = '1' }
  }
}

function highlightRoute(route) {
  if (!routeGroup || route.length < 2) return
  clearRouteHighlight()

  const routeSet = new Set(route)

  // 暗化所有地铁线路
  lineGroup.eachLayer(layer => {
    if (layer._lineName) {
      layer.setStyle({ opacity: layer._isGlow ? rs.value.dimLineGlowOpacity : rs.value.dimLineOpacity, weight: layer._isGlow ? 8 : 3 })
    }
  })

  // 暗化非路径站点
  for (const [id, marker] of Object.entries(stationMarkers)) {
    const onRoute = routeSet.has(id)
    if (!onRoute) {
      marker.setStyle({ opacity: rs.value.dimStationOpacity, fillOpacity: rs.value.dimStationOpacity, radius: rs.value.dimStationRadius })
      const tooltip = marker.getTooltip()
      if (tooltip) { const el = tooltip.getElement(); if (el) el.style.opacity = '0.08' }
    }
  }

  // 绘制路径线
  const coords = route
    .map(id => allStationsMap.value[id])
    .filter(s => s && s.latitude != null && s.longitude != null)
    .map(s => [Number(s.latitude), Number(s.longitude)])

  if (coords.length >= 2) {
    L.polyline(coords, {
      color: rs.value.routeColor, weight: rs.value.glowWeight, opacity: rs.value.glowOpacity,
      lineJoin: 'round', lineCap: 'round',
    }).addTo(routeGroup)
    L.polyline(coords, {
      color: rs.value.routeColor, weight: rs.value.lineWeight, opacity: rs.value.lineOpacity,
      lineJoin: 'round', lineCap: 'round',
      dashArray: rs.value.dashArray,
      className: 'route-animated-line',
    }).addTo(routeGroup)
  }

  // 高亮路径上的站点
  for (const id of route) {
    const marker = stationMarkers[id]
    if (!marker) continue
    const isEndpoint = id === route[0] || id === route[route.length - 1]
    marker.setStyle({
      fillColor: isEndpoint ? rs.value.routeColor : '#fff',
      color: rs.value.routeColor,
      weight: isEndpoint ? rs.value.endpointWeight : rs.value.midpointWeight,
      radius: isEndpoint ? rs.value.endpointRadius : rs.value.midpointRadius,
      fillOpacity: 1,
      opacity: 1,
    })
    const tooltip = marker.getTooltip()
    if (tooltip) { const el = tooltip.getElement(); if (el) el.style.opacity = '1' }
  }
}

function onStationClick(stationId) {
  emit('station-click', stationId)
}

// ── 线路高亮 ──
function onLegendClick(name) {
  if (pathMode.value) return
  if (selectedLine.value === name) {
    selectedLine.value = null
    showAllLines()
  } else {
    selectedLine.value = name
    highlightLine(name)
  }
}

function highlightLine(name) {
  if (!lineGroup) return
  lineGroup.eachLayer(layer => {
    if (layer._lineName) {
      if (layer._lineName === name) {
        layer.setStyle({ opacity: 1, weight: 6 })
      } else {
        layer.setStyle({ opacity: 0.1, weight: 3 })
      }
    }
  })
  // 站点和标签透明度
  for (const [id, marker] of Object.entries(stationMarkers)) {
    const s = allStationsMap.value[id]
    if (!s) continue
    const belongs = (s._lineNames || []).includes(name)
    marker.setStyle({ opacity: belongs ? 1 : 0.15, fillOpacity: belongs ? 1 : 0.15 })
    // 控制标签可见性
    const tooltip = marker.getTooltip()
    if (tooltip) {
      const el = tooltip.getElement()
      if (el) el.style.opacity = belongs ? '1' : '0.1'
    }
  }
}

function showAllLines() {
  if (!lineGroup) return
  lineGroup.eachLayer(layer => {
    if (layer._lineName) {
      layer.setStyle({ opacity: layer._isGlow ? 0.15 : 0.9, weight: layer._isGlow ? 10 : 5 })
    }
  })
  for (const [id, marker] of Object.entries(stationMarkers)) {
    const s = allStationsMap.value[id]
    if (!s) continue
    const isTransfer = s.isTransfer === 1
    marker.setStyle({
      opacity: 1, fillOpacity: 1,
      weight: isTransfer ? 2.5 : 2,
      radius: isTransfer ? 7 : 4.5,
      color: isTransfer ? '#303133' : (s._lineColor || '#409EFF'),
    })
    // 恢复标签可见性
    const tooltip = marker.getTooltip()
    if (tooltip) {
      const el = tooltip.getElement()
      if (el) el.style.opacity = '1'
    }
  }
}

// ── 缩放时标签渐变 ──
function updateLabelsForZoom() {
  if (!map) return
  const zoom = map.getZoom()
  const cfg = lc.value

  for (const [, marker] of Object.entries(stationMarkers)) {
    const tooltip = marker.getTooltip()
    if (!tooltip) continue
    const el = tooltip.getElement()
    if (!el) continue

    if (zoom <= cfg.hideZoom) {
      el.style.opacity = '0'
      el.style.pointerEvents = 'none'
    } else if (zoom < cfg.shrinkStartZoom) {
      const progress = (zoom - cfg.hideZoom) / (cfg.shrinkStartZoom - cfg.hideZoom)
      const fontSize = cfg.minFontSize + (cfg.baseFontSize - cfg.minFontSize) * progress
      el.style.fontSize = fontSize + 'px'
      el.style.opacity = String(Math.max(0.3, progress))
      el.style.pointerEvents = ''
    } else {
      el.style.fontSize = cfg.baseFontSize + 'px'
      el.style.opacity = '1'
      el.style.pointerEvents = ''
    }
  }
}

// ── 地图初始化（白色背景，无瓦片）──
function initMap() {
  if (!mapRef.value) return
  if (map) { map.remove(); map = null }

  map = L.map(mapRef.value, {
    zoomControl: false,
    attributionControl: false,
    zoomSnap: 0.25,
    zoomDelta: 0.5,
  })

  // 不加载瓦片图层，使用CSS白色背景

  L.control.zoom({ position: 'bottomright' }).addTo(map)
  lineGroup = L.layerGroup().addTo(map)
  markerGroup = L.layerGroup().addTo(map)
  routeGroup = L.layerGroup().addTo(map)
  map.on('zoomend', updateLabelsForZoom)
}

function renderMap() {
  if (!map) return
  map.invalidateSize()
  lineGroup.clearLayers()
  markerGroup.clearLayers()
  routeGroup.clearLayers()
  Object.keys(stationMarkers).forEach(k => delete stationMarkers[k])

  const allCoords = []
  const legend = []
  const drawnStations = new Set()

  if (props.lines.length > 0) {
    for (const line of props.lines) {
      if (line.connected === false) {
        for (const s of (line.stations || [])) {
          if (s.longitude != null && s.latitude != null && !isNaN(Number(s.longitude))) {
            allCoords.push([Number(s.latitude), Number(s.longitude)])
          }
        }
        legend.push({ name: line.name || '未知线路', color: line.color || '#409EFF', stationCount: line.stations?.length || 0 })
        continue
      }

      if (!line.stations || line.stations.length < 2) continue
      const coords = line.stations
        .filter(s => s.longitude != null && s.longitude !== '' && s.latitude != null && s.latitude !== '' && !isNaN(Number(s.longitude)) && !isNaN(Number(s.latitude)))
        .map(s => [Number(s.latitude), Number(s.longitude)])

      if (coords.length < 2) continue

      // 光晕层
      const glowLine = L.polyline(coords, {
        color: line.color || '#409EFF', weight: 10, opacity: 0.15,
        lineJoin: 'round', lineCap: 'round',
      })
      glowLine._lineName = line.name
      glowLine._isGlow = true
      glowLine.addTo(lineGroup)

      // 主线
      const mainLine = L.polyline(coords, {
        color: line.color || '#409EFF', weight: 5, opacity: 0.9,
        lineJoin: 'round', lineCap: 'round',
      })
      mainLine._lineName = line.name
      mainLine._isGlow = false
      mainLine.addTo(lineGroup)

      allCoords.push(...coords)
      legend.push({ name: line.name || '未知线路', color: line.color || '#409EFF', stationCount: line.stations?.length || 0 })
    }
  }

  // 绘制站点
  const stationSource = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []).map(s => ({ ...s, lineColor: l.color })))
    : props.stations

  for (const station of stationSource) {
    if (station.longitude == null || station.latitude == null || isNaN(Number(station.longitude))) continue
    const sid = String(station.id)
    if (drawnStations.has(sid)) continue
    drawnStations.add(sid)

    const isTransfer = station.isTransfer === 1
    const lat = Number(station.latitude)
    const lng = Number(station.longitude)
    const color = station.lineColor || '#409EFF'

    // 统一站点样式：换乘站稍大，普通站统一大小
    const marker = L.circleMarker([lat, lng], {
      radius: isTransfer ? 7 : 4.5,
      fillColor: '#ffffff',
      color: isTransfer ? '#303133' : color,
      weight: isTransfer ? 2.5 : 2,
      opacity: 1,
      fillOpacity: 1,
    })

    // 所有站点都显示永久标签，统一字体和位置
    marker.bindTooltip(station.stationName, {
      permanent: true,
      direction: 'bottom',
      offset: [0, isTransfer ? 10 : 7],
      className: 'station-label-unified',
    })

    marker.on('click', () => onStationClick(sid))
    marker.addTo(markerGroup)
    stationMarkers[sid] = marker

    allCoords.push([lat, lng])
  }

  // 图例
  const seen = new Set()
  const uniqueLegend = []
  for (const item of legend) {
    if (!seen.has(item.name)) { seen.add(item.name); uniqueLegend.push(item) }
  }
  legendItems.value = uniqueLegend

  if (props.fitBounds && allCoords.length > 0) {
    map.fitBounds(allCoords, { padding: [40, 40], maxZoom: 15 })
    const bounds = L.latLngBounds(allCoords)
    const sw = bounds.getSouthWest()
    const ne = bounds.getNorthEast()
    const latPad = (ne.lat - sw.lat) * 0.3 || 0.02
    const lngPad = (ne.lng - sw.lng) * 0.3 || 0.02
    map.setMaxBounds([
      [sw.lat - latPad, sw.lng - lngPad],
      [ne.lat + latPad, ne.lng + lngPad],
    ])
  } else if (allCoords.length === 0) {
    map.setView([39.9, 116.4], 11)
  }

  nextTick(() => updateLabelsForZoom())
}

onMounted(() => { initMap(); nextTick(() => renderMap()) })
watch(() => [props.stations, props.lines], () => {
  exitPathMode()
  selectedLine.value = null
  nextTick(() => renderMap())
}, { deep: true })
onBeforeUnmount(() => { if (map) { map.off('zoomend', updateLabelsForZoom); map.remove(); map = null } })
</script>

<style scoped>
.metro-map-wrapper {
  position: relative; border-radius: 14px; overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); border: 1px solid #e4e7ed;
}
.metro-map { width: 100%; height: 100%; background: #fff; }

/* ── 路径模式提示条 ── */
.path-mode-bar {
  position: absolute; top: 12px; left: 50%; transform: translateX(-50%);
  z-index: 1000;
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
  position: absolute; top: 12px; right: 12px; z-index: 1000;
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
  position: absolute; bottom: 16px; left: 16px; z-index: 1000;
  background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(12px);
  border-radius: 12px; padding: 12px 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1); border: 1px solid rgba(255, 255, 255, 0.8); min-width: 140px;
}
.legend-header {
  display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #303133;
  margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0;
}
.legend-icon { font-size: 15px; }
.legend-list { display: flex; flex-direction: column; gap: 6px; }
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
  z-index: 999;
  background: rgba(48, 49, 51, 0.8); color: rgba(255, 255, 255, 0.85);
  padding: 5px 16px; border-radius: 14px;
  font-size: 11px; white-space: nowrap;
  pointer-events: none;
}

/* ── 统计 ── */
.map-stats {
  position: absolute; top: 12px; left: 12px; z-index: 1000;
  background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(10px);
  border-radius: 20px; padding: 6px 14px; font-size: 12px; color: #606266;
  display: flex; align-items: center; gap: 6px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06); border: 1px solid rgba(255, 255, 255, 0.8);
}
.stats-dot { width: 6px; height: 6px; border-radius: 50%; background: #67c23a; animation: pulse 2s infinite; }
.stats-divider { color: #dcdfe6; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>

<style>
/* 路径动画 */
.route-animated-line {
  animation: routeFlow 1.2s linear infinite;
}
@keyframes routeFlow {
  to { stroke-dashoffset: -18; }
}

/* 统一站点标签样式 */
.station-label-unified {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 0 !important;
  color: #3a3a4a !important;
  font-family: 'PingFang SC', 'Noto Sans SC', 'Microsoft YaHei', -apple-system, sans-serif !important;
  font-size: 11px;
  font-weight: 500 !important;
  letter-spacing: 0.3px !important;
  white-space: nowrap !important;
  text-shadow: -1px -1px 0 #fff, 1px -1px 0 #fff, -1px 1px 0 #fff, 1px 1px 0 #fff, 0 0 3px #fff !important;
}
.station-label-unified::before {
  display: none !important;
}
.station-label-unified .leaflet-tooltip-tip {
  display: none !important;
}

/* 缩放控件 */
.leaflet-control-zoom a {
  width: 32px !important; height: 32px !important; line-height: 32px !important; font-size: 16px !important;
  border-radius: 8px !important; border: none !important; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1) !important; color: #303133 !important;
}
.leaflet-control-zoom a:hover { background: #f0f2f5 !important; }
.leaflet-control-zoom-in { border-bottom: 1px solid #eee !important; margin-bottom: 2px !important; }
</style>
