<template>
  <div class="chat-manage">
    <!-- 用量大盘 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card stat-blue">
          <div class="stat-label">近 30 天请求</div>
          <div class="stat-value">{{ summary.totalRequests || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-green">
          <div class="stat-label">输入 Token</div>
          <div class="stat-value">{{ formatNum(summary.totalTokensIn) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-orange">
          <div class="stat-label">输出 Token</div>
          <div class="stat-value">{{ formatNum(summary.totalTokensOut) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-red">
          <div class="stat-label">近 30 天成本</div>
          <div class="stat-value">¥{{ (summary.totalCostYuan || 0).toFixed(2) }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图 + 输入方式占比 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>近 7 天 Token 用量</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :label="7">7天</el-radio-button>
                <el-radio-button :label="14">14天</el-radio-button>
                <el-radio-button :label="30">30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-canvas" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>输入方式占比（近 7 天）</span>
            </div>
          </template>
          <div ref="inputChartRef" class="chart-canvas" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 会话列表 -->
    <el-card class="session-list-card">
      <template #header>
        <div class="card-header">
          <span>会话列表</span>
          <div class="header-tools">
            <el-input
              v-model="filterUserId"
              size="small"
              placeholder="按用户 ID 过滤"
              clearable
              style="width: 180px"
              @change="loadSessions"
            />
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              size="small"
              start-placeholder="起始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px"
              @change="loadSessions"
            />
          </div>
        </div>
      </template>

      <el-table :data="sessions" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="100">
          <template #default="{ row }">
            {{ row.userId || '(匿名)' }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" show-overflow-tooltip />
        <el-table-column prop="msgCount" label="消息数" width="90" align="center" />
        <el-table-column prop="lastMsgAt" label="最后活跃" width="170">
          <template #default="{ row }">
            {{ formatTime(row.lastMsgAt || row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadSessions"
        @size-change="loadSessions"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 差评消息 -->
    <el-card class="bad-feedback-card" v-if="badList.length">
      <template #header>
        <span>近期差评消息（仅展示预览）</span>
      </template>
      <el-table :data="badList" stripe>
        <el-table-column prop="id" label="消息ID" width="80" />
        <el-table-column prop="sessionId" label="会话ID" width="100">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openDetail(row.sessionId)">{{ row.sessionId }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="intent" label="意图" width="120" />
        <el-table-column prop="preview" label="内容预览" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 会话详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail?.canSeePlain ? '会话详情（已授权明文）' : '会话详情（脱敏）'"
      size="820px"
      destroy-on-close
    >
      <div v-if="detail" class="detail-body">
        <div class="detail-toolbar">
          <el-tag v-if="detail.canSeePlain" type="success" effect="dark">已获授权 · 可看明文</el-tag>
          <el-tag v-else type="info" effect="plain">脱敏视图</el-tag>
          <div class="spacer"></div>
          <el-button v-if="!detail.canSeePlain" type="warning" plain size="small" @click="openRequestDialog">
            <el-icon><Lock /></el-icon>
            申请查看明文
          </el-button>
        </div>

        <h4>会话信息</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detail.session.id }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.session.userId || '匿名' }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.session.title }}</el-descriptions-item>
          <el-descriptions-item label="消息数">{{ detail.session.msgCount }}</el-descriptions-item>
          <el-descriptions-item label="创建">{{ formatTime(detail.session.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="过期">{{ formatTime(detail.session.expireAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4>消息列表 {{ detail.canSeePlain ? '（明文）' : '（脱敏：仅统计）' }}</h4>
        <el-table :data="detail.messages" stripe size="small">
          <el-table-column prop="role" label="角色" width="80" />
          <el-table-column v-if="detail.canSeePlain" prop="content" label="内容" show-overflow-tooltip />
          <el-table-column v-else prop="length" label="字符数" width="80" />
          <el-table-column prop="intent" label="意图" width="100" />
          <el-table-column prop="inputMethod" label="输入" width="80" />
          <el-table-column prop="tokensIn" label="入" width="70" />
          <el-table-column prop="tokensOut" label="出" width="70" />
          <el-table-column prop="latencyMs" label="耗时" width="80" />
          <el-table-column prop="feedback" label="反馈" width="60">
            <template #default="{ row }">
              <span v-if="row.feedback === 1">👍</span>
              <span v-else-if="row.feedback === -1">👎</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>

        <h4>节点执行日志</h4>
        <el-table :data="detail.logs" stripe size="small">
          <el-table-column prop="nodeName" label="节点" width="160" />
          <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? 'OK' : 'FAIL' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
          <el-table-column prop="traceId" label="Trace" width="240" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <!-- 申请查看明文对话框 -->
    <el-dialog v-model="requestDialogVisible" title="申请查看明文对话" width="520px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        申请提交后需要 <strong>另一位超级管理员</strong> 审批通过，方可看到明文（24 小时有效）。所有审批操作均会留痕。
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="会话 ID">
          <el-input :value="detail?.session?.id" disabled />
        </el-form-item>
        <el-form-item label="申请理由" required>
          <el-input
            v-model="requestReason"
            type="textarea"
            :rows="4"
            placeholder="例如：用户投诉某次推荐错站，需复核问答细节"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingRequest" @click="submitDecryptRequest">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getSessionPage,
  getSessionDetail,
  getDailyUsage,
  getUsageSummary,
  getBadFeedback,
  getInputMethodStats,
  requestDecrypt
} from '@/api/chatManage'

const sessions = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const filterUserId = ref('')
const dateRange = ref([])

const summary = ref({})
const badList = ref([])
const inputStats = ref({})

const trendChartRef = ref(null)
const inputChartRef = ref(null)
const trendDays = ref(7)
let trendChart = null
let inputChart = null

const detailVisible = ref(false)
const detail = ref(null)
const requestDialogVisible = ref(false)
const requestReason = ref('')
const submittingRequest = ref(false)

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  return d.getFullYear() + '-' +
    String(d.getMonth() + 1).padStart(2, '0') + '-' +
    String(d.getDate()).padStart(2, '0') + ' ' +
    String(d.getHours()).padStart(2, '0') + ':' +
    String(d.getMinutes()).padStart(2, '0')
}

function formatNum(n) {
  const v = Number(n) || 0
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000) return (v / 1_000).toFixed(1) + 'K'
  return v
}

async function loadSessions() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filterUserId.value) params.userId = filterUserId.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getSessionPage(params)
    if (res.code === 200) {
      sessions.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    const res = await getUsageSummary(30)
    if (res.code === 200) summary.value = res.data || {}
  } catch {}
}

async function loadTrend() {
  try {
    const res = await getDailyUsage(trendDays.value)
    if (res.code === 200) {
      renderTrendChart(res.data || [])
    }
  } catch {}
}

async function loadBadFeedback() {
  try {
    const res = await getBadFeedback(10)
    if (res.code === 200) badList.value = res.data || []
  } catch {}
}

async function loadInputStats() {
  try {
    const res = await getInputMethodStats(7)
    if (res.code === 200) {
      inputStats.value = res.data || {}
      renderInputChart(inputStats.value)
    }
  } catch {}
}

function renderTrendChart(rows) {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  // 按日聚合
  const byDate = {}
  for (const r of rows) {
    const d = r.statDate
    if (!byDate[d]) byDate[d] = { in: 0, out: 0, cost: 0, req: 0 }
    byDate[d].in += r.tokensIn || 0
    byDate[d].out += r.tokensOut || 0
    byDate[d].cost += r.costCents || 0
    byDate[d].req += r.requestCount || 0
  }
  const dates = Object.keys(byDate).sort()
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['输入 Token', '输出 Token', '请求数'] },
    grid: { top: 40, left: 50, right: 50, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: 'Token' },
      { type: 'value', name: '请求' }
    ],
    series: [
      { name: '输入 Token', type: 'line', smooth: true, data: dates.map(d => byDate[d].in) },
      { name: '输出 Token', type: 'line', smooth: true, data: dates.map(d => byDate[d].out) },
      { name: '请求数', type: 'bar', yAxisIndex: 1, data: dates.map(d => byDate[d].req), itemStyle: { opacity: 0.5 } }
    ]
  })
}

function renderInputChart(stats) {
  if (!inputChartRef.value) return
  if (!inputChart) inputChart = echarts.init(inputChartRef.value)
  const data = [
    { value: stats.text || 0, name: '文本' },
    { value: stats.voice || 0, name: '语音' },
    { value: stats.chip || 0, name: '快捷词' }
  ]
  inputChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data,
      label: { formatter: '{b}\n{c}' }
    }]
  })
}

