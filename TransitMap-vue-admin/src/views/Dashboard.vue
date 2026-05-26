<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="welcome-section">
      <div class="welcome-left">
        <h3>欢迎回来，{{ userStore.username }}</h3>
        <p>{{ currentDate }} · 系统运行正常</p>
      </div>
      <div class="welcome-right">
        <div class="welcome-stat">
          <span class="ws-value">{{ stats.totalUsers }}</span>
          <span class="ws-label">总用户</span>
        </div>
        <div class="welcome-stat">
          <span class="ws-value">{{ stats.totalOrders }}</span>
          <span class="ws-label">总订单</span>
        </div>
        <div class="welcome-stat">
          <span class="ws-value">¥{{ stats.totalRevenue }}</span>
          <span class="ws-label">总收入</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="12" :sm="8" :lg="4" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card" :class="card.class">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 折线图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">用户注册趋势</span>
              <span class="card-sub">近 7 天</span>
            </div>
          </template>
          <v-chart :option="userTrendOption" autoresize style="height: 280px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">订单趋势</span>
              <span class="card-sub">近 7 天</span>
            </div>
          </template>
          <v-chart :option="orderTrendOption" autoresize style="height: 280px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 饼图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="card-title">用户角色分布</span></template>
          <v-chart :option="userRoleOption" autoresize style="height: 300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="card-title">订单状态分布</span></template>
          <v-chart :option="orderStatusOption" autoresize style="height: 300px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="card-title">国家状态分布</span></template>
          <v-chart :option="countryStatusOption" autoresize style="height: 300px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部：表格 + 小饼图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="13">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="card-title">最近注册用户</span></template>
          <el-table :data="recentUsers" size="small" max-height="280" stripe>
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="role" label="角色" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="roleTagType(row.role)">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="注册时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="11">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="hover" class="chart-card">
              <template #header><span class="card-title">账号状态</span></template>
              <v-chart :option="userAccountStatusOption" autoresize style="height: 220px" />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover" class="chart-card">
              <template #header><span class="card-title">在线状态</span></template>
              <v-chart :option="userOnlineStatusOption" autoresize style="height: 220px" />
            </el-card>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { UserFilled, Check, Flag, Plus, OfficeBuilding, Van, MapLocation, CircleCheckFilled, Ticket, Coin } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

