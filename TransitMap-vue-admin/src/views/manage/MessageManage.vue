<template>
  <div class="message-manage">
    <div class="layout">
      <!-- 左侧分类栏 -->
      <div class="category-sidebar">
        <div class="sidebar-title">消息分类</div>
        <div
          v-for="cat in categories"
          :key="cat.key"
          class="category-item"
          :class="{ active: activeCategory === cat.key }"
          @click="selectCategory(cat.key)"
        >
          <div class="cat-left">
            <el-icon :size="18" :color="cat.color"><component :is="cat.icon" /></el-icon>
            <span class="cat-label">{{ cat.label }}</span>
          </div>
          <el-badge v-if="cat.unread > 0" :value="cat.unread" :max="99" class="cat-badge" />
        </div>

        <el-divider />

        <div
          class="category-item"
          :class="{ active: activeCategory === 'all' }"
          @click="selectCategory('all')"
        >
          <div class="cat-left">
            <el-icon :size="18"><Grid /></el-icon>
            <span class="cat-label">全部消息</span>
          </div>
          <span class="cat-count">{{ totalCount }}</span>
        </div>
      </div>

      <!-- 右侧消息列表 -->
      <div class="message-list">
        <!-- 工具栏 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <span class="toolbar-title">{{ currentCategoryLabel }}</span>
            <el-tag v-if="unreadCount > 0" type="danger" size="small" effect="plain">
              {{ unreadCount }} 条未读
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-select v-model="filterRead" placeholder="已读状态" clearable size="small" style="width:100px" @change="fetchData">
              <el-option label="未读" :value="0" />
              <el-option label="已读" :value="1" />
            </el-select>
            <el-button size="small" type="primary" plain @click="handleMarkAllRead" :loading="marking">
              全部已读
            </el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="message-items" v-loading="loading">
          <div
            v-for="msg in tableData"
            :key="msg.id"
            class="message-item"
            :class="{ unread: msg.isRead === 0 }"
            @click="showDetail(msg)"
          >
            <div class="msg-icon">
              <el-icon :size="20" :color="getTypeConfig(msg.type).color">
                <component :is="getTypeConfig(msg.type).icon" />
              </el-icon>
            </div>
            <div class="msg-body">
              <div class="msg-header">
                <span class="msg-title">{{ msg.title }}</span>
                <el-tag :type="getTypeConfig(msg.type).tagType" size="small" effect="plain">
                  {{ getTypeConfig(msg.type).label }}
                </el-tag>
              </div>
              <div class="msg-content">{{ msg.content }}</div>
              <div class="msg-footer">
                <span class="msg-time">{{ msg.createTime }}</span>
                <span v-if="msg.isRead === 0" class="msg-dot">●</span>
              </div>
            </div>
          </div>

          <el-empty v-if="!loading && tableData.length === 0" description="暂无消息" :image-size="80" />
        </div>

        <!-- 分页 -->
        <div class="pagination">
          <el-pagination
            :current-page="pageNum"
            :page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- 消息详情弹窗 -->
    <el-drawer
      v-model="detailVisible"
      :title="detailMessage?.title || '消息详情'"
      direction="rtl"
      size="450px"
    >
      <template v-if="detailMessage">
        <div class="detail-section">
          <div class="detail-row">
            <span class="detail-label">类型</span>
            <el-tag :type="getTypeConfig(detailMessage.type).tagType" size="small">
              {{ getTypeConfig(detailMessage.type).label }}
            </el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">状态</span>
            <el-tag :type="detailMessage.isRead === 0 ? 'danger' : 'success'" size="small">
              {{ detailMessage.isRead === 0 ? '未读' : '已读' }}
            </el-tag>
          </div>
          <div class="detail-row">
            <span class="detail-label">时间</span>
            <span>{{ detailMessage.createTime }}</span>
          </div>
          <div v-if="detailMessage.userId" class="detail-row">
            <span class="detail-label">用户ID</span>
            <span>{{ detailMessage.userId }}</span>
          </div>
          <div v-if="detailMessage.target" class="detail-row">
            <span class="detail-label">目标</span>
            <span>{{ targetLabel(detailMessage.target) }}</span>
          </div>
        </div>

        <el-divider />

        <div class="detail-content">
          <div class="detail-label">内容</div>
          <div class="detail-text">{{ detailMessage.content }}</div>
        </div>

        <div v-if="detailMessage.extra" class="detail-extra">
          <div class="detail-label">附加数据</div>
          <pre class="detail-json">{{ formatJson(detailMessage.extra) }}</pre>
        </div>

        <div class="detail-actions">
          <el-button v-if="detailMessage.isRead === 0" type="primary" @click="handleRead(detailMessage)">
            标为已读
          </el-button>
          <el-button type="danger" plain @click="handleDelete(detailMessage)">
            删除消息
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Grid, ShoppingTicket, Warning, Bell, ChatDotRound,
  Ticket, Money, Timer, CircleCheck, Document,
  UserFilled, Setting, Position
} from '@element-plus/icons-vue'
import { getMessageList, markMessageRead, markAllMessagesRead, deleteMessage } from '@/api/message'

