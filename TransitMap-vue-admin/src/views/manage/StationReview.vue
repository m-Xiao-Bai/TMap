<template>
  <div class="station-review">
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>待审核站点数据</span>
          <div>
            <el-input v-model="filterCity" placeholder="筛选城市" clearable size="small"
              style="width: 150px; margin-right: 8px;" @keyup.enter="refreshList" />
            <el-button size="small" @click="refreshList">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="reviews" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="city_name" label="城市" width="100" />
        <el-table-column prop="station_name" label="站点名称" width="150" />
        <el-table-column prop="line_name" label="线路" width="120" />
        <el-table-column prop="scraped_address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="坐标" width="180">
          <template #default="{ row }">
            <el-text v-if="row.scraped_lat && row.scraped_lng" size="small">
              {{ row.scraped_lat }}, {{ row.scraped_lng }}
            </el-text>
            <el-text v-else type="info" size="small">无</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="100">
          <template #default="{ row }">
            <el-tag :type="confidenceType(row.confidence)" size="small">
              {{ row.confidence }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="task_id" label="任务ID" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text type="info" size="small">{{ row.task_id?.substring(0, 8) }}...</el-text>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="handleApprove(row.id)" :loading="row._approving">
              批准
            </el-button>
            <el-button type="danger" size="small" @click="handleReject(row.id)" :loading="row._rejecting">
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <el-button type="success" size="small" @click="handleBatchApprove" :disabled="!selectedIds.length">
            批量批准 ({{ selectedIds.length }})
          </el-button>
          <el-button type="danger" size="small" @click="handleBatchReject" :disabled="!selectedIds.length">
            批量拒绝 ({{ selectedIds.length }})
          </el-button>
        </div>
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="refreshList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getPendingReviews, approveReview, rejectReview } from '@/api/crawler'

const loading = ref(false)
const reviews = ref([])
const filterCity = ref('')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const selectedIds = ref([])

const confidenceType = (c) => {
  const map = { high: 'success', medium: 'warning', low: 'danger' }
  return map[c] || 'info'
}

const refreshList = async () => {
  loading.value = true
  try {
    const res = await getPendingReviews({
      city: filterCity.value,
      page: page.value,
      size: pageSize,
    })
    if (res.code === 200) {
      reviews.value = (res.data?.items || []).map(item => ({
        ...item,
        _approving: false,
        _rejecting: false,
      }))
      total.value = res.data?.total || 0
    }
  } catch {
    ElMessage.error('获取审核列表失败')
  } finally {
    loading.value = false
  }
}

const handleApprove = async (id) => {
  const row = reviews.value.find(r => r.id === id)
  if (row) row._approving = true
  try {
    await approveReview(id)
    ElMessage.success('已批准')
    refreshList()
  } catch {
    ElMessage.error('批准失败')
  } finally {
    if (row) row._approving = false
  }
}

const handleReject = async (id) => {
  try {
    await ElMessageBox.confirm('确定拒绝此站点数据吗？', '确认', { type: 'warning' })
  } catch { return }
  const row = reviews.value.find(r => r.id === id)
  if (row) row._rejecting = true
  try {
    await rejectReview(id)
    ElMessage.success('已拒绝')
    refreshList()
  } catch {
    ElMessage.error('拒绝失败')
  } finally {
    if (row) row._rejecting = false
  }
}

const handleBatchApprove = async () => {
  // 简单实现：逐个批准
  for (const id of selectedIds.value) {
    try { await approveReview(id) } catch {}
  }
  ElMessage.success(`已批准 ${selectedIds.value.length} 条`)
  selectedIds.value = []
  refreshList()
}

const handleBatchReject = async () => {
  try {
    await ElMessageBox.confirm(`确定拒绝 ${selectedIds.value.length} 条数据吗？`, '确认', { type: 'warning' })
  } catch { return }
  for (const id of selectedIds.value) {
    try { await rejectReview(id) } catch {}
  }
  ElMessage.success(`已拒绝 ${selectedIds.value.length} 条`)
  selectedIds.value = []
  refreshList()
}

onMounted(refreshList)
</script>

<style scoped>
.station-review {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
