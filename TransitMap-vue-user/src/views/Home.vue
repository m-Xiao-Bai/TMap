<template>
  <div class="ticket-page" @click="showCityDropdown = false">
    <!-- 顶部导航 -->
    <header class="nav-bar">
      <div class="nav-inner">
        <div class="nav-left">
          <div class="nav-logo">
            <el-icon :size="20" color="#fff"><Aim /></el-icon>
          </div>
          <span class="nav-title">地铁购票系统</span>
        </div>
        <div class="nav-center">
          <div class="city-selector" @click.stop>
            <el-icon :size="14"><Location /></el-icon>
            <div class="city-display" @click="toggleCityDropdown">
              <span class="city-display-text" :class="{ placeholder: !selectedCityId }">{{ selectedCityName || '选择城市' }}</span>
              <el-icon class="city-display-arrow" :class="{ open: showCityDropdown }"><ArrowDown /></el-icon>
            </div>
            <!-- 城市下拉 -->
            <div v-if="showCityDropdown" class="city-dropdown">
              <div class="cd-country-tabs">
                <span
                  class="cd-tab"
                  :class="{ active: !filterCountryId }"
                  @click="filterCountryId = null"
                >全部</span>
                <span
                  v-for="c in countries" :key="c.id"
                  class="cd-tab"
                  :class="{ active: filterCountryId === c.id }"
                  @click="filterCountryId = c.id"
                >{{ c.countryName }}</span>
              </div>
              <div class="cd-search">
                <el-input v-model="citySearchText" placeholder="搜索城市..." clearable size="small" prefix-icon="Search" />
              </div>
              <div class="cd-city-list">
                <div
                  v-for="c in filteredCityList"
                  :key="c.id"
                  class="cd-city-item"
                  :class="{ active: selectedCityId === c.id }"
                  @click="selectCity(c.id)"
                >
                  <span class="cd-city-name">{{ c.cityName }}</span>
                  <span class="cd-city-country">{{ c._countryName }}</span>
                  <span class="cd-city-count">{{ c.metroLineCount }}条线</span>
                </div>
                <div v-if="!filteredCityList.length" class="cd-empty">暂无匹配城市</div>
              </div>
            </div>
          </div>
        </div>
        <div class="nav-right">
          <button class="nav-theme-btn" :title="isDark() ? '切换浅色' : '切换暗黑'" @click="toggle">
            <el-icon :size="18">
              <Sunny v-if="isDark()" />
              <Moon v-else />
            </el-icon>
          </button>
          <template v-if="userStore.isLoggedIn()">
            <el-badge v-if="msgUnreadCount > 0" :value="msgUnreadCount" :max="99" class="nav-msg-badge">
              <el-icon class="nav-msg-icon" @click="$router.push('/messages')"><Bell /></el-icon>
            </el-badge>
            <el-icon v-else class="nav-msg-icon" @click="$router.push('/messages')"><Bell /></el-icon>
            <el-dropdown @command="handleCommand">
              <span class="user-info">
                <el-avatar v-if="userStore.avatar" :size="28" :src="avatarUrl" shape="square" />
                <el-icon v-else :size="18"><UserFilled /></el-icon>
                <span class="user-name">{{ userStore.username }}</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                  <el-dropdown-item command="tickets">我的订单</el-dropdown-item>
                  <el-dropdown-item command="messages">消息中心</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button text @click="$router.push('/login')" class="nav-login-btn">登录</el-button>
            <el-button type="primary" round @click="$router.push('/register')" size="default">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 主体内容 -->
    <div class="main-content">
      <!-- 左侧：购票面板 -->
      <div class="ticket-panel">
        <!-- 购票卡片 -->
        <div class="ticket-card">
          <div class="ticket-card-header">
            <div class="tch-title">
              <svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/></svg>
              <span>选择行程</span>
            </div>
            <div class="tch-hint" v-if="!pfStartId && !pfEndId">请在下方选择出发站和到达站，也可直接在地图上点选</div>
          </div>

          <div class="station-selector">
            <!-- 起点 -->
            <div class="ss-row ss-start" @click="togglePfPicker('start')">
              <div class="ss-dot-wrap">
                <span class="ss-dot ss-dot-start"></span>
                <span class="ss-line-connector"></span>
              </div>
              <div class="ss-info">
                <span class="ss-label">出发站</span>
                <span class="ss-station" :class="{ filled: pfStartId }">{{ pfStartName || '点击选择出发站点' }}</span>
              </div>
              <el-icon class="ss-arrow"><ArrowRight /></el-icon>
            </div>

            <!-- 交换按钮 -->
            <div class="ss-swap-row">
              <div class="ss-swap-line"></div>
              <button class="ss-swap-btn" @click="swapPf" title="交换起终点">
                <svg viewBox="0 0 24 24" width="16" height="16"><path d="M7 16l-4-4 4-4M17 8l4 4-4 4" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
              </button>
            </div>

            <!-- 终点 -->
            <div class="ss-row ss-end" @click="togglePfPicker('end')">
              <div class="ss-dot-wrap">
                <span class="ss-dot ss-dot-end"></span>
              </div>
              <div class="ss-info">
                <span class="ss-label">到达站</span>
                <span class="ss-station" :class="{ filled: pfEndId }">{{ pfEndName || '点击选择到达站点' }}</span>
              </div>
              <el-icon class="ss-arrow"><ArrowRight /></el-icon>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="ticket-actions">
            <el-button
              v-if="pfStartId || pfEndId"
              text
              type="danger"
              @click="clearPf"
              size="small"
            >
              <el-icon><Close /></el-icon>
              清除选择
            </el-button>
            <div class="spacer"></div>
            <el-button
              type="primary"
              round
              size="large"
              :disabled="!pfStartId || !pfEndId || pfStartId === pfEndId"
              @click="searchRoute"
              class="search-btn"
            >
              <el-icon><Search /></el-icon>
              查询路线
            </el-button>
          </div>
        </div>

        <!-- 站点选择下拉 -->
        <div v-if="showPfStart || showPfEnd" class="station-picker-dropdown" @click.stop>
          <div class="spd-header">
            <span class="spd-title">{{ showPfStart ? '选择出发站' : '选择到达站' }}</span>
            <button class="spd-close" @click="closePfDropdowns">
              <svg viewBox="0 0 24 24" width="16" height="16"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg>
            </button>
          </div>
          <div class="spd-lines">
            <span
              v-for="line in cityAllMapLines" :key="'spd-'+line.id"
              class="spd-line-tag"
              :class="{ active: (showPfStart ? pfStartLineId : pfEndLineId) === line.id }"
              :style="(showPfStart ? pfStartLineId : pfEndLineId) === line.id ? { background: line.color, color: '#fff', borderColor: line.color } : { borderColor: line.color + '40' }"
              @click="selectPickerLine(line.id)"
            >
              <span class="spd-line-dot" :style="{ background: line.color }"></span>
              {{ line.name }}
            </span>
          </div>
          <div class="spd-stations" v-if="(showPfStart ? pfStartLineId : pfEndLineId)">
            <div
              v-for="(s, idx) in getPfLineStations(showPfStart ? pfStartLineId : pfEndLineId)"
              :key="'spd-s-'+s.id"
              class="spd-station"
              :class="{ selected: (showPfStart ? pfStartId : pfEndId) === String(s.id) }"
              @click="selectPfStation(showPfStart ? 'start' : 'end', String(s.id))"
            >
              <span class="spd-station-num" :style="{ background: getPfLineColor(showPfStart ? pfStartLineId : pfEndLineId) }">{{ idx + 1 }}</span>
              <span class="spd-station-name">{{ s.stationName }}</span>
              <span v-if="s.isTransfer === 1" class="spd-station-transfer">换乘</span>
            </div>
          </div>
        </div>

        <!-- 路线结果卡片 -->
        <div v-if="routeInfo" class="route-card">
          <div class="route-card-header">
            <div class="rch-title">
              <svg viewBox="0 0 24 24" width="18" height="18"><path d="M3 12h4l3-9 4 18 3-9h4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
              <span>路线方案</span>
            </div>
            <button class="rch-close" @click="clearRouteResult">
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" fill="none"/></svg>
            </button>
          </div>

          <!-- 行程概览 -->
          <div class="route-overview">
            <div class="ro-endpoint">
              <span class="ro-dot ro-dot-start"></span>
              <span class="ro-name">{{ routeInfo.startName }}</span>
            </div>
            <div class="ro-middle">
              <div class="ro-line"></div>
              <div class="ro-badge">
                <span class="ro-stops">{{ routeInfo.totalStops }}</span>
                <span class="ro-stops-label">站</span>
              </div>
              <div class="ro-line"></div>
            </div>
            <div class="ro-endpoint">
              <span class="ro-dot ro-dot-end"></span>
              <span class="ro-name">{{ routeInfo.endName }}</span>
            </div>
          </div>

          <!-- 旅程信息 -->
          <div class="route-meta">
            <div class="rm-item">
              <span class="rm-icon">
                <svg viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 6v6l4 2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
              </span>
              <span class="rm-text">约 {{ estimatedTime }} 分钟</span>
            </div>
            <div class="rm-item">
              <span class="rm-icon">
                <svg viewBox="0 0 24 24" width="16" height="16"><path d="M12 2v20M2 12h20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              </span>
              <span class="rm-text">约 {{ estimatedDistance }} 公里</span>
            </div>
            <div class="rm-item rm-price">
              <span class="rm-icon">
                <svg viewBox="0 0 24 24" width="16" height="16"><rect x="2" y="4" width="20" height="16" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M2 10h20" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              </span>
              <span class="rm-text rm-price-text">¥ {{ estimatedPrice }}</span>
            </div>
          </div>

          <!-- 站点列表 -->
          <div class="route-stations">
            <div class="rs-title">途经站点</div>
            <div class="rs-list">
              <div v-for="(sid, idx) in pathRoute" :key="sid" class="rs-item" :class="{ start: idx === 0, end: idx === pathRoute.length - 1 }">
                <div class="rs-item-left">
                  <span class="rs-dot" :class="{ start: idx === 0, end: idx === pathRoute.length - 1 }"></span>
                  <span v-if="idx < pathRoute.length - 1" class="rs-connector"></span>
                </div>
                <span class="rs-name">{{ getStationName(sid) }}</span>
                <span v-if="isTransferStation(sid)" class="rs-transfer-tag">换乘</span>
                <span class="rs-line-indicator" :style="{ background: getStationLineColor(sid) }"></span>
              </div>
            </div>
          </div>

          <!-- 购票按钮 -->
          <div class="route-buy">
            <div class="rb-price-row">
              <span class="rb-label">票价</span>
              <span class="rb-price">¥ {{ estimatedPrice }}</span>
            </div>
            <el-button type="primary" size="large" round class="buy-btn" @click="handleBuyTicket">
              <el-icon><ShoppingCart /></el-icon>
              立即购票
            </el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：地图区域 -->
      <div class="map-area" @click="onMapAreaClick">
        <!-- 地图切换 -->
        <div class="map-toggle" v-if="mapLines.length">
          <button class="toggle-btn" :class="{ active: mapMode === 'enhanced' }" @click="mapMode = 'enhanced'">
            <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/><path d="M8 14l2 2 4-4" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
            线路图
          </button>
          <button class="toggle-btn" :class="{ active: mapMode === 'geo' }" @click="mapMode = 'geo'">
            <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="9" r="2.5" fill="currentColor"/></svg>
            地理图
          </button>
        </div>
        <div class="map-container" v-loading="mapLoading">
          <MetroMapEnhanced
            v-if="mapLines.length && mapMode === 'enhanced'"
            ref="enhancedMapRef"
            :stations="[]"
            :lines="mapLines"
            height="100%"
            :show-legend="true"
            :fit-bounds="true"
            :highlight-line-name="pfActiveLineName"
            :show-route-panel="false"
            :route-style="routeStyleConfig"
            :label-config="labelConfigObj"
            @station-click="onMapStationClick"
            @route-found="onRouteFound"
            @route-cleared="onRouteCleared"
          />
          <MetroMap
            v-if="mapLines.length && mapMode === 'geo'"
            :stations="[]"
            :lines="mapLines"
            height="100%"
            :show-legend="true"
            :fit-bounds="true"
          />
          <div v-if="!mapLines.length && !mapLoading" class="map-empty">
            <div class="empty-icon">
              <svg viewBox="0 0 80 80" width="64" height="64">
                <circle cx="20" cy="40" r="6" fill="none" stroke="#c0c4cc" stroke-width="2"/>
                <circle cx="40" cy="20" r="6" fill="none" stroke="#c0c4cc" stroke-width="2"/>
                <circle cx="60" cy="40" r="6" fill="none" stroke="#c0c4cc" stroke-width="2"/>
                <circle cx="40" cy="60" r="6" fill="none" stroke="#c0c4cc" stroke-width="2"/>
                <path d="M25 37 L35 23 M45 23 L55 37 M55 43 L45 57 M35 57 L25 43" stroke="#dcdfe6" stroke-width="2" fill="none"/>
              </svg>
            </div>
            <p class="empty-text">请先选择城市</p>
            <p class="empty-hint">选择城市后将显示地铁线路图</p>
          </div>
        </div>
        <!-- 地图提示 -->
        <div class="map-hint" v-if="mapLines.length">
          <svg viewBox="0 0 24 24" width="14" height="14"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>
          <span v-if="mapMode === 'enhanced'">点击地图站点可快速选择出发站和到达站</span>
          <span v-else>地理图仅用于浏览，路线规划请切换到线路图</span>
        </div>
      </div>
    </div>

    <ProfileDialog v-model="showProfile" />
    <PurchaseDialog v-model:visible="showPurchase" :route-data="purchaseRouteData" @purchased="onPurchased" />
    <PurchaseDialog v-model:visible="showPurchaseFromAgent" :route-data="agentPurchaseData" @purchased="onPurchasedFromAgent" />

    <!-- Agent 路线助手面板 -->
    <RouteAgentPanel
      v-if="agentChatStore.panelState !== 'collapsed'"
      @open-purchase="openPurchaseFromAgent"
    />
    <button
      v-if="agentChatStore.panelState === 'collapsed'"
      class="agent-fab"
      @click="agentChatStore.setState('expanded')"
    >
      <svg viewBox="0 0 24 24" width="24"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z" fill="currentColor"/></svg>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, UserFilled, Location, ArrowRight, ArrowDown, Close, Search, ShoppingCart, Bell, Sunny, Moon } from '@element-plus/icons-vue'
