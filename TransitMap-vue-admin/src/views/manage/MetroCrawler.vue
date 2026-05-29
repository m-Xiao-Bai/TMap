<template>
  <div class="metro-crawler">
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>触发爬取</span>
          <el-tag :type="serviceHealthy ? 'success' : 'danger'" size="small">
            {{ serviceHealthy ? '服务正常' : '服务不可用' }}
          </el-tag>
        </div>
      </template>

      <el-form :model="form" label-width="100px" style="max-width: 600px">
        <el-form-item label="城市名称">
          <el-input v-model="form.cityName" placeholder="如：南昌市" clearable />
        </el-form-item>
        <el-form-item label="国家">
          <el-select v-model="form.countryId" style="width: 100%">
            <el-option :value="1" label="中国" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="triggering" @click="handleTrigger"
            :disabled="!serviceHealthy || !form.cityName.trim()">
            触发爬取
          </el-button>
          <el-button @click="handleBatchTrigger" :disabled="!serviceHealthy">
            批量爬取
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>任务队列</span>
          <el-button size="small" @click="refreshTasks">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-table :data="tasks" v-loading="loadingTasks" stripe style="width: 100%">
        <el-table-column prop="task_id" label="任务ID" width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <el-text type="info" size="small">{{ row.task_id?.substring(0, 8) }}...</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="city_name" label="城市" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="200">
          <template #default="{ row }">
            <el-progress v-if="row.status === 'running'"
              :percentage="Number(row.progress_pct || 0)" :stroke-width="16" :text-inside="true" />
            <el-text v-else-if="row.status === 'completed'" type="success" size="small">100%</el-text>
            <el-text v-else type="info" size="small">-</el-text>
          </template>
        </el-table-column>
        <el-table-column label="站点统计" width="200">
          <template #default="{ row }">
            <template v-if="row.stations_inserted && row.stations_inserted !== '0'">
              <el-text size="small">
                +{{ row.stations_inserted }} 新增
                <template v-if="row.stations_pending_review && row.stations_pending_review !== '0'">
                  , {{ row.stations_pending_review }} 待审核
                </template>
              </el-text>
            </template>
            <el-text v-else type="info" size="small">-</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="current_step" label="当前步骤" width="150">
          <template #default="{ row }">
            <el-text size="small">{{ stepText(row.current_step) }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'running' || row.status === 'pending'"
              type="danger" size="small" link @click="handleCancel(row.task_id)">
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 批量爬取弹窗 -->
    <el-dialog v-model="batchDialogVisible" title="批量爬取" width="500">
      <el-input v-model="batchCities" type="textarea" :rows="6"
        placeholder="每行一个城市名，如：&#10;南昌市&#10;广州市&#10;成都市" />
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchTriggering" @click="handleBatchConfirm">
          确认爬取
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { triggerCrawl, triggerBatchCrawl, getAllTasks, cancelTask, checkCrawlerHealth } from '@/api/crawler'

const serviceHealthy = ref(false)
const triggering = ref(false)
const loadingTasks = ref(false)
const tasks = ref([])
const batchDialogVisible = ref(false)
const batchTriggering = ref(false)
const batchCities = ref('')

const form = ref({
  cityName: '',
  countryId: 1,
})

let pollTimer = null

const statusType = (status) => {
  const map = { pending: 'info', running: 'warning', completed: 'success', failed: 'danger', cancelled: 'info' }
  return map[status] || 'info'
}

const statusText = (status) => {
  const map = { pending: '排队中', running: '运行中', completed: '已完成', failed: '失败', cancelled: '已取消' }
  return map[status] || status
}

const stepText = (step) => {
  const map = {
    scraping: '数据爬取', comparing: '多源对比', llm_validating: 'LLM校验',
    geocoding: '坐标补全', quality_checking: '数据质检', db_writing: '写入数据库',
    completed: '已完成',
  }
  return map[step] || step || '-'
}

const checkHealth = async () => {
  try {
    const res = await checkCrawlerHealth()
    serviceHealthy.value = res.code === 200 && res.data?.healthy
  } catch {
    serviceHealthy.value = false
  }
}

const refreshTasks = async () => {
  loadingTasks.value = true
  try {
    const res = await getAllTasks()
    if (res.code === 200) {
      tasks.value = res.data || []
    }
  } catch (e) {
    ElMessage.error('获取任务列表失败')
  } finally {
    loadingTasks.value = false
  }
}

const handleTrigger = async () => {
  if (!form.value.cityName.trim()) return
  triggering.value = true
  try {
    const res = await triggerCrawl({
      city_name: form.value.cityName.trim(),
      country_id: form.value.countryId,
    })
    if (res.code === 200) {
      ElMessage.success(`爬取任务已触发: ${res.data.task_id}`)
      form.value.cityName = ''
      refreshTasks()
    }
  } catch (e) {
    ElMessage.error('触发失败: ' + (e.message || '未知错误'))
  } finally {
    triggering.value = false
  }
}

const handleBatchTrigger = () => {
  batchCities.value = ''
  batchDialogVisible.value = true
}

const handleBatchConfirm = async () => {
  const cities = batchCities.value.split('\n').map(c => c.trim()).filter(c => c)
  if (!cities.length) {
    ElMessage.warning('请输入至少一个城市名')
    return
  }
  batchTriggering.value = true
  try {
    const res = await triggerBatchCrawl({
      cities: cities.map(c => ({
        city_name: c,
        country_id: 1,
      })),
    })
    if (res.code === 200) {
      ElMessage.success(`已入队 ${res.data.count} 个任务`)
      batchDialogVisible.value = false
      refreshTasks()
    }
  } catch (e) {
    ElMessage.error('批量爬取失败')
  } finally {
    batchTriggering.value = false
  }
}

const handleCancel = async (taskId) => {
  try {
    await cancelTask(taskId)
    ElMessage.success('任务已取消')
    refreshTasks()
  } catch {
    ElMessage.error('取消失败')
  }
}

onMounted(() => {
  checkHealth()
  refreshTasks()
  pollTimer = setInterval(() => {
    const hasRunning = tasks.value.some(t => t.status === 'running' || t.status === 'pending')
    if (hasRunning) refreshTasks()
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.metro-crawler {
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
