<template>
  <!-- 权限不足提示 -->
  <div v-if="!canAccess" class="no-permission">
    <el-result icon="warning" title="权限不足" sub-title="缓存管理仅超级管理员及以上可操作">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>

  <div v-else class="cache-manage">
    <div class="page-header">
      <h2><el-icon><Coin /></el-icon> 缓存管理</h2>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="fetchStatus">刷新状态</el-button>
        <el-button type="danger" :icon="Delete" :loading="clearingAll" @click="handleClearAll">
          清除全部缓存
        </el-button>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
      <template #title>
        清除缓存会导致相关数据首次访问时重新从数据库加载，可能短暂影响响应速度。Token 缓存清除后所有用户需重新登录。
      </template>
    </el-alert>

    <!-- 总览 -->
    <div class="summary-bar" v-if="!loading">
      <div class="summary-item">
        <span class="summary-num">{{ totalCount }}</span>
        <span class="summary-label">缓存键总数</span>
      </div>
      <div class="summary-item">
        <span class="summary-num">{{ categoryCount }}</span>
        <span class="summary-label">缓存分类</span>
      </div>
    </div>

    <!-- 分类卡片 -->
    <div class="cache-grid" v-loading="loading">
      <div
        v-for="cat in categories"
        :key="cat.key"
        class="cache-card"
        :class="{ danger: cat.danger, empty: (statusData[cat.key] || 0) === 0 }"
      >
        <div class="card-icon" :style="{ background: cat.color }">
          <el-icon :size="24"><component :is="cat.icon" /></el-icon>
        </div>
        <div class="card-body">
          <div class="card-title">{{ cat.label }}</div>
          <div class="card-pattern">{{ cat.pattern }}</div>
          <div class="card-count">
            <span class="count-num">{{ statusData[cat.key] || 0 }}</span>
            <span class="count-unit">个键</span>
          </div>
        </div>
        <div class="card-action">
          <el-button
            :type="cat.danger ? 'danger' : 'warning'"
            :disabled="(statusData[cat.key] || 0) === 0"
            :loading="clearingKey === cat.key"
            @click="handleClear(cat)"
          >
            清除
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Coin, Refresh, Delete, Picture, Message, Key, Flag, OfficeBuilding, Van, MapLocation, FolderDelete } from '@element-plus/icons-vue'
import { getCacheStatus, clearCacheCategory } from '@/api/cacheManage'
import { usePermission } from '@/composables/usePermission'

const { canEditAllFields } = usePermission()
const canAccess = computed(() => canEditAllFields.value)

const loading = ref(false)
const clearingAll = ref(false)
const clearingKey = ref(null)
const statusData = ref({})

const categories = [
  {
    key: 'captcha_image',
    label: '图片验证码',
    pattern: 'transitMap:captcha:image:*',
    icon: Picture,
    color: '#409EFF',
    danger: false,
  },
  {
    key: 'captcha_email',
    label: '邮箱验证码',
    pattern: 'transitMap:captcha:email:*',
    icon: Message,
    color: '#67C23A',
    danger: false,
  },
  {
    key: 'token',
    label: '用户 Token',
    pattern: 'transitMap:token:*',
    icon: Key,
    color: '#E6A23C',
    danger: true,
  },
  {
    key: 'country',
    label: '国家数据',
    pattern: 'transitMap:COUNTRY:* + transitMap:cache:country_list',
    icon: Flag,
    color: '#409EFF',
    danger: false,
  },
  {
    key: 'city',
    label: '城市数据',
    pattern: 'transitMap:cache:city_list',
    icon: OfficeBuilding,
    color: '#67C23A',
    danger: false,
  },
  {
    key: 'metro_line',
    label: '地铁线路',
    pattern: 'transitMap:cache:metro_line_list',
    icon: Van,
    color: '#409EFF',
    danger: false,
  },
  {
    key: 'metro_station',
    label: '地铁站点',
    pattern: 'transitMap:cache:metro_station_list:*',
    icon: MapLocation,
    color: '#E6A23C',
    danger: false,
  },
  {
    key: 'all',
    label: '全部缓存',
    pattern: 'transitMap:*',
    icon: FolderDelete,
    color: '#F56C6C',
    danger: true,
  },
]