import { userLogout } from '@/api/user'
import { getMessageUnreadCount } from '@/api/message'
import { getMetroLines, getMetroStations, getPublicCities, getPublicCountries } from '@/api/public'
import { useUserStore } from '@/store/user'
import ProfileDialog from '@/views/ProfileDialog.vue'
import PurchaseDialog from '@/components/PurchaseDialog.vue'
import MetroMapEnhanced from '@/components/MetroMapEnhanced.vue'
import MetroMap from '@/components/MetroMap.vue'
import RouteAgentPanel from '@/components/RouteAgentPanel.vue'
import { useAgentChatStore } from '@/store/agentChat'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const userStore = useUserStore()
const agentChatStore = useAgentChatStore()
const { toggle, isDark } = useTheme()
const showProfile = ref(false)
const showPurchase = ref(false)
const showPurchaseFromAgent = ref(false)
const purchaseRouteFromAgent = ref(null)
const msgUnreadCount = ref(0)
let msgPollTimer = null

const fetchMsgUnread = async () => {
  if (!userStore.isLoggedIn()) return
  try {
    const res = await getMessageUnreadCount()
    if (res.code === 200) msgUnreadCount.value = res.data.count || 0
  } catch {}
}
const enhancedMapRef = ref(null)
const { ensureLoaded, getConfigJson } = useSystemConfig()

