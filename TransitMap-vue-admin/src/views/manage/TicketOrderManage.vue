<template>
  <div class="manage-page">
    <div class="page-header">
      <h2><el-icon><Ticket /></el-icon> 订单管理</h2>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card" v-for="s in statItems" :key="s.key">
        <div class="stat-val">{{ stats[s.key] ?? '-' }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-input v-model="searchOrderNo" placeholder="搜索订单号" clearable style="width:200px" @clear="fetchOrders" @keyup.enter="fetchOrders">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width:140px" @change="fetchOrders">
        <el-option label="待支付" :value="0" />
        <el-option label="已支付" :value="1" />
        <el-option label="已使用" :value="2" />
        <el-option label="已过期" :value="3" />
        <el-option label="已退票" :value="4" />
        <el-option label="退票审核中" :value="5" />
      </el-select>
      <el-button type="primary" @click="fetchOrders">查询</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="orders" border stripe v-loading="loading" style="width:100%">
      <el-table-column prop="orderNo" label="订单号" min-width="160" />
      <el-table-column label="用户ID" width="100">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column label="起始站" min-width="100">
        <template #default="{ row }">{{ row.startStationName }}</template>
      </el-table-column>
      <el-table-column label="终点站" min-width="100">
        <template #default="{ row }">{{ row.endStationName }}</template>
      </el-table-column>
      <el-table-column prop="stationCount" label="站数" width="60" align="center" />
      <el-table-column label="票价" width="80" align="center">
        <template #default="{ row }">
          <span style="color:#ff6b35;font-weight:600">¥{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type" size="small">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.orderTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="showDetail(row)">详情</el-button>
          <el-button v-if="row.status === 5" type="warning" text size="small" @click="openRefundReview(row)">审核</el-button>
          <el-button v-if="canManageOrder" type="success" text size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button v-if="canManageOrder" type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="订单详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="起始站">{{ detail.startStationName }}</el-descriptions-item>
          <el-descriptions-item label="终点站">{{ detail.endStationName }}</el-descriptions-item>
          <el-descriptions-item label="站数">{{ detail.stationCount }} 站</el-descriptions-item>
          <el-descriptions-item label="票价"><span style="color:#ff6b35;font-weight:600">¥{{ detail.price }}</span></el-descriptions-item>
          <el-descriptions-item label="距离">{{ detail.distanceKm }} km</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.durationMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMap[detail.status]?.type" size="small">{{ statusMap[detail.status]?.text }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(detail.orderTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间" v-if="detail.payTime">{{ formatTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="退票时间" v-if="detail.refundTime">{{ formatTime(detail.refundTime) }}</el-descriptions-item>
          <el-descriptions-item label="退票原因" v-if="detail.refundReason">{{ detail.refundReason }}</el-descriptions-item>
          <el-descriptions-item label="进站时间" v-if="detail.entryTime">{{ formatTime(detail.entryTime) }}</el-descriptions-item>
          <el-descriptions-item label="出站时间" v-if="detail.exitTime">{{ formatTime(detail.exitTime) }}</el-descriptions-item>
          <el-descriptions-item label="过期时间" v-if="detail.qrExpireTime">{{ formatTime(detail.qrExpireTime) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.stationNames" style="margin-top:16px">
          <h4 style="margin:0 0 8px;font-size:14px">途经站点</h4>
          <div class="detail-stations">
            <el-tag v-for="(name, idx) in parseJson(detail.stationNames)" :key="idx" size="small" style="margin:2px">{{ name }}</el-tag>
          </div>
        </div>

        <div v-if="detail.lineNames" style="margin-top:12px">
          <h4 style="margin:0 0 8px;font-size:14px">经过线路</h4>
          <div class="detail-stations">
            <el-tag v-for="(name, idx) in parseJson(detail.lineNames)" :key="idx" type="warning" size="small" style="margin:2px">{{ name }}</el-tag>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑订单" width="560px" destroy-on-close close-on-click-modal="false">
      <el-form v-if="editForm" :model="editForm" label-width="90px">
        <!-- 票价 -->
        <el-form-item label="票价">
          <el-input-number v-model="editForm.price" :min="1" :max="99" :disabled="!canManageOrder" />
          <span style="margin-left:8px;color:#909399;font-size:12px">元</span>
        </el-form-item>

        <!-- 起始站 -->
        <el-form-item label="起始站">
          <el-select
            v-model="editForm.startStationId"
            filterable
            placeholder="选择起始站"
            :disabled="!canManageOrder"
            style="width:100%"
          >
            <el-option
              v-for="s in allStations"
              :key="s.id"
              :label="s.stationName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <!-- 终点站 -->
        <el-form-item label="终点站">
          <el-select
            v-model="editForm.endStationId"
            filterable
            placeholder="选择终点站"
            :disabled="!canManageOrder"
            style="width:100%"
          >
            <el-option
              v-for="s in allStations"
              :key="s.id"
              :label="s.stationName"
              :value="s.id"
            />
          </el-select>
          <p v-if="canManageOrder && editForm.startStationId !== editForm._origStartId || editForm.endStationId !== editForm._origEndId"
             style="margin:4px 0 0;font-size:12px;color:#E6A23C">
            修改起止站后将自动重新规划路线和票价
          </p>
        </el-form-item>

        <!-- 状态 -->
        <el-form-item label="状态">
          <el-select v-model="editForm.status" :disabled="!canManageOrder" style="width:100%">
            <el-option label="待支付" :value="0" />
            <el-option label="已支付" :value="1" />
            <el-option label="已使用" :value="2" />
            <el-option label="已过期" :value="3" />
            <el-option label="已退票" :value="4" />
            <el-option label="退票审核中" :value="5" />
          </el-select>
        </el-form-item>

        <!-- 进站时间 -->
        <el-form-item label="进站时间">
          <el-date-picker
            v-model="editForm.entryTime"
            type="datetime"
            placeholder="选择进站时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm"
            style="width:100%"
          />
        </el-form-item>

        <!-- 出站时间 -->
        <el-form-item label="出站时间">
          <el-date-picker
            v-model="editForm.exitTime"
            type="datetime"
            placeholder="选择出站时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 退票审核弹窗 -->
    <el-dialog v-model="refundReviewVisible" title="退票审核" width="440px" destroy-on-close close-on-click-modal="false">
      <template v-if="refundReviewOrder">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ refundReviewOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ refundReviewOrder.userId }}</el-descriptions-item>
          <el-descriptions-item label="行程">{{ refundReviewOrder.startStationName }} → {{ refundReviewOrder.endStationName }}</el-descriptions-item>
          <el-descriptions-item label="票价"><span style="color:#ff6b35;font-weight:600">¥{{ refundReviewOrder.price }}</span></el-descriptions-item>
          <el-descriptions-item label="退票原因">{{ refundReviewOrder.refundReason || '无' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button type="danger" :loading="refundReviewing" @click="handleRefundReview(2)">拒绝退票</el-button>
        <el-button type="success" :loading="refundReviewing" @click="handleRefundReview(1)">批准退票</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Ticket, Search } from '@element-plus/icons-vue'
import { getTicketOrderList, getTicketOrderDetail, getTicketOrderStats, updateTicketOrder, deleteTicketOrder, approveRefund } from '@/api/ticketOrder'
import { getMetroStationList } from '@/api/metroStation'
import { usePermission } from '@/composables/usePermission'
import { useSystemConfig } from '@/composables/useSystemConfig'

const { canEditAllFields: canManageOrder } = usePermission()
const { state: config } = useSystemConfig()

const loading = ref(false)
const orders = ref([])
const pageNum = ref(1)
const pageSize = ref(config.defaultPageSize)
const total = ref(0)
const searchOrderNo = ref('')
const filterStatus = ref(null)
const drawerVisible = ref(false)
const detail = ref(null)

const stats = reactive({ total: 0, unpaid: 0, paid: 0, used: 0, expired: 0, refunded: 0, refundPending: 0 })

const statItems = [
  { key: 'total', label: '总订单' },
  { key: 'unpaid', label: '待支付' },
  { key: 'paid', label: '已支付' },
  { key: 'used', label: '已使用' },
  { key: 'expired', label: '已过期' },
  { key: 'refunded', label: '已退票' },
  { key: 'refundPending', label: '审核中' },
]

const statusMap = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'success' },
  2: { text: '已使用', type: '' },
  3: { text: '已过期', type: 'info' },
  4: { text: '已退票', type: 'danger' },
  5: { text: '退票审核中', type: 'warning' },
}

// ── 站点数据 ──
const allStations = ref([])

async function loadStations() {
  try {
    const res = await getMetroStationList({ pageNum: 1, pageSize: 9999 })
    allStations.value = res.data?.records || []
  } catch {}
}

// ── 订单列表 ──
async function fetchOrders() {
  loading.value = true
  try {
    const res = await getTicketOrderList({ pageNum: pageNum.value, pageSize: pageSize.value, orderNo: searchOrderNo.value || undefined, status: filterStatus.value ?? undefined })
    const data = res.data
    orders.value = data.records || []
    total.value = Number(data.total) || 0
  } catch {
    ElMessage.error('获取订单失败')
  } finally { loading.value = false }
}

function handleCurrentChange(val) { pageNum.value = val; fetchOrders() }
function handleSizeChange(val) { pageSize.value = val; pageNum.value = 1; fetchOrders() }

async function fetchStats() {
  try {
    const res = await getTicketOrderStats()
    Object.assign(stats, res.data)
  } catch {}
}

async function showDetail(row) {
  try {
    const res = await getTicketOrderDetail(row.id)
    detail.value = res.data
    drawerVisible.value = true
  } catch {
    ElMessage.error('获取详情失败')
  }
}

// ── 编辑弹窗 ──
const editDialogVisible = ref(false)
const editForm = ref(null)
const editSubmitting = ref(false)

function openEditDialog(row) {
  editForm.value = {
    id: row.id,
    price: row.price,
    startStationId: row.startStationId,
    endStationId: row.endStationId,
    status: row.status,
    entryTime: row.entryTime ? formatDateTime(row.entryTime) : null,
    exitTime: row.exitTime ? formatDateTime(row.exitTime) : null,
    _origStartId: row.startStationId,
    _origEndId: row.endStationId,
  }
  editDialogVisible.value = true
}

async function handleEditSubmit() {
  const form = editForm.value
  if (!form) return

  if (form.startStationId === form.endStationId) {
    ElMessage.warning('起始站和终点站不能相同')
    return
  }

  editSubmitting.value = true
  try {
    const payload = {}
    if (canManageOrder.value) {
      payload.price = form.price
      payload.startStationId = form.startStationId
      payload.endStationId = form.endStationId
      payload.status = form.status
    }
    if (form.entryTime) payload.entryTime = form.entryTime
    if (form.exitTime) payload.exitTime = form.exitTime

    await updateTicketOrder(form.id, payload)
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    fetchOrders()
    fetchStats()
  } catch {
    ElMessage.error('修改失败')
  } finally { editSubmitting.value = false }
}

// ── 退票审核 ──
const refundReviewVisible = ref(false)
const refundReviewOrder = ref(null)
const refundReviewing = ref(false)

function openRefundReview(row) {
  refundReviewOrder.value = row
  refundReviewVisible.value = true
}

async function handleRefundReview(action) {
  refundReviewing.value = true
  try {
    await approveRefund(refundReviewOrder.value.id, action)
    ElMessage.success(action === 1 ? '已批准退票' : '已拒绝退票')
    refundReviewVisible.value = false
    fetchOrders()
    fetchStats()
  } catch {
    ElMessage.error('操作失败')
  } finally { refundReviewing.value = false }
}

// ── 删除 ──
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除订单 ${row.orderNo}？删除后不可恢复`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteTicketOrder(row.id)
    ElMessage.success('删除成功')
    fetchOrders()
    fetchStats()
  } catch {}
}

// ── 工具 ──
function parseJson(json) {
  if (!json) return []
  try { return JSON.parse(json) } catch { return [] }
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDateTime(t) {
  if (!t) return null
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => { fetchOrders(); fetchStats(); loadStations() })
</script>

<style scoped>
.manage-page { padding: 4px 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; display: flex; align-items: center; gap: 8px; }

.stats-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat-card {
  flex: 1; min-width: 100px; background: #fff; border-radius: 8px;
  padding: 12px 16px; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.stat-val { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }

.filter-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }

.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }

.detail-stations { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