use([PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const userStore = useUserStore()

const currentDate = computed(() => {
  const d = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${weekdays[d.getDay()]}`
})

const stats = ref({
  totalUsers: 0, onlineUsers: 0, disabledUsers: 0,
  todayNewUsers: 0, totalCountries: 0, onlineCountries: 0,
  totalCities: 0, totalMetroLines: 0, totalMetroStations: 0,
  totalOrders: 0, todayOrders: 0, totalRevenue: 0, todayRevenue: 0,
  usersByRole: [], usersByOnlineStatus: [], usersByAccountStatus: [],
  countriesByStatus: [], recentRegistrations: [],
  userRegistrationTrend: [], orderTrend: [], ordersByStatus: []
})

const statCards = computed(() => [
  { label: '总用户数', value: stats.value.totalUsers, icon: UserFilled, bg: '#ecf5ff', color: '#409EFF', class: '' },
  { label: '在线用户', value: stats.value.onlineUsers, icon: Check, bg: '#f0f9eb', color: '#67C23A', class: '' },
  { label: '今日新增', value: stats.value.todayNewUsers, icon: Plus, bg: '#f4f0fe', color: '#a855f7', class: '' },
  { label: '总订单数', value: stats.value.totalOrders, icon: Ticket, bg: '#fdf6ec', color: '#E6A23C', class: '' },
  { label: '今日订单', value: stats.value.todayOrders, icon: Ticket, bg: '#e6fffb', color: '#13c2c2', class: '' },
  { label: '今日收入', value: '¥' + stats.value.todayRevenue, icon: Coin, bg: '#fff0f6', color: '#eb2f96', class: '' },
])

const recentUsers = computed(() => stats.value.recentRegistrations)

const roleTagType = (role) => {
  const map = { '普通用户': 'info', '管理员用户': 'warning', '超级管理员': 'success', '最高级管理员': 'danger' }
  return map[role] || 'info'
}

// ═══ 折线图 ═══

const makeTrendOption = (data, color, gradientTop, gradientBottom) => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255,255,255,0.95)',
    borderColor: '#eee',
    borderWidth: 1,
    textStyle: { color: '#303133', fontSize: 13 },
    formatter: (params) => {
      const p = params[0]
      return `<div style="font-weight:600">${p.axisValue}</div><div style="margin-top:4px">${p.marker} ${p.value}</div>`
    }
  },
  grid: { left: '3%', right: '4%', bottom: '3%', top: 20, containLabel: true },
  xAxis: {
    type: 'category',
    data: data.map(d => d.date.slice(5)),
    boundaryGap: false,
    axisLine: { lineStyle: { color: '#e4e7ed' } },
    axisLabel: { color: '#909399' }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
    axisLabel: { color: '#909399' }
  },
  series: [{
    type: 'line',
    data: data.map(d => d.value),
    smooth: true,
    symbol: 'circle',
    symbolSize: 8,
    showSymbol: data.length <= 7,
    areaStyle: {
      color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: gradientTop },
          { offset: 1, color: gradientBottom }
        ]
      }
    },
    lineStyle: { color, width: 3 },
    itemStyle: { color, borderColor: '#fff', borderWidth: 2 }
  }]
})

const userTrendOption = computed(() =>
  makeTrendOption(stats.value.userRegistrationTrend || [], '#409EFF', 'rgba(64,158,255,0.25)', 'rgba(64,158,255,0.01)')
)

const orderTrendOption = computed(() =>
  makeTrendOption(stats.value.orderTrend || [], '#67C23A', 'rgba(103,194,58,0.25)', 'rgba(103,194,58,0.01)')
)

// ═══ 饼图 ═══

const pieOption = (data, colors) => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12, color: '#606266' } },
  color: colors,
  series: [{
    type: 'pie',
    radius: ['42%', '68%'],
    center: ['50%', '42%'],
    itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
    label: { formatter: '{b}\n{d}%', fontSize: 11 },
    emphasis: { label: { fontSize: 14, fontWeight: 'bold' }, itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.1)' } },
    data: data.map(item => ({ name: item.name, value: item.value }))
  }]
})

const userRoleOption = computed(() =>
  pieOption(stats.value.usersByRole || [], ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C'])
)

const orderStatusOption = computed(() =>
  pieOption(stats.value.ordersByStatus || [], ['#E6A23C', '#67C23A', '#409EFF', '#909399', '#F56C6C', '#a855f7'])
)

const countryStatusOption = computed(() => {
  const data = stats.value.countriesByStatus || []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: { rotate: 15, color: '#909399' },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
      axisLabel: { color: '#909399' }
    },
    series: [{
      type: 'bar',
      data: data.map((item, idx) => ({
        value: item.value,
        itemStyle: { color: ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399'][idx % 5] }
      })),
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      barWidth: '45%',
      label: { show: true, position: 'top', color: '#606266', fontSize: 12 }
    }]
  }
})

const userAccountStatusOption = computed(() =>
  pieOption(stats.value.usersByAccountStatus || [], ['#409EFF', '#F56C6C'])
)

const userOnlineStatusOption = computed(() =>
  pieOption(stats.value.usersByOnlineStatus || [], ['#67C23A', '#909399'])
)

// ═══ 数据获取 ═══

const fetchStats = async () => {
  try {
    const res = await request.get('/dashboard/stats')
    if (res.code === 200) {
      stats.value = res.data
    }
  } catch (e) {
    // ignore
  }
}

onMounted(() => { fetchStats() })
</script>

<style scoped>
.dashboard { padding: 0; }

/* 欢迎横幅 */
.welcome-section {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 50%, #2b6cb0 100%);
  border-radius: 12px; padding: 24px 28px; margin-bottom: 16px;
  color: #fff; display: flex; justify-content: space-between; align-items: center;
  position: relative; overflow: hidden;
}
.welcome-section::before {
  content: ''; position: absolute; right: -40px; top: -40px;
  width: 200px; height: 200px; border-radius: 50%;
  background: rgba(255,255,255,0.08);
}
.welcome-section::after {
  content: ''; position: absolute; right: 60px; bottom: -60px;
  width: 160px; height: 160px; border-radius: 50%;
  background: rgba(255,255,255,0.05);
}
.welcome-left h3 { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
.welcome-left p { margin: 0; font-size: 13px; opacity: 0.85; }
.welcome-right { display: flex; gap: 28px; }
.welcome-stat { text-align: center; }
.ws-value { display: block; font-size: 22px; font-weight: 700; }
.ws-label { font-size: 12px; opacity: 0.8; }

/* 统计卡片 */
.stat-cards { margin-bottom: 16px; }
.stat-card { cursor: default; border-radius: 12px; transition: transform 0.2s, box-shadow 0.2s; }
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 6px 20px rgba(0,0,0,0.08); }
.stat-content { display: flex; align-items: center; gap: 14px; }
.stat-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.stat-value { font-size: 24px; font-weight: 700; color: #303133; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 2px; }

/* 图表卡片 */
.chart-row { margin-bottom: 16px; }
.chart-card { border-radius: 12px; }
.card-header { display: flex; align-items: baseline; gap: 8px; }
.card-title { font-weight: 600; font-size: 15px; color: #303133; }
.card-sub { font-size: 12px; color: #909399; }
</style>