const avatarUrl = computed(() => {
  if (!userStore.avatar) return ''
  if (userStore.avatar.startsWith('http://') || userStore.avatar.startsWith('https://')) return userStore.avatar
  const path = userStore.avatar.startsWith('/') ? userStore.avatar : '/' + userStore.avatar
  return `/transitMap${path}`
})

// ── 筛选数据 ──
const countries = ref([])
const cities = ref([])
const allLines = ref([])
const allStations = ref([])

const selectedCityId = ref(null)
const mapLoading = ref(false)
const cityAllMapLines = ref([])
const mapMode = ref('enhanced')

// ── 配置驱动的计算属性 ──
const routeStyleConfig = computed(() => getConfigJson('map.route_style', {}))
const labelConfigObj = computed(() => getConfigJson('map.label_config', {}))

// ── 城市下拉 ──
const showCityDropdown = ref(false)
const filterCountryId = ref(null)
const citySearchText = ref('')

const selectedCityName = computed(() => {
  if (!selectedCityId.value) return ''
  const c = cities.value.find(c => c.id === selectedCityId.value)
  return c?.cityName || ''
})

const filteredCityList = computed(() => {
  let list = cities.value
  if (filterCountryId.value) {
    list = list.filter(c => c.countryId === filterCountryId.value)
  }
  if (citySearchText.value) {
    const q = citySearchText.value.toLowerCase()
    list = list.filter(c => c.cityName.toLowerCase().includes(q))
  }
  return list.map(c => {
    const country = countries.value.find(co => co.id === c.countryId)
    return { ...c, _countryName: country?.countryName || '' }
  })
})