const categoryCount = computed(() => categories.length)
const totalCount = computed(() => {
  let sum = 0
  for (const cat of categories) {
    if (cat.key !== 'all') {
      sum += statusData.value[cat.key] || 0
    }
  }
  return sum
})

async function fetchStatus() {
  loading.value = true
  try {
    const res = await getCacheStatus()
    statusData.value = res.data || {}
  } catch {
    statusData.value = {}
  } finally {
    loading.value = false
  }
}

async function handleClear(cat) {
  const count = statusData.value[cat.key] || 0
  if (count === 0) return

  const title = cat.danger ? '危险操作' : '确认清除'
  const type = cat.danger ? 'error' : 'warning'
  const confirmText = cat.danger ? '我已了解风险，确认清除' : '确认清除'

  try {
    await ElMessageBox.confirm(
      cat.danger
        ? `即将清除「${cat.label}」下的 ${count} 个缓存键。${cat.key === 'token' ? '所有用户的登录状态将失效，需要重新登录。' : '此操作不可撤销，确定继续？'}`
        : `即将清除「${cat.label}」下的 ${count} 个缓存键，确定继续？`,
      title,
      { confirmButtonText: confirmText, cancelButtonText: '取消', type }
    )
  } catch {
    return
  }

  clearingKey.value = cat.key
  try {
    const res = await clearCacheCategory(cat.key)
    const deleted = res.data?.deleted || 0
    ElMessage.success(`已清除 ${deleted} 个缓存键`)
    await fetchStatus()
  } catch {
    ElMessage.error('清除失败')
  } finally {
    clearingKey.value = null
  }
}

async function handleClearAll() {
  const count = totalCount.value
  if (count === 0) {
    ElMessage.info('当前没有任何缓存数据')
    return
  }

  try {
    await ElMessageBox.confirm(
      `即将清除所有 ${count} 个缓存键，包括用户 Token（所有用户需重新登录）。此操作不可撤销，确定继续？`,
      '危险操作：清除全部缓存',
      { confirmButtonText: '我已了解风险，确认清除全部', cancelButtonText: '取消', type: 'error' }
    )
  } catch {
    return
  }

  clearingAll.value = true
  try {
    const res = await clearCacheCategory('all')
    const deleted = res.data?.deleted || 0
    ElMessage.success(`已清除全部 ${deleted} 个缓存键`)
    await fetchStatus()
  } catch {
    ElMessage.error('清除失败')
  } finally {
    clearingAll.value = false
  }
}

onMounted(() => {
  fetchStatus()
})
</script>

<style scoped>
.cache-manage {
  padding: 0;
}

/* ── 页头 ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-actions {
  display: flex;
  gap: 8px;
}

/* ── 总览栏 ── */
.summary-bar {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9eb 100%);
  border-radius: 12px;
  border: 1px solid #e4e7ed;
}
.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.summary-num {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.summary-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

/* ── 卡片网格 ── */
.cache-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

/* ── 卡片 ── */
.cache-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  transition: all 0.2s;
}
.cache-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}
.cache-card.danger {
  border-color: #fde2e2;
  background: #fef0f0;
}
.cache-card.danger:hover {
  box-shadow: 0 4px 16px rgba(245, 108, 108, 0.12);
}
.cache-card.empty {
  opacity: 0.6;
}

/* ── 图标 ── */
.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

/* ── 内容 ── */
.card-body {
  flex: 1;
  min-width: 0;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.card-pattern {
  font-size: 11px;
  color: #909399;
  font-family: 'Cascadia Code', 'Fira Code', monospace;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-count {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.count-num {
  font-size: 22px;
  font-weight: 700;
  color: #409EFF;
}
.cache-card.danger .count-num {
  color: #F56C6C;
}
.cache-card.empty .count-num {
  color: #c0c4cc;
}
.count-unit {
  font-size: 12px;
  color: #909399;
}

/* ── 操作按钮 ── */
.card-action {
  flex-shrink: 0;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .cache-grid {
    grid-template-columns: 1fr;
  }
  .summary-bar {
    justify-content: center;
  }
}
</style>