// 消息分类配置
const categories = ref([
  {
    key: 'order',
    label: '订单消息',
    icon: ShoppingTicket,
    color: '#409eff',
    types: ['ORDER_CREATED', 'ORDER_PAID', 'ORDER_USED', 'ORDER_EXPIRED', 'ORDER_REFUNDED', 'REFUND_PENDING'],
    unread: 0,
  },
  {
    key: 'user',
    label: '用户消息',
    icon: ChatDotRound,
    color: '#e6a23c',
    types: ['USER_CONTACT', 'USER_FEEDBACK', 'AGENT_CITY_REQUEST'],
    unread: 0,
  },
  {
    key: 'system',
    label: '系统通知',
    icon: Bell,
    color: '#67c23a',
    types: ['SYSTEM_NOTICE', 'SYSTEM_UPDATE', 'SYSTEM_CONFIG'],
    unread: 0,
  },
  {
    key: 'error',
    label: '系统异常',
    icon: Warning,
    color: '#f56c6c',
    types: ['SYSTEM_ERROR', 'SYSTEM_WARNING'],
    unread: 0,
  },
  {
    key: 'crawler',
    label: '爬虫通知',
    icon: Position,
    color: '#909399',
    types: ['CRAWLER_COMPLETE', 'CRAWLER_FAILED', 'CRAWLER_REVIEW'],
    unread: 0,
  },
])

const activeCategory = ref('all')
const tableData = ref([])
const loading = ref(false)
const marking = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalCount = ref(0)
const filterRead = ref(null)
const detailVisible = ref(false)
const detailMessage = ref(null)

// 当前分类标签
const currentCategoryLabel = computed(() => {
  if (activeCategory.value === 'all') return '全部消息'
  const cat = categories.value.find(c => c.key === activeCategory.value)
  return cat ? cat.label : '全部消息'
})

// 当前分类未读数
const unreadCount = computed(() => {
  if (activeCategory.value === 'all') {
    return categories.value.reduce((sum, c) => sum + c.unread, 0)
  }
  const cat = categories.value.find(c => c.key === activeCategory.value)
  return cat ? cat.unread : 0
})

// 获取消息类型配置
const getTypeConfig = (type) => {
  const configs = {
    ORDER_CREATED:    { label: '订单创建', color: '#409eff', tagType: 'primary', icon: Ticket },
    ORDER_PAID:       { label: '订单支付', color: '#67c23a', tagType: 'success', icon: Money },
    ORDER_USED:       { label: '车票核销', color: '#67c23a', tagType: 'success', icon: CircleCheck },
    ORDER_EXPIRED:    { label: '订单过期', color: '#909399', tagType: 'info', icon: Timer },
    ORDER_REFUNDED:   { label: '退票完成', color: '#e6a23c', tagType: 'warning', icon: Money },
    REFUND_PENDING:   { label: '退票申请', color: '#e6a23c', tagType: 'warning', icon: Ticket },
    USER_CONTACT:     { label: '用户来信', color: '#e6a23c', tagType: 'warning', icon: ChatDotRound },
    USER_FEEDBACK:    { label: '用户反馈', color: '#e6a23c', tagType: 'warning', icon: ChatDotRound },
    AGENT_CITY_REQUEST: { label: '城市请求', color: '#409eff', tagType: 'primary', icon: Position },
    SYSTEM_NOTICE:    { label: '系统通知', color: '#67c23a', tagType: 'success', icon: Bell },
    SYSTEM_UPDATE:    { label: '系统更新', color: '#409eff', tagType: 'primary', icon: Setting },
    SYSTEM_CONFIG:    { label: '配置变更', color: '#409eff', tagType: 'primary', icon: Setting },
    SYSTEM_ERROR:     { label: '系统异常', color: '#f56c6c', tagType: 'danger', icon: Warning },
    SYSTEM_WARNING:   { label: '系统警告', color: '#e6a23c', tagType: 'warning', icon: Warning },
    CRAWLER_COMPLETE: { label: '爬取完成', color: '#67c23a', tagType: 'success', icon: Position },
    CRAWLER_FAILED:   { label: '爬取失败', color: '#f56c6c', tagType: 'danger', icon: Position },
    CRAWLER_REVIEW:   { label: '待审核', color: '#e6a23c', tagType: 'warning', icon: Document },
  }
  return configs[type] || { label: type, color: '#909399', tagType: 'info', icon: Document }
}