function toggleCityDropdown() {
  showCityDropdown.value = !showCityDropdown.value
  if (showCityDropdown.value) {
    citySearchText.value = ''
    filterCountryId.value = null
  }
}

function selectCity(cityId) {
  selectedCityId.value = cityId
  showCityDropdown.value = false
  citySearchText.value = ''
  filterCountryId.value = null
}

const mapLines = computed(() => cityAllMapLines.value)

const autoCityId = computed(() => {
  if (cities.value.length === 0) return null
  return cities.value[0].id
})

// ── 初始化加载 ──
async function loadInitialData() {
  const [citiesRes, countriesRes, linesRes, stationsRes] = await Promise.allSettled([
    getPublicCities(),
    getPublicCountries(),
    getMetroLines(),
    getMetroStations(),
  ])
  cities.value = citiesRes.status === 'fulfilled' ? (citiesRes.value.data || []) : []
  countries.value = countriesRes.status === 'fulfilled' ? (countriesRes.value.data || []) : []
  allLines.value = linesRes.status === 'fulfilled' ? (linesRes.value.data || []) : []
  allStations.value = stationsRes.status === 'fulfilled' ? (stationsRes.value.data || []) : []

  if (autoCityId.value) {
    selectedCityId.value = autoCityId.value
  }
}

function onCityChange() {
  clearPf()
  if (selectedCityId.value) {
    loadCityMapData(selectedCityId.value)
  } else {
    cityAllMapLines.value = []
  }
}

watch(selectedCityId, () => {
  onCityChange()
})

async function loadCityMapData(cityId) {
  if (!cityId) { cityAllMapLines.value = []; return }

  mapLoading.value = true
  try {
    const lines = allLines.value.filter(l => l.cityId === cityId)
    const stations = allStations.value.filter(s => s.cityId === cityId)

    const lineStationMap = {}
    for (const station of stations) {
      const lineIds = parseJsonSafe(station.lineIds)
      for (const lid of lineIds) {
        if (!lineStationMap[lid]) lineStationMap[lid] = []
        lineStationMap[lid].push(station)
      }
    }

    const result = []
    let colorIdx = 0
    const FALLBACK_COLORS = ['#e60012','#f39800','#009944','#0068b7','#00a0e9','#8b5eaa','#7fc242','#e6007e','#1d2088','#9b7b4d','#009f9d','#d70f5c','#6cc3c0','#f26522','#5c2d91']

    for (const line of lines) {
      const lineStations = lineStationMap[String(line.id)] || []
      if (lineStations.length < 2) continue
      const { ordered, connected } = orderByAdjacency(lineStations, String(line.id))
      result.push({
        id: String(line.id),
        name: line.lineName,
        color: line.lineColor || FALLBACK_COLORS[colorIdx % FALLBACK_COLORS.length],
        stations: ordered,
        connected,
      })
      colorIdx++
    }

    cityAllMapLines.value = result
  } catch (e) {
    console.error('加载城市地图数据失败:', e)
    cityAllMapLines.value = []
  } finally {
    mapLoading.value = false
  }
}

// ── 拓扑排序 ──
function orderByAdjacency(stations, lineId) {
  if (stations.length <= 1) return { ordered: stations, connected: stations.length > 0 }

  const idSet = new Set(stations.map(s => String(s.id)))
  const stationMap = {}
  stations.forEach(s => { stationMap[String(s.id)] = s })

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

  const hasAnyConnection = Object.values(adj).some(s => s.size > 0)
  if (!hasAnyConnection) return { ordered: stations, connected: false }

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
    const allConnected = ordered.length === stations.length
    if (!allConnected) {
      for (const s of stations) {
        if (!visited.has(String(s.id))) ordered.push(s)
      }
    }
    return { ordered, connected: allConnected }
  }

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

function parseJsonSafe(str) {
  if (!str) return []
  try { const arr = JSON.parse(str); return Array.isArray(arr) ? arr : [] } catch { return [] }
}

// ── 路径查找 ──
const pfStartId = ref(null)
const pfEndId = ref(null)
const pfStartLineId = ref(null)
const pfEndLineId = ref(null)
const showPfStart = ref(false)
const showPfEnd = ref(false)

const pfStartName = computed(() => {
  if (!pfStartId.value) return ''
  const s = findStationById(pfStartId.value)
  return s?.stationName || ''
})
const pfEndName = computed(() => {
  if (!pfEndId.value) return ''
  const s = findStationById(pfEndId.value)
  return s?.stationName || ''
})

const pfActiveLineName = computed(() => {
  if (showPfStart.value && pfStartLineId.value) {
    const line = cityAllMapLines.value.find(l => l.id === pfStartLineId.value)
    return line?.name || null
  }
  if (showPfEnd.value && pfEndLineId.value) {
    const line = cityAllMapLines.value.find(l => l.id === pfEndLineId.value)
    return line?.name || null
  }
  return null
})

function findStationById(id) {
  for (const line of cityAllMapLines.value) {
    for (const s of (line.stations || [])) {
      if (String(s.id) === String(id)) return s
    }
  }
  return null
}

