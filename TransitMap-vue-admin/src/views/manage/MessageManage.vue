<template>
  <div class="message-manage">
    <div class="toolbar">
      <el-select v-model="filterType" placeholder="消息类型" clearable style="width:150px" @change="fetchData">
        <el-option label="订单创建" value="ORDER_CREATED" />
        <el-option label="订单支付" value="ORDER_PAID" />
        <el-option label="车票核销" value="ORDER_USED" />
        <el-option label="订单过期" value="ORDER_EXPIRED" />
        <el-option label="退票申请" value="REFUND_PENDING" />
        <el-option label="退票完成" value="ORDER_REFUNDED" />
        <el-option label="用户来信" value="USER_CONTACT" />
        <el-option label="系统异常" value="SYSTEM_ERROR" />
      </el-select>
      <el-select v-model="filterRead" placeholder="已读状态" clearable style="width:120px" @change="fetchData">
        <el-option label="未读" :value="0" />
        <el-option label="已读" :value="1" />
      </el-select>
      <el-button type="primary" @click="handleMarkAllRead" :loading="marking">全部已读</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe max-height="calc(100vh - 260px)">
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="tagType(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="180" />
      <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isRead === 0 ? 'danger' : 'info'" size="small">
            {{ row.isRead === 0 ? '未读' : '已读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button v-if="row.isRead === 0" type="primary" link size="small" @click="handleRead(row)">已读</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="config.pageSizeOptions"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMessageList, markMessageRead, markAllMessagesRead, deleteMessage } from '@/api/message'
import { useSystemConfig } from '@/composables/useSystemConfig'

const { state: config } = useSystemConfig()

const tableData = ref([])
const loading = ref(false)
const marking = ref(false)
const pageNum = ref(1)
const pageSize = ref(config.defaultPageSize)
const total = ref(0)
const filterType = ref('')
const filterRead = ref(null)

const typeLabel = (type) => {
  const map = {
    ORDER_CREATED: '订单创建', ORDER_PAID: '订单支付', ORDER_USED: '车票核销',
    ORDER_EXPIRED: '订单过期', ORDER_REFUNDED: '退票完成', REFUND_PENDING: '退票申请',
    USER_CONTACT: '用户来信', SYSTEM_ERROR: '系统异常'
  }
  return map[type] || type
}

const tagType = (type) => {
  if (type === 'SYSTEM_ERROR') return 'danger'
  if (type === 'USER_CONTACT') return 'warning'
  if (type.startsWith('ORDER_') || type === 'REFUND_PENDING') return 'primary'
  return 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filterType.value) params.type = filterType.value
    if (filterRead.value !== null && filterRead.value !== '') params.isRead = filterRead.value
    const res = await getMessageList(params)
    if (res.code === 200) {
      tableData.value = res.data.records || []
      total.value = Number(res.data.total) || 0
    }
  } catch { } finally { loading.value = false }
}

const handleCurrentChange = (val) => { pageNum.value = val; fetchData() }
const handleSizeChange = (val) => { pageSize.value = val; pageNum.value = 1; fetchData() }

const handleRead = async (row) => {
  try {
    await markMessageRead(row.id)
    row.isRead = 1
  } catch { }
}

const handleMarkAllRead = async () => {
  marking.value = true
  try {
    await markAllMessagesRead()
    ElMessage.success('已全部标记为已读')
    fetchData()
  } catch { } finally { marking.value = false }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该消息？', '提示', { type: 'warning' })
    await deleteMessage(row.id)
    ElMessage.success('已删除')
    fetchData()
  } catch { }
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
