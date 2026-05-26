<template>
  <div class="metro-map-wrapper" :style="{ height }">
    <div ref="mapRef" class="metro-map"></div>

    <div v-if="showLegend && legendItems.length" class="map-legend">
      <div class="legend-header">
        <span class="legend-icon">🚇</span>
        <span>线路图例</span>
      </div>
      <div class="legend-list">
        <div v-for="item in legendItems" :key="item.name" class="legend-item">
          <span class="legend-line" :style="{ background: item.color }"></span>
          <span class="legend-name">{{ item.name }}</span>
          <span v-if="item.stationCount" class="legend-count">{{ item.stationCount }}站</span>
        </div>
      </div>
    </div>

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
})

const mapRef = ref(null)
const legendItems = ref([])
let map = null
let markerGroup = null
let lineGroup = null

const totalStations = computed(() => {
  const ids = new Set()
  const source = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []))
    : props.stations
  for (const s of source) {
    if (s.longitude != null && s.latitude != null) ids.add(String(s.id))
  }
  return ids.size
})

const transferCount = computed(() => {
  const ids = new Set()
  let count = 0
  const source = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []))
    : props.stations
  for (const s of source) {
    const sid = String(s.id)
    if (s.isTransfer === 1 && s.longitude != null && !ids.has(sid)) {
      ids.add(sid)
      count++
    }
  }
  return count
})

function initMap() {
  if (!mapRef.value) return
  if (map) { map.remove(); map = null }

  map = L.map(mapRef.value, {
    zoomControl: false,
    attributionControl: false,
  })

  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: '1234',
    maxZoom: 18,
  }).addTo(map)

  L.control.zoom({ position: 'bottomright' }).addTo(map)
  markerGroup = L.layerGroup().addTo(map)
  lineGroup = L.layerGroup().addTo(map)
}

function renderMap() {
  if (!map) return
  markerGroup.clearLayers()
  lineGroup.clearLayers()

  const allCoords = []
  const legend = []

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

      L.polyline(coords, { color: line.color || '#409EFF', weight: 10, opacity: 0.15, lineJoin: 'round', lineCap: 'round' }).addTo(lineGroup)
      L.polyline(coords, { color: line.color || '#409EFF', weight: 5, opacity: 0.9, lineJoin: 'round', lineCap: 'round' }).addTo(lineGroup)

      allCoords.push(...coords)
      legend.push({ name: line.name || '未知线路', color: line.color || '#409EFF', stationCount: line.stations?.length || 0 })
    }
  }

  const drawnStations = new Set()
  const stationSource = props.lines.length > 0
    ? props.lines.flatMap(l => (l.stations || []).map(s => ({ ...s, lineColor: l.color })))
    : props.stations

  const normalStations = []
  const transferStations = []
  for (const station of stationSource) {
    if (station.longitude == null || station.longitude === '' || station.latitude == null || station.latitude === '' || isNaN(Number(station.longitude)) || isNaN(Number(station.latitude))) continue
    const sid = String(station.id)
    if (drawnStations.has(sid)) continue
    drawnStations.add(sid)
    if (station.isTransfer === 1) transferStations.push(station)
    else normalStations.push(station)
  }

  for (const station of normalStations) {
    drawStationMarker(station, false)
    allCoords.push([Number(station.latitude), Number(station.longitude)])
  }
  for (const station of transferStations) {
    drawStationMarker(station, true)
    allCoords.push([Number(station.latitude), Number(station.longitude)])
  }

  if (props.lines.length > 0) {
    const seen = new Set()
    const uniqueLegend = []
    for (const item of legend) {
      if (!seen.has(item.name)) { seen.add(item.name); uniqueLegend.push(item) }
    }
    legendItems.value = uniqueLegend
  } else {
    legendItems.value = []
  }

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
}

