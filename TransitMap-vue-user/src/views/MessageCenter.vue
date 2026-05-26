<template>
  <div class="message-center">
    <header class="nav-bar">
      <div class="nav-left" @click="$router.push('/')">
        <el-icon :size="22" color="#409eff"><Aim /></el-icon>
        <span class="nav-title">城市轨道交通</span>
      </div>
      <span class="page-title">消息中心</span>
      <div class="nav-right">
        <el-button v-if="unreadCount > 0" type="primary" link @click="handleMarkAllRead">全部已读</el-button>
        <el-icon class="nav-icon" @click="$router.push('/')"><HomeFilled /></el-icon>
      </div>
    </header>

    <div class="content">
      <!-- 消息列表 -->
      <div class="msg-list" v-loading="loading">
        <div v-if="messages.length === 0 && !loading" class="empty">暂无消息</div>
        <div v-for="msg in messages" :key="msg.id"
             class="msg-item" :class="{ unread: msg.isRead === 0 }"
             @click="handleRead(msg)">
          <div class="msg-header">
            <el-tag :type="tagType(msg.type)" size="small">{{ typeLabel(msg.type) }}</el-tag>
            <span class="msg-time">{{ msg.createTime }}</span>
          </div>
          <div class="msg-title">{{ msg.title }}</div>
          <div class="msg-content">{{ msg.content }}</div>
        </div>
        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          style="margin-top:16px;justify-content:center"
          @current-change="fetchMessages"
        />
      </div>

      <!-- 联系管理员 -->
      <div class="contact-section">
        <div class="contact-title">联系管理员</div>
        <el-input
          v-model="contactContent"
          type="textarea"
          :rows="3"
          placeholder="描述您遇到的问题..."
          maxlength="500"
          show-word-limit
        />
        <el-button type="primary" :loading="sending" @click="handleSendContact"
                   style="margin-top:10px;width:100%">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim, HomeFilled } from '@element-plus/icons-vue'
import { getMessageList, getMessageUnreadCount, markMessageRead, markAllMessagesRead, sendContactMessage } from '@/api/message'

const messages = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const unreadCount = ref(0)
const contactContent = ref('')
const sending = ref(false)

const typeLabel = (type) => {
  const map = {
    ORDER_CREATED: '下单', ORDER_PAID: '支付', ORDER_USED: '核销',
    ORDER_EXPIRED: '过期', ORDER_REFUNDED: '退票', REFUND_PENDING: '退票申请',
    USER_CONTACT: '我的来信', SYSTEM_ERROR: '系统'
  }
  return map[type] || type
}

const tagType = (type) => {
  if (type === 'ORDER_CREATED') return 'primary'
  if (type === 'ORDER_PAID' || type === 'ORDER_USED') return 'success'
  if (type === 'ORDER_EXPIRED' || type === 'SYSTEM_ERROR') return 'danger'
  if (type === 'REFUND_PENDING' || type === 'ORDER_REFUNDED') return 'warning'
  return 'info'
}

const fetchMessages = async () => {
  loading.value = true
  try {
    const res = await getMessageList({ page: page.value, size: pageSize.value })
    messages.value = res.data.records || []
    total.value = res.data.total || 0
  } catch { } finally { loading.value = false }
}

const fetchUnreadCount = async () => {
  try {
    const res = await getMessageUnreadCount()
    unreadCount.value = res.data.count || 0
  } catch { }
}

const handleRead = async (msg) => {
  if (msg.isRead === 0) {
    try {
      await markMessageRead(msg.id)
      msg.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch { }
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllMessagesRead()
    messages.value.forEach(m => m.isRead = 1)
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch { }
}

const handleSendContact = async () => {
  if (!contactContent.value.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  sending.value = true
  try {
    await sendContactMessage(contactContent.value.trim())
    ElMessage.success('消息已发送')
    contactContent.value = ''
  } catch { } finally { sending.value = false }
}

let pollTimer = null
onMounted(() => {
  fetchMessages()
  fetchUnreadCount()
  pollTimer = setInterval(fetchUnreadCount, 30000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped>
.message-center { min-height: 100vh; background: #f5f7fa; }
.nav-bar {
  height: 56px; background: #fff; display: flex; align-items: center;
  justify-content: space-between; padding: 0 20px;
  border-bottom: 1px solid #ebeef5; position: sticky; top: 0; z-index: 10;
}
.nav-left { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.nav-title { font-size: 16px; font-weight: 600; color: #303133; }
.page-title { font-size: 15px; font-weight: 500; color: #606266; }
.nav-right { display: flex; align-items: center; gap: 12px; }
.nav-icon { cursor: pointer; color: #909399; font-size: 20px; }
.nav-icon:hover { color: #409eff; }

.content { max-width: 700px; margin: 16px auto; padding: 0 16px; }
.msg-list { background: #fff; border-radius: 12px; padding: 16px; min-height: 200px; }
.empty { text-align: center; color: #909399; padding: 60px 0; }
.msg-item {
  padding: 14px 16px; border-radius: 8px; margin-bottom: 10px;
  cursor: pointer; transition: background 0.2s;
}
.msg-item:hover { background: #f5f7fa; }
.msg-item.unread { background: #ecf5ff; border-left: 3px solid #409eff; }
.msg-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.msg-time { font-size: 12px; color: #909399; }
.msg-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.msg-content { font-size: 13px; color: #606266; line-height: 1.5; white-space: pre-wrap; }

.contact-section {
  background: #fff; border-radius: 12px; padding: 16px; margin-top: 16px;
}
.contact-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
</style>