async function openDetail(id) {
  try {
    const res = await getSessionDetail(id)
    if (res.code === 200) {
      detail.value = res.data
      detailVisible.value = true
    }
  } catch (e) {
    console.warn('Failed to load detail', e)
  }
}

function openRequestDialog() {
  requestReason.value = ''
  requestDialogVisible.value = true
}

async function submitDecryptRequest() {
  const reason = requestReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写申请理由')
    return
  }
  submittingRequest.value = true
  try {
    const res = await requestDecrypt(detail.value.session.id, reason)
    if (res.code === 200) {
      ElMessage.success('申请已提交，请通知另一位超级管理员审批')
      requestDialogVisible.value = false
    }
  } catch (e) {
    // request.js 已弹错
  } finally {
    submittingRequest.value = false
  }
}

function onResize() {
  trendChart?.resize()
  inputChart?.resize()
}

onMounted(async () => {
  await loadSessions()
  await loadSummary()
  await nextTick()
  await loadTrend()
  await loadInputStats()
  await loadBadFeedback()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  inputChart?.dispose()
})
</script>

<style scoped>
.chat-manage {
  padding: 0;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  padding: 18px 20px;
  border-radius: 12px;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.stat-blue { background: linear-gradient(135deg, #1a73e8, #1565c0); }
.stat-green { background: linear-gradient(135deg, #34a853, #2d8f47); }
.stat-orange { background: linear-gradient(135deg, #ff7043, #f4511e); }
.stat-red { background: linear-gradient(135deg, #ea4335, #c5221f); }

.stat-label {
  font-size: 12px;
  opacity: 0.9;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: 800;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  border-radius: 12px;
}

.chart-canvas {
  width: 100%;
  height: 280px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-tools {
  display: flex;
  gap: 10px;
  align-items: center;
}

.session-list-card,
.bad-feedback-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.detail-body h4 {
  margin: 16px 0 10px;
  font-size: 14px;
  color: #1a1a2e;
  border-left: 3px solid #1a73e8;
  padding-left: 10px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0 4px;
}
.detail-toolbar .spacer { flex: 1; }
</style>