function getPfLineStations(lineId) {
  const line = cityAllMapLines.value.find(l => l.id === lineId)
  return line?.stations || []
}

function getPfLineColor(lineId) {
  const line = cityAllMapLines.value.find(l => l.id === lineId)
  return line?.color || '#409EFF'
}

function isTransferStation(stationId) {
  const s = findStationById(stationId)
  return s?.isTransfer === 1
}

function togglePfPicker(type) {
  if (type === 'start') {
    showPfStart.value = !showPfStart.value
    showPfEnd.value = false
  } else {
    showPfEnd.value = !showPfEnd.value
    showPfStart.value = false
  }
}

function selectPickerLine(lineId) {
  if (showPfStart.value) {
    pfStartLineId.value = lineId
  } else {
    pfEndLineId.value = lineId
  }
}

function selectPfStation(type, stationId) {
  if (type === 'start') {
    pfStartId.value = stationId
    showPfStart.value = false
  } else {
    pfEndId.value = stationId
    showPfEnd.value = false
  }
}

function searchRoute() {
  if (!pfStartId.value || !pfEndId.value || pfStartId.value === pfEndId.value) return
  if (mapMode.value !== 'enhanced') {
    mapMode.value = 'enhanced'
    nextTick(() => enhancedMapRef.value?.findRoute(pfStartId.value, pfEndId.value))
  } else {
    enhancedMapRef.value?.findRoute(pfStartId.value, pfEndId.value)
  }
}

function swapPf() {
  const tmpId = pfStartId.value
  const tmpLine = pfStartLineId.value
  pfStartId.value = pfEndId.value
  pfStartLineId.value = pfEndLineId.value
  pfEndId.value = tmpId
  pfEndLineId.value = tmpLine
  if (pfStartId.value && pfEndId.value && pfStartId.value !== pfEndId.value) {
    nextTick(() => enhancedMapRef.value?.findRoute(pfStartId.value, pfEndId.value))
  }
}

function clearPf() {
  pfStartId.value = null
  pfEndId.value = null
  pfStartLineId.value = null
  pfEndLineId.value = null
  showPfStart.value = false
  showPfEnd.value = false
  routeInfo.value = null
  pathRoute.value = []
  enhancedMapRef.value?.clearRoute()
}

function clearRouteResult() {
  routeInfo.value = null
  pathRoute.value = []
  enhancedMapRef.value?.clearRoute()
}

function closePfDropdowns() {
  showPfStart.value = false
  showPfEnd.value = false
}

function onMapAreaClick() {
  closePfDropdowns()
  showCityDropdown.value = false
}

function onMapStationClick(stationId) {
  closePfDropdowns()
  if (!pfStartId.value) {
    pfStartId.value = stationId
    return
  }
  if (!pfEndId.value && stationId !== pfStartId.value) {
    pfEndId.value = stationId
    return
  }
  pfStartId.value = stationId
  pfEndId.value = null
}

// ── 路线结果 ──
const routeInfo = ref(null)
const pathRoute = ref([])

function getStationName(id) { return findStationById(id)?.stationName || id }
function getStationLineColor(id) {
  for (const line of cityAllMapLines.value) {
    if ((line.stations || []).some(s => String(s.id) === String(id))) return line.color
  }
  return '#ccc'
}

const estimatedTime = computed(() => {
  if (!routeInfo.value) return 0
  const params = getConfigJson('ticket.estimate_params', { minutesPerStop: 3, minMinutes: 2, kmPerStop: 1.8 })
  return Math.max(params.minMinutes, routeInfo.value.totalStops * params.minutesPerStop)
})

const estimatedDistance = computed(() => {
  if (!routeInfo.value) return 0
  const params = getConfigJson('ticket.estimate_params', { minutesPerStop: 3, minMinutes: 2, kmPerStop: 1.8 })
  const fallback = params.kmPerStop
  // 必须等待 pathRoute 就绪（nextTick 后才会填充）
  const route = pathRoute.value
  if (!route || route.length < 2) return (routeInfo.value.totalStops * fallback).toFixed(1)

  let totalKm = 0
  for (let i = 0; i < route.length - 1; i++) {
    const curId = String(route[i])
    const nextId = String(route[i + 1])
    const curStation = findStationById(curId)
    const nextStation = findStationById(nextId)

    let distA = null
    let distB = null

    // 从当前站的 nextStationDistances 中查找到下一站的距离
    if (curStation?.nextStationDistances) {
      const nextIds = parseJsonSafe(curStation.nextStationIds)
      const nextDists = parseJsonSafe(curStation.nextStationDistances)
      const idx = nextIds.findIndex(id => String(id) === nextId)
      if (idx >= 0 && nextDists[idx] != null && Number(nextDists[idx]) > 0) {
        distA = Number(nextDists[idx])
      }
    }

    // 从下一站的 prevStationDistances 中查找到当前站的距离
    if (nextStation?.prevStationDistances) {
      const prevIds = parseJsonSafe(nextStation.prevStationIds)
      const prevDists = parseJsonSafe(nextStation.prevStationDistances)
      const idx = prevIds.findIndex(id => String(id) === curId)
      if (idx >= 0 && prevDists[idx] != null && Number(prevDists[idx]) > 0) {
        distB = Number(prevDists[idx])
      }
    }

    // 两个方向都有值取平均，只有一个用那个，都没有用 fallback
    if (distA != null && distB != null) totalKm += (distA + distB) / 2
    else if (distA != null) totalKm += distA
    else if (distB != null) totalKm += distB
    else totalKm += fallback
  }
  return totalKm.toFixed(1)
})