function drawStationMarker(station, isTransfer) {
  const lat = Number(station.latitude)
  const lng = Number(station.longitude)
  const color = station.lineColor || '#409EFF'

  let marker
  if (isTransfer) {
    marker = L.circleMarker([lat, lng], { radius: 8, fillColor: '#ffffff', color: '#303133', weight: 3, opacity: 1, fillOpacity: 1 })
  } else {
    marker = L.circleMarker([lat, lng], { radius: 5, fillColor: '#ffffff', color, weight: 2.5, opacity: 1, fillOpacity: 1 })
  }

  const tooltipText = station.stationName + (station.stationNameEn ? ` (${station.stationNameEn})` : '')
  marker.bindTooltip(tooltipText, {
    permanent: isTransfer,
    direction: 'top',
    offset: [0, isTransfer ? -12 : -8],
    className: isTransfer ? 'metro-transfer-tooltip' : 'metro-station-tooltip',
  })

  const lineNames = Array.isArray(station.lineNames) ? station.lineNames.join('、') : (station.lineNames || '')
  const typeMap = { 0: '地下站', 1: '地面站', 2: '高架站' }
  const typeText = typeMap[station.stationType] || ''

  marker.bindPopup(
    `<div class="metro-popup">` +
      `<div class="metro-popup-title">${station.stationName}</div>` +
      (station.stationNameEn ? `<div class="metro-popup-en">${station.stationNameEn}</div>` : '') +
      `<div class="metro-popup-info">` +
        (lineNames ? `<div><span class="metro-popup-label">线路</span>${lineNames}</div>` : '') +
        (isTransfer ? `<div><span class="metro-popup-tag transfer">换乘站</span></div>` : '') +
        (typeText ? `<div><span class="metro-popup-label">类型</span>${typeText}</div>` : '') +
      `</div>` +
    `</div>`,
    { className: 'metro-popup-wrapper' }
  )

  marker.addTo(markerGroup)
}

onMounted(() => { initMap(); nextTick(() => renderMap()) })
watch(() => [props.stations, props.lines], () => { nextTick(() => renderMap()) }, { deep: true })
onBeforeUnmount(() => { if (map) { map.remove(); map = null } })
</script>

<style scoped>
.metro-map-wrapper {
  position: relative; border-radius: 14px; overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08); border: 1px solid #e4e7ed;
}
.metro-map { width: 100%; height: 100%; }
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
.legend-item { display: flex; align-items: center; gap: 10px; }
.legend-line { width: 20px; height: 4px; border-radius: 2px; flex-shrink: 0; }
.legend-name { font-size: 12px; color: #303133; font-weight: 500; flex: 1; }
.legend-count { font-size: 11px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 4px; }
.map-stats {
  position: absolute; top: 12px; right: 12px; z-index: 1000;
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
.metro-station-tooltip {
  font-size: 12px !important; font-weight: 500 !important; padding: 4px 10px !important;
  border-radius: 6px !important; background: rgba(48, 49, 51, 0.9) !important; color: #fff !important;
  border: none !important; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15) !important;
}
.metro-station-tooltip::before { border-top-color: rgba(48, 49, 51, 0.9) !important; }
.metro-transfer-tooltip {
  font-size: 13px !important; font-weight: 700 !important; padding: 5px 12px !important;
  border-radius: 6px !important; background: #303133 !important; color: #fff !important;
  border: none !important; box-shadow: 0 3px 12px rgba(0, 0, 0, 0.2) !important; letter-spacing: 0.5px;
}
.metro-transfer-tooltip::before { border-top-color: #303133 !important; }
.metro-popup-wrapper .leaflet-popup-content-wrapper {
  border-radius: 12px !important; box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12) !important; padding: 0 !important;
}
.metro-popup-wrapper .leaflet-popup-content { margin: 0 !important; min-width: 160px !important; }
.metro-popup-wrapper .leaflet-popup-tip { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08) !important; }
.metro-popup { padding: 14px 16px; }
.metro-popup-title { font-size: 15px; font-weight: 700; color: #303133; margin-bottom: 2px; }
.metro-popup-en { font-size: 12px; color: #909399; margin-bottom: 8px; }
.metro-popup-info { font-size: 12px; color: #606266; line-height: 1.8; }
.metro-popup-label { display: inline-block; width: 32px; color: #909399; margin-right: 6px; }
.metro-popup-tag { display: inline-block; font-size: 11px; padding: 1px 8px; border-radius: 4px; font-weight: 500; }
.metro-popup-tag.transfer { background: #fdf6ec; color: #e6a23c; }
.leaflet-control-zoom a {
  width: 32px !important; height: 32px !important; line-height: 32px !important; font-size: 16px !important;
  border-radius: 8px !important; border: none !important; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1) !important; color: #303133 !important;
}
.leaflet-control-zoom a:hover { background: #f0f2f5 !important; }
.leaflet-control-zoom-in { border-bottom: 1px solid #eee !important; margin-bottom: 2px !important; }
</style>
