<template>
  <div class="quality-dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.total_conversations || 0 }}</div>
          <div class="stat-label">总对话数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #67c23a">
            {{ ((stats.positive_rate || 0) * 100).toFixed(1) }}%
          </div>
          <div class="stat-label">好评率</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #409eff">
            {{ (stats.avg_quality_score || 0).toFixed(2) }}
          </div>
          <div class="stat-label">平均质量分</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #f56c6c">
            {{ ((stats.hallucination_rate || 0) * 100).toFixed(1) }}%
          </div>
          <div class="stat-label">幻觉率</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 意图分布 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>按意图分布</span>
          <el-select v-model="days" size="small" style="width: 120px" @change="fetchStats">
            <el-option :value="1" label="最近1天" />
            <el-option :value="7" label="最近7天" />
            <el-option :value="30" label="最近30天" />
          </el-select>
        </div>
      </template>

      <el-table :data="intentData" stripe>
        <el-table-column prop="intent" label="意图类型" width="150">
          <template #default="{ row }">
            <el-tag :type="intentType(row.intent)" size="small">{{ intentText(row.intent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="对话数" width="120" />
        <el-table-column prop="avg_score" label="平均质量分" width="150">
          <template #default="{ row }">
            <el-progress :percentage="Math.round((row.avg_score || 0) * 100)"
              :color="scoreColor(row.avg_score)" :stroke-width="14" :text-inside="true" />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!intentData.length" description="暂无数据" :image-size="60" />
    </el-card>

    <!-- 低分对话 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>低分对话（需关注）</span>
          <el-button size="small" @click="fetchLowScore">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="lowScoreItems" v-loading="loadingLowScore" stripe>
        <el-table-column prop="message_id" label="消息ID" width="80" />
        <el-table-column prop="quality_score" label="质量分" width="100">
          <template #default="{ row }">
            <el-tag :type="scoreTagType(row.quality_score)" size="small">
              {{ (row.quality_score || 0).toFixed(2) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="user_feedback" label="用户反馈" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.user_feedback === 'positive'" type="success" size="small">👍 好评</el-tag>
            <el-tag v-else-if="row.user_feedback === 'negative'" type="danger" size="small">👎 差评</el-tag>
            <el-text v-else type="info" size="small">-</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="intent_type" label="意图" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ intentText(row.intent_type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="is_hallucination" label="幻觉" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.is_hallucination" type="danger" size="small">是</el-tag>
            <el-text v-else type="info" size="small">否</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="user_message" label="用户消息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="assistant_reply" label="AI回复" min-width="200" show-overflow-tooltip />
        <el-table-column prop="created_at" label="时间" width="180" />
      </el-table>

      <el-empty v-if="!lowScoreItems.length && !loadingLowScore" description="暂无低分对话" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getQualityStats, getLowScoreConversations } from '@/api/quality'

const days = ref(7)
const loadingLowScore = ref(false)

const stats = ref({
  total_conversations: 0,
  positive_rate: 0,
  avg_quality_score: 0,
  hallucination_rate: 0,
  by_intent: {},
})

const lowScoreItems = ref([])

const intentData = computed(() => {
  const byIntent = stats.value.by_intent || {}
  return Object.entries(byIntent).map(([intent, data]) => ({
    intent,
    count: data.count || 0,
    avg_score: data.avg_score || 0,
  }))
})

const intentType = (intent) => {
  const map = { route: 'primary', chat: 'success', order: 'warning' }
  return map[intent] || 'info'
}

const intentText = (intent) => {
  const map = { route: '路线规划', chat: '通用对话', order: '下单' }
  return map[intent] || intent || '-'
}

const scoreColor = (score) => {
  if (score >= 0.8) return '#67c23a'
  if (score >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

const scoreTagType = (score) => {
  if (score >= 0.8) return 'success'
  if (score >= 0.6) return 'warning'
  return 'danger'
}

const fetchStats = async () => {
  try {
    const res = await getQualityStats({ days: days.value })
    if (res.code === 200) {
      stats.value = res.data || {}
    }
  } catch {}
}

const fetchLowScore = async () => {
  loadingLowScore.value = true
  try {
    const res = await getLowScoreConversations({ page: 1, size: 50 })
    if (res.code === 200) {
      lowScoreItems.value = res.data?.items || []
    }
  } catch {} finally {
    loadingLowScore.value = false
  }
}

onMounted(() => {
  fetchStats()
  fetchLowScore()
})
</script>

<style scoped>
.quality-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-cards {
  margin-bottom: 0;
}

.stat-card {
  border-radius: 8px;
  text-align: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.section-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
</style>