const estimatedPrice = computed(() => {
  if (!routeInfo.value) return 0
  const stops = routeInfo.value.totalStops
  const tiers = getConfigJson('ticket.price_tiers', [
    { maxStops: 3, price: 2 }, { maxStops: 6, price: 3 },
    { maxStops: 9, price: 4 }, { maxStops: 12, price: 5 },
    { maxStops: 18, price: 6 }, { maxStops: 999, price: 7 },
  ])
  for (const tier of tiers) {
    if (stops <= tier.maxStops) return tier.price
  }
  return tiers[tiers.length - 1]?.price ?? 0
})

function onRouteFound(info) {
  routeInfo.value = info
  // 从地图组件获取完整路径
  nextTick(() => {
    const mapRef = enhancedMapRef.value
    if (mapRef?.pathRoute) {
      pathRoute.value = [...mapRef.pathRoute]
    }
  })
}

function onRouteCleared() {
  routeInfo.value = null
  pathRoute.value = []
}

// 处理购票
function handleBuyTicket() {
  if (!userStore.isLoggedIn()) {
    ElMessageBox.confirm('购票需要先登录，是否前往登录？', '提示', {
      confirmButtonText: '去登录',
      cancelButtonText: '取消',
      type: 'info',
    }).then(() => {
      router.push('/login')
    }).catch(() => {})
    return
  }
  if (!routeInfo.value || !pathRoute.value.length) return
  showPurchase.value = true
}

// 购票成功回调
function onPurchased() {
  ElMessage.success('购票成功！可在"我的订单"中查看')
}

// 从 Agent 面板购票成功 → 回插订单卡片
function onPurchasedFromAgent(orderInfo) {
  ElMessage.success('购票成功！订单已添加到对话')
  const route = purchaseRouteFromAgent.value
  agentChatStore.appendOrderCard({
    orderNo: orderInfo?.orderNo || orderInfo?.id || ('ORD' + Date.now()),
    id: orderInfo?.id,
    startStationName: route?.startStationName || route?.startName,
    endStationName: route?.endStationName || route?.endName,
    price: route?.price,
    quantity: orderInfo?.quantity || 1,
    status: 'paid'
  })
  purchaseRouteFromAgent.value = null
}

// 从 Agent 面板打开购票弹窗
function openPurchaseFromAgent(route) {
  if (!userStore.isLoggedIn()) {
    ElMessageBox.confirm('登录后才能购票，是否前往登录？', '提示', {
      confirmButtonText: '去登录',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      router.push('/login')
    }).catch(() => {})
    return
  }
  purchaseRouteFromAgent.value = route
  showPurchaseFromAgent.value = true
}

// 购票弹窗所需的路线数据
const purchaseRouteData = computed(() => {
  if (!routeInfo.value || !pathRoute.value.length) return null
  const names = pathRoute.value.map(sid => getStationName(sid))
  return {
    startId: pathRoute.value[0],
    endId: pathRoute.value[pathRoute.value.length - 1],
    startName: names[0],
    endName: names[names.length - 1],
    stationCount: routeInfo.value.totalStops,
    distance: estimatedDistance.value,
    duration: estimatedTime.value,
    price: estimatedPrice.value,
    stationNames: names,
  }
})

// Agent 路线卡片的购票数据
const agentPurchaseData = computed(() => {
  if (!purchaseRouteFromAgent.value) return null
  const r = purchaseRouteFromAgent.value
  // 关键：站点 ID 强制按字符串传递，防止 JS 在处理 19 位雪花 ID 时精度丢失
  return {
    startId: r.startStationId != null ? String(r.startStationId) : null,
    endId: r.endStationId != null ? String(r.endStationId) : null,
    startName: r.startStationName,
    endName: r.endStationName,
    stationCount: r.stationCount,
    distance: r.distanceKm,
    duration: r.durationMinutes,
    price: r.price,
    stationNames: r.stationNames || [],
  }
})

onMounted(async () => {
  loadInitialData()
  await ensureLoaded()
  fetchMsgUnread()
  msgPollTimer = setInterval(fetchMsgUnread, 30000)
})
onUnmounted(() => { if (msgPollTimer) clearInterval(msgPollTimer) })

// ── 用户操作 ──
const handleCommand = async (command) => {
  if (command === 'profile') {
    showProfile.value = true
  } else if (command === 'tickets') {
    router.push('/tickets')
  } else if (command === 'messages') {
    router.push('/messages')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
      })
      await userLogout()
      userStore.clearUser()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch {}
  }
}
</script>

<style scoped>
/* ── 全局 ── */
.ticket-page {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
}

/* ── 导航栏 ── */
.nav-bar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #1a73e8 0%, #1565c0 100%);
  z-index: 100;
  box-shadow: 0 2px 12px rgba(26, 115, 232, 0.3);
}
.nav-inner {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.nav-left { display: flex; align-items: center; gap: 10px; }
.nav-logo {
  width: 34px; height: 34px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(10px);
}
.nav-title {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}
.nav-center { flex: 1; display: flex; justify-content: center; }
.city-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  padding: 6px 14px;
  color: rgba(255, 255, 255, 0.9);
  position: relative;
}
.city-display {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
  min-width: 120px;
}
.city-display:hover { background: rgba(255, 255, 255, 0.1); }
.city-display-text {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}
.city-display-text.placeholder { color: rgba(255, 255, 255, 0.6); font-weight: 500; }
.city-display-arrow {
  color: rgba(255, 255, 255, 0.7);
  transition: transform 0.2s;
  font-size: 12px;
}
.city-display-arrow.open { transform: rotate(180deg); }

/* ── 城市下拉 ── */
.city-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  width: 340px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
  border: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 300;
  overflow: hidden;
  animation: ddSlide 0.2s ease;
}
@keyframes ddSlide { from { transform: translateX(-50%) translateY(-6px); opacity: 0; } }

