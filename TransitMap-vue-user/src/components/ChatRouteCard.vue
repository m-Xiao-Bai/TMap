<template>
  <div class="route-card">
    <div class="route-header">
      <div class="station-start">
        <span class="dot start-dot"></span>
        <span class="station-name">{{ route.startStationName }}</span>
      </div>
      <div class="route-arrow">
        <svg viewBox="0 0 24 24" width="16"><path d="M16.01 11H4v2h12.01v3L20 12l-3.99-4v3z" fill="currentColor"/></svg>
      </div>
      <div class="station-end">
        <span class="dot end-dot"></span>
        <span class="station-name">{{ route.endStationName }}</span>
      </div>
    </div>

    <div class="route-meta">
      <span class="meta-item">
        <svg viewBox="0 0 24 24" width="14"><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13H11v6l5.2 3.2.8-1.3-4.5-2.7V7z" fill="currentColor"/></svg>
        约 {{ route.durationMinutes }} 分钟
      </span>
      <span class="meta-item">
        <svg viewBox="0 0 24 24" width="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" fill="currentColor"/></svg>
        {{ route.distanceKm }} 公里
      </span>
      <span class="meta-item">
        共 {{ route.stationCount }} 站
      </span>
      <span class="meta-item price">
        ¥{{ route.price }}
      </span>
    </div>

    <!-- 乘车路径分段（核心信息：哪条线、坐几站、在哪换乘） -->
    <div v-if="segments.length" class="segments">
      <div class="segments-title">🚇 乘车路径</div>
      <div class="segments-list">
        <template v-for="(seg, i) in segments" :key="i">
          <div class="segment-row">
            <span class="line-chip" :style="lineChipStyle(seg.lineColor)">
              {{ seg.lineName }}
            </span>
            <div class="seg-detail">
              <div class="seg-route">
                <span class="seg-from">{{ seg.startName }}</span>
                <svg viewBox="0 0 24 24" width="12" class="seg-arrow">
                  <path d="M16.01 11H4v2h12.01v3L20 12l-3.99-4v3z" fill="currentColor"/>
                </svg>
                <span class="seg-to">{{ seg.endName }}</span>
              </div>
              <div class="seg-stops">乘 {{ seg.stopCount }} 站</div>
            </div>
          </div>
          <!-- 换乘标记（在两段之间） -->
          <div v-if="i < segments.length - 1" class="transfer-marker">
            <span class="transfer-icon">🔄</span>
            在 <strong>{{ segments[i + 1].startName }}</strong> 换乘
          </div>
        </template>
      </div>
    </div>

    <!-- 无换乘时的简洁提示 -->
    <div v-if="!route.transfers?.length && segments.length === 1" class="no-transfer-tip">
      ✨ 全程无需换乘
    </div>

    <!-- 途经站点（可折叠） -->
    <div v-if="route.stationNames?.length" class="stations-section">
      <button class="toggle-stations" @click="showStations = !showStations">
        {{ showStations ? '收起' : '展开' }}全部 {{ route.stationCount + 1 }} 站
        <svg :class="{ rotated: showStations }" viewBox="0 0 24 24" width="12"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z" fill="currentColor"/></svg>
      </button>
      <div v-if="showStations" class="station-list">
        <span v-for="(name, i) in route.stationNames" :key="i" class="station-tag">
          <span v-if="i > 0" class="connector">→</span>
          {{ name }}
        </span>
      </div>
    </div>

    <button class="order-btn" @click="$emit('order')">
      <svg viewBox="0 0 24 24" width="16"><path d="M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z" fill="currentColor"/></svg>
      立即下单
    </button>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  route: { type: Object, required: true }
})

defineEmits(['order'])

const showStations = ref(false)

/**
 * 把整条路线按换乘点切段
 * 返回 [{lineName, lineColor, startName, endName, stopCount}]
 */