// 目标标签
const targetLabel = (target) => {
  const map = { 1: '普通用户', 2: '管理员', 3: '超级管理员', 4: '最高管理员' }
  return map[target] || `角色 ${target}`
}

// 格式化 JSON
const formatJson = (val) => {
  if (!val) return ''
  try {
    const obj = typeof val === 'string' ? JSON.parse(val) : val
    return JSON.stringify(obj, null, 2)
  } catch {
    return val
  }
}

// 选择分类
const selectCategory = (key) => {
  activeCategory.value = key
  pageNum.value = 1
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filterRead.value !== null && filterRead.value !== '') {
      params.isRead = filterRead.value
    }

    // 根据分类过滤类型
    if (activeCategory.value !== 'all') {
      const cat = categories.value.find(c => c.key === activeCategory.value)
      if (cat) {
        params.types = cat.types.join(',')
      }
    }

    const res = await getMessageList(params)
    if (res.code === 200) {
      tableData.value = res.data.records || []
      total.value = Number(res.data.total) || 0
    }

    // 更新未读数（简单实现，实际应从后端获取）
    await updateUnreadCounts()
  } catch { } finally { loading.value = false }
}

// 更新各分类未读数
const updateUnreadCounts = async () => {
  try {
    // 这里应该调用后端接口获取各分类未读数
    // 简单实现：从全部消息中统计
    const res = await getMessageList({ pageNum: 1, pageSize: 1000, isRead: 0 })
    if (res.code === 200) {
      const unreadMsgs = res.data.records || []
      totalCount.value = Number(res.data.total) || 0
      for (const cat of categories.value) {
        cat.unread = unreadMsgs.filter(m => cat.types.includes(m.type)).length
      }
    }
  } catch { }
}

// 显示详情
const showDetail = async (msg) => {
  detailMessage.value = msg
  detailVisible.value = true
  // 自动标记已读
  if (msg.isRead === 0) {
    await handleRead(msg)
  }
}

// 标记已读
const handleRead = async (row) => {
  try {
    await markMessageRead(row.id)
    row.isRead = 1
    await updateUnreadCounts()
  } catch { }
}

// 全部已读
const handleMarkAllRead = async () => {
  marking.value = true
  try {
    await markAllMessagesRead()
    ElMessage.success('已全部标记为已读')
    fetchData()
  } catch { } finally { marking.value = false }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该消息？', '提示', { type: 'warning' })
    await deleteMessage(row.id)
    ElMessage.success('已删除')
    detailVisible.value = false
    fetchData()
  } catch { }
}

const handleCurrentChange = (val) => { pageNum.value = val; fetchData() }
const handleSizeChange = (val) => { pageSize.value = val; pageNum.value = 1; fetchData() }

onMounted(() => { fetchData() })
</script>

<style scoped>
.message-manage {
  height: calc(100vh - 120px);
}

.layout {
  display: flex;
  height: 100%;
  gap: 16px;
}

/* 左侧分类栏 */
.category-sidebar {
  width: 200px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.category-item:hover {
  background: #f5f7fa;
}

.category-item.active {
  background: #ecf5ff;
  color: #409eff;
}

.cat-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cat-label {
  font-size: 13px;
}

.cat-count {
  font-size: 12px;
  color: #909399;
}

/* 右侧消息列表 */
.message-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 消息条目 */
.message-items {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.message-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.message-item:hover {
  background: #f5f7fa;
}

.message-item.unread {
  background: #f0f9ff;
}

.msg-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.msg-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-content {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.msg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.msg-time {
  font-size: 12px;
  color: #909399;
}

.msg-dot {
  color: #409eff;
  font-size: 10px;
}

/* 分页 */
.pagination {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
}

/* 详情弹窗 */
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-label {
  font-size: 13px;
  color: #909399;
  min-width: 60px;
}

.detail-content {
  margin-top: 8px;
}

.detail-text {
  font-size: 14px;
  color: #303133;
  line-height: 1.8;
  margin-top: 8px;
  white-space: pre-wrap;
}

.detail-extra {
  margin-top: 16px;
}

.detail-json {
  font-size: 12px;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  margin-top: 8px;
  overflow-x: auto;
  max-height: 300px;
}

.detail-actions {
  margin-top: 24px;
  display: flex;
  gap: 8px;
}
</style>