.cd-country-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  overflow-x: auto;
  scrollbar-width: none;
}
.cd-country-tabs::-webkit-scrollbar { display: none; }
.cd-tab {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  color: #5f6368;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  background: #f5f7fa;
  border: 1px solid transparent;
}
.cd-tab:hover { background: #e8f0fe; color: #1a73e8; }
.cd-tab.active {
  background: #1a73e8;
  color: #fff;
  border-color: #1a73e8;
}

.cd-search {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}
:deep(.cd-search .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #e0e0e0 inset;
}
:deep(.cd-search .el-input__wrapper:hover),
:deep(.cd-search .el-input__wrapper:focus-within) {
  box-shadow: 0 0 0 1px #1a73e8 inset;
}

.cd-city-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 6px;
}
.cd-city-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.1s;
}
.cd-city-item:hover { background: #f0f5ff; }
.cd-city-item.active { background: #e8f0fe; }
.cd-city-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
}
.cd-city-country {
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 4px;
}
.cd-city-count {
  font-size: 11px;
  color: #5f6368;
  font-weight: 500;
}
.cd-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
.nav-right { display: flex; align-items: center; gap: 8px; }

.nav-theme-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.nav-theme-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}
.nav-msg-icon {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.nav-msg-icon:hover { color: #fff; background: rgba(255, 255, 255, 0.15); }
.nav-msg-badge { margin-right: 4px; }
:deep(.nav-msg-badge .el-badge__content) { border: none; }
.user-info {
  display: flex; align-items: center; gap: 8px;
  cursor: pointer; color: rgba(255, 255, 255, 0.9); font-size: 14px;
  padding: 4px 10px; border-radius: 8px;
  transition: background 0.2s;
}
.user-info:hover { background: rgba(255, 255, 255, 0.15); }
.user-name { font-weight: 500; }
.nav-login-btn { color: rgba(255, 255, 255, 0.9) !important; font-weight: 500; }
:deep(.nav-login-btn:hover) { color: #fff !important; background: rgba(255, 255, 255, 0.1) !important; }

/* ── 主体内容 ── */
.main-content {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 0;
  overflow: hidden;
}

/* ── Agent 浮动按钮 ── */
.agent-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #1a73e8;
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(26, 115, 232, 0.4);
  transition: all 0.2s;
  z-index: 999;
}

.agent-fab:hover {
  background: #1557b0;
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(26, 115, 232, 0.5);
}

/* ── 左侧购票面板 ── */
.ticket-panel {
  width: 420px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  background: #f5f7fa;
  min-height: 0;
}

/* ── 购票卡片 ── */
.ticket-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.ticket-card-header {
  padding: 20px 20px 0;
}
.tch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
}
.tch-title svg { color: #1a73e8; }
.tch-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  padding-left: 28px;
}

/* ── 站点选择器 ── */
.station-selector {
  padding: 20px;
}
.ss-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e8eaed;
  background: #fafbfc;
}
.ss-row:hover {
  border-color: #1a73e8;
  background: #f0f6ff;
}
.ss-start:hover { border-color: #34a853; background: #f0faf4; }
.ss-end:hover { border-color: #ea4335; background: #fef7f6; }

.ss-dot-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  position: relative;
}
.ss-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 3px solid;
}
.ss-dot-start { border-color: #34a853; background: #fff; }
.ss-dot-end { border-color: #ea4335; background: #fff; }
.ss-line-connector {
  width: 2px;
  height: 16px;
  background: #d0d7de;
  margin: 2px 0;
}
.ss-swap-row {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  height: 0;
}
.ss-swap-line {
  position: absolute;
  left: 32px;
  top: -8px;
  bottom: -8px;
  width: 2px;
  background: #d0d7de;
}
.ss-swap-btn {
  position: relative;
  z-index: 1;
  width: 32px;
  height: 32px;
  border: 2px solid #e8eaed;
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  transition: all 0.2s;
  margin-left: auto;
  margin-right: 16px;
}
.ss-swap-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
  transform: rotate(180deg);
}
.ss-info {
  flex: 1;
  min-width: 0;
}
.ss-label {
  display: block;
  font-size: 11px;
  color: #909399;
  font-weight: 500;
  margin-bottom: 2px;
}
.ss-station {
  display: block;
  font-size: 15px;
  color: #c0c4cc;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ss-station.filled {
  color: #1a1a2e;
  font-weight: 700;
  font-size: 16px;
}
.ss-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

/* ── 操作按钮 ── */
.ticket-actions {
  display: flex;
  align-items: center;
  padding: 0 20px 20px;
  gap: 8px;
}
.ticket-actions .spacer { flex: 1; }
.search-btn {
  padding: 12px 32px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  background: linear-gradient(135deg, #1a73e8, #1565c0) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(26, 115, 232, 0.35) !important;
}
.search-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(26, 115, 232, 0.45) !important;
}
.search-btn:disabled {
  background: #c0c4cc !important;
  box-shadow: none !important;
  transform: none !important;
}

/* ── 站点选择下拉 ── */
.station-picker-dropdown {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  animation: slideUp 0.2s ease;
}
@keyframes slideUp { from { transform: translateY(8px); opacity: 0; } }

.spd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.spd-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}
.spd-close {
  width: 28px; height: 28px; border: none; border-radius: 8px;
  background: #f5f7fa; cursor: pointer; display: flex;
  align-items: center; justify-content: center; color: #909399;
  transition: all 0.2s;
}
.spd-close:hover { background: #fef0f0; color: #f56c6c; }

.spd-lines {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.spd-line-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
  border: 1.5px solid;
}
.spd-line-tag:hover { opacity: 0.85; }
.spd-line-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.spd-line-tag.active .spd-line-dot {
  background: #fff !important;
}

.spd-stations {
  max-height: 280px;
  overflow-y: auto;
  padding: 8px;
}
.spd-station {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.1s;
}
.spd-station:hover { background: #f0f5ff; }
.spd-station.selected { background: #e8f0fe; }
.spd-station-num {
  width: 22px; height: 22px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700; color: #fff; flex-shrink: 0;
}
.spd-station-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}
.spd-station-transfer {
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

/* ── 路线结果卡片 ── */
.route-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  animation: slideUp 0.3s ease;
}
.route-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.rch-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
}
.rch-title svg { color: #1a73e8; }
.rch-close {
  width: 28px; height: 28px; border: none; border-radius: 8px;
  background: #f5f7fa; cursor: pointer; display: flex;
  align-items: center; justify-content: center; color: #909399;
  transition: all 0.2s;
}
.rch-close:hover { background: #fef0f0; color: #f56c6c; }

/* ── 行程概览 ── */
.route-overview {
  display: flex;
  align-items: center;
  padding: 20px;
  gap: 12px;
}
.ro-endpoint {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.ro-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 3px solid;
}
.ro-dot-start { border-color: #34a853; }
.ro-dot-end { border-color: #ea4335; }
.ro-name {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ro-middle {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.ro-line {
  width: 20px;
  height: 2px;
  background: #d0d7de;
}
.ro-badge {
  display: flex;
  align-items: baseline;
  gap: 2px;
  background: #e8f0fe;
  padding: 4px 10px;
  border-radius: 12px;
}
.ro-stops {
  font-size: 18px;
  font-weight: 800;
  color: #1a73e8;
}
.ro-stops-label {
  font-size: 11px;
  color: #5f6368;
  font-weight: 600;
}

/* ── 旅程信息 ── */
.route-meta {
  display: flex;
  gap: 16px;
  padding: 0 20px 16px;
}
.rm-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #5f6368;
}
.rm-icon { color: #9aa0a6; display: flex; }
.rm-price .rm-price-text {
  font-weight: 700;
  color: #1a73e8;
  font-size: 15px;
}

/* ── 站点列表 ── */
.route-stations {
  padding: 0 20px 16px;
}
.rs-title {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.rs-list {
  max-height: 200px;
  overflow-y: auto;
}
.rs-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  position: relative;
}
.rs-item-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16px;
  flex-shrink: 0;
}
.rs-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d0d7de;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #d0d7de;
  z-index: 1;
  flex-shrink: 0;
}
.rs-dot.start {
  background: #34a853;
  box-shadow: 0 0 0 1px #34a853;
}
.rs-dot.end {
  background: #ea4335;
  box-shadow: 0 0 0 1px #ea4335;
}
.rs-connector {
  width: 2px;
  height: 12px;
  background: #d0d7de;
}
.rs-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
}
.rs-item.start .rs-name,
.rs-item.end .rs-name {
  font-weight: 700;
  color: #1a1a2e;
}
.rs-transfer-tag {
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
  flex-shrink: 0;
}
.rs-line-indicator {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ── 购票按钮 ── */
.route-buy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f8fbff, #eef4ff);
  border-top: 1px solid #e8eaed;
}
.rb-price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.rb-label {
  font-size: 13px;
  color: #5f6368;
}
.rb-price {
  font-size: 28px;
  font-weight: 800;
  color: #1a73e8;
}
.buy-btn {
  padding: 12px 32px !important;
  font-size: 15px !important;
  font-weight: 700 !important;
  background: linear-gradient(135deg, #34a853, #2d8f47) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(52, 168, 83, 0.35) !important;
}
.buy-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(52, 168, 83, 0.45) !important;
}

/* ── 右侧地图区域 ── */
.map-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 16px 16px 0;
  min-width: 0;
  position: relative;
}
.map-toggle {
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border-radius: 10px;
  padding: 3px;
  position: absolute;
  top: 28px;
  right: 28px;
  z-index: 10;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.toggle-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #909399;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.toggle-btn:hover { color: #606266; background: rgba(0,0,0,0.03); }
.toggle-btn.active {
  background: #1a73e8;
  color: #fff;
  box-shadow: 0 1px 4px rgba(26, 115, 232, 0.3);
  font-weight: 600;
}
.map-container {
  flex: 1;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid #e4e7ed;
  position: relative;
  z-index: 0;
  min-height: 0;
}
.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fff;
  gap: 12px;
}
.empty-icon { opacity: 0.4; }
.empty-text {
  font-size: 16px;
  font-weight: 600;
  color: #606266;
  margin: 0;
}
.empty-hint {
  font-size: 13px;
  color: #b0b0b0;
  margin: 0;
}
.map-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 12px;
  color: #909399;
  justify-content: center;
}
.map-hint svg { color: #c0c4cc; }

/* ── 响应式 ── */
@media (max-width: 1100px) {
  .main-content {
    flex-direction: column;
    overflow: visible;
    min-height: 0;
  }
  .ticket-panel {
    width: 100%;
    flex-shrink: 0;
  }
  .map-area {
    padding: 0 16px 16px;
    min-height: 500px;
    flex-shrink: 0;
  }
}
@media (max-width: 600px) {
  .nav-center { display: none; }
  .ticket-panel { padding: 12px; }
  .route-meta { flex-wrap: wrap; gap: 10px; }
}

/* ── 深度样式 ── */
:deep(.el-select) {
  --el-select-border-color-hover: #1a73e8;
}
:deep(.el-input__wrapper) {
  border-radius: 8px;
}
</style>