const segments = computed(() => {
  const route = props.route || {}
  const names = route.stationNames || []
  const transfers = route.transfers || []
  if (!names.length) return []

  // 建 lineName → lineColor 索引（如果后端给了 stations 详情）
  const colorMap = {}
  if (Array.isArray(route.stations)) {
    for (const s of route.stations) {
      if (s.lineName && s.lineColor) colorMap[s.lineName] = s.lineColor
    }
  }

  const out = []
  // 无换乘：单段
  if (!transfers.length) {
    const line = (route.lineNames && route.lineNames[0]) || '线路'
    out.push({
      lineName: line,
      lineColor: colorMap[line] || '#1a73e8',
      startName: names[0],
      endName: names[names.length - 1],
      stopCount: names.length - 1
    })
    return out
  }

  // 有换乘：按 transfer 站名切段
  let segStart = 0
  let currentLine = transfers[0].fromLineName
  for (const tf of transfers) {
    const idx = names.indexOf(tf.stationName, segStart)
    if (idx < 0) continue
    const stops = idx - segStart
    if (stops > 0) {
      out.push({
        lineName: currentLine,
        lineColor: colorMap[currentLine] || autoColor(out.length),
        startName: names[segStart],
        endName: names[idx],
        stopCount: stops
      })
    }
    segStart = idx
    currentLine = tf.toLineName
  }
  const lastStops = names.length - 1 - segStart
  if (lastStops > 0) {
    out.push({
      lineName: currentLine,
      lineColor: colorMap[currentLine] || autoColor(out.length),
      startName: names[segStart],
      endName: names[names.length - 1],
      stopCount: lastStops
    })
  }
  return out
})

// 后端没给 lineColor 时按段序号自动配色
const PALETTE = ['#1a73e8', '#ea580c', '#16a34a', '#9333ea', '#dc2626', '#0891b2']
function autoColor(i) {
  return PALETTE[i % PALETTE.length]
}

function lineChipStyle(color) {
  const c = color || '#1a73e8'
  return {
    background: c,
    color: '#fff'
  }
}
</script>

<style scoped>
.route-card {
  margin: 8px 0 8px 40px;
  padding: 16px;
  background: #fff;
  border: 1px solid #e8eaed;
  border-radius: 12px;
  max-width: 400px;
  container-type: inline-size;
}

@container (max-width: 320px) {
  .route-header { flex-wrap: wrap; }
  .route-meta { gap: 8px; font-size: 11px; }
}

.route-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.station-start,
.station-end {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.start-dot { background: #4CAF50; }
.end-dot { background: #F44336; }

.station-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.route-arrow {
  color: #999;
  flex-shrink: 0;
}

.route-meta {
  display: flex;
  gap: 14px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e8eaed;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.meta-item.price {
  font-weight: 700;
  color: #1a73e8;
  font-size: 14px;
}

/* 乘车路径分段区域 */
.segments {
  margin-bottom: 10px;
}

.segments-title {
  font-size: 12px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8px;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.segment-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 8px;
  border-left: 3px solid transparent;
}

.line-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
}

.seg-detail {
  flex: 1;
  min-width: 0;
}

.seg-route {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #1d1d1f;
  font-weight: 500;
}

.seg-from, .seg-to {
  white-space: nowrap;
}

.seg-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

.seg-stops {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.transfer-marker {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 2px dashed #ffa940;
  font-size: 12px;
  color: #d97706;
  padding: 4px 0 4px 12px;
}

.transfer-icon {
  font-size: 14px;
}

.no-transfer-tip {
  margin-bottom: 10px;
  padding: 6px 10px;
  background: #f0f9eb;
  color: #67c23a;
  border-radius: 6px;
  font-size: 12px;
  text-align: center;
}

.stations-section {
  margin-top: 4px;
}

.toggle-stations {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #1a73e8;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
}

.toggle-stations svg {
  transition: transform 0.2s;
}

.toggle-stations svg.rotated {
  transform: rotate(180deg);
}

.station-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.station-tag {
  font-size: 11px;
  color: #606266;
}

.connector {
  color: #ccc;
  margin: 0 2px;
}

.order-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 10px;
  margin-top: 12px;
  background: #1a73e8;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.order-btn:hover {
  background: #1557b0;
}
</style>
