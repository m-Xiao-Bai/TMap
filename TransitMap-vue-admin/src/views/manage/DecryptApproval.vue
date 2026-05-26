<template>
  <div class="decrypt-approval">
    <div class="page-header">
      <h2><el-icon><Lock /></el-icon> 明文查看 · 待审批</h2>
      <el-button :icon="Refresh" plain @click="load">刷新</el-button>
    </div>

    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
      根据双人原则，<strong>申请人不能审批自己的申请</strong>，需要另一位超级管理员处理。所有操作均会留痕。
    </el-alert>

    <el-card v-loading="loading">
      <el-table :data="pending" stripe>
        <el-table-column prop="id" label="申请ID" width="100" />
        <el-table-column prop="sessionId" label="会话ID" width="140" />
        <el-table-column prop="requesterName" label="申请人" width="140" />
        <el-table-column prop="reason" label="申请理由" show-overflow-tooltip />
        <el-table-column prop="createTime" label="申请时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              :disabled="row.requesterId === userStore.userId"
              @click="onApprove(row, true)"
            >通过</el-button>
            <el-button
              type="danger"
              size="small"
              :disabled="row.requesterId === userStore.userId"
              @click="onApprove(row, false)"
            >拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!pending.length && !loading" class="empty">暂无待审批申请</div>
    </el-card>

    <h3 style="margin-top: 24px">我的申请记录</h3>
    <el-card>
      <el-table :data="myList" stripe>
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="sessionId" label="会话ID" width="140" />
        <el-table-column prop="reason" label="理由" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approverName" label="审批人" width="140" />
        <el-table-column prop="approverNote" label="备注" show-overflow-tooltip />
        <el-table-column prop="approveTime" label="审批时间" width="170">
          <template #default="{ row }">{{ formatTime(row.approveTime) }}</template>
        </el-table-column>
        <el-table-column prop="expireTime" label="授权到期" width="170">
          <template #default="{ row }">{{ formatTime(row.expireTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审批备注弹窗 -->
    <el-dialog v-model="approveDialogVisible" :title="approveAction ? '审批通过' : '拒绝申请'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="备注">
          <el-input v-model="approveNote" type="textarea" :rows="3"
            :placeholder="approveAction ? '可选：写明授权原因' : '可选：说明拒绝理由'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button :type="approveAction ? 'success' : 'danger'" :loading="submitting" @click="confirmApprove">
          {{ approveAction ? '确认通过（授权 24h）' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getPendingDecrypts, approveDecrypt, getMyDecryptRequests } from '@/api/chatManage'

const userStore = useUserStore()
const pending = ref([])
const myList = ref([])
const loading = ref(false)

const approveDialogVisible = ref(false)
const approveAction = ref(true)
const approveTarget = ref(null)
const approveNote = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const [p, m] = await Promise.all([getPendingDecrypts(), getMyDecryptRequests()])
    if (p.code === 200) pending.value = p.data || []
    if (m.code === 200) myList.value = m.data || []
  } finally {
    loading.value = false
  }
}

function onApprove(row, approve) {
  if (row.requesterId === userStore.userId || row.requesterId === Number(userStore.userId)) {
    ElMessage.warning('不能审批自己的申请')
    return
  }
  approveTarget.value = row
  approveAction.value = approve
  approveNote.value = ''
  approveDialogVisible.value = true
}

async function confirmApprove() {
  submitting.value = true
  try {
    const res = await approveDecrypt(approveTarget.value.id, approveAction.value, approveNote.value)
    if (res.code === 200) {
      ElMessage.success(approveAction.value ? '已通过' : '已拒绝')
      approveDialogVisible.value = false
      await load()
    }
  } finally {
    submitting.value = false
  }
}

function statusLabel(s) {
  return { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已拒绝', EXPIRED: '已过期' }[s] || s
}
function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info' }[s] || 'info'
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  return d.getFullYear() + '-' +
    String(d.getMonth() + 1).padStart(2, '0') + '-' +
    String(d.getDate()).padStart(2, '0') + ' ' +
    String(d.getHours()).padStart(2, '0') + ':' +
    String(d.getMinutes()).padStart(2, '0')
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
h3 {
  font-size: 16px;
  color: #1a1a2e;
  margin: 0 0 12px;
}
.empty {
  padding: 32px;
  text-align: center;
  color: #909399;
}
</style>
