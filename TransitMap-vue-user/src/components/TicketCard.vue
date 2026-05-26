<template>
  <div class="ticket-card" :class="'status-' + order.status">
    <div class="ticket-header">
      <el-tag :type="statusType" size="small">{{ statusText }}</el-tag>
      <span class="order-no">{{ order.orderNo }}</span>
      <span class="order-time">{{ formatTime(order.orderTime) }}</span>
    </div>

    <div class="ticket-body">
      <div class="ticket-route">
        <div class="route-endpoint">
          <span class="ep-label">起</span>
          <span class="ep-name">{{ order.startStationName }}</span>
        </div>
        <div class="route-arrow">
          <div class="arrow-line"></div>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          <div class="arrow-line"></div>
        </div>
        <div class="route-endpoint">
          <span class="ep-label end">终</span>
          <span class="ep-name">{{ order.endStationName }}</span>
        </div>
      </div>

      <div class="ticket-meta">
        <div class="meta-item">
          <span class="meta-val">{{ order.stationCount }}</span>
          <span class="meta-lbl">站</span>
        </div>
        <div class="meta-item">
          <span class="meta-val">{{ order.distanceKm }}</span>
          <span class="meta-lbl">公里</span>
        </div>
        <div class="meta-item">
          <span class="meta-val">{{ order.durationMinutes }}</span>
          <span class="meta-lbl">分钟</span>
        </div>
        <div class="meta-item price">
          <span class="meta-val">¥{{ order.price }}</span>
        </div>
      </div>
    </div>

    <!-- 已支付：显示二维码和退票按钮 -->
    <div v-if="order.status === 1" class="ticket-footer">
      <div class="qr-mini">
        <QrcodeVue :value="order.qrCode || ''" :size="100" level="L" />
        <span class="qr-label">进站扫码</span>
      </div>
      <div class="footer-actions">
        <p class="expire-hint">
          有效期至 {{ formatTime(order.qrExpireTime) }}
          <el-button text size="small" :loading="refreshing" @click="handleRefreshQr" class="refresh-btn">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </p>
        <el-button type="danger" text size="small" @click="$emit('refund', order)">申请退票</el-button>
      </div>
    </div>

    <!-- 退票审核中 -->
    <div v-else-if="order.status === 5" class="ticket-footer">
      <div class="footer-actions" style="width:100%">
        <p class="expire-hint" style="color:#E6A23C">退票申请审核中，请等待管理员处理</p>
        <p v-if="order.refundReason" class="expire-hint">退票原因：{{ order.refundReason }}</p>
      </div>
    </div>

    <!-- 待支付：显示支付和取消 -->
    <div v-else-if="order.status === 0" class="ticket-footer">
      <div class="footer-actions" style="width:100%">
        <p class="expire-hint">请在{{ paymentTimeoutHours }}小时内完成支付</p>
        <div>
          <el-button type="primary" size="small" @click="$emit('pay', order)">去支付</el-button>
          <el-button size="small" @click="$emit('cancel', order)">取消</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ArrowRight, Refresh } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import { ElMessage } from 'element-plus'
import { refreshQrCode } from '@/api/ticket'
import { useSystemConfig } from '@/composables/useSystemConfig'

const { getConfigValue } = useSystemConfig()

const props = defineProps({ order: Object })
const emit = defineEmits(['pay', 'cancel', 'refund', 'update-qr'])

const refreshing = ref(false)

async function handleRefreshQr() {
  refreshing.value = true
  try {
    const res = await refreshQrCode({ orderId: props.order.id })
    emit('update-qr', { orderId: props.order.id, qrCode: res.data.qrCode, qrExpireTime: res.data.qrExpireTime })
    ElMessage.success('二维码已刷新')
  } catch {
    ElMessage.error('刷新失败')
  } finally {
    refreshing.value = false
  }
}

const paymentTimeoutHours = computed(() => parseInt(getConfigValue('ticket.payment_timeout_hours', '24')) || 24)

const statusMap = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'success' },
  2: { text: '已使用', type: 'info' },
  3: { text: '已过期', type: 'info' },
  4: { text: '已退票', type: 'danger' },
  5: { text: '审核中', type: 'warning' },
}

const statusText = computed(() => statusMap[props.order.status]?.text || '未知')
const statusType = computed(() => statusMap[props.order.status]?.type || 'info')

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.ticket-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  padding: 16px;
  transition: box-shadow 0.2s;
}
.ticket-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.1); }

.ticket-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.order-no { font-size: 12px; color: #909399; font-family: monospace; }
.order-time { margin-left: auto; font-size: 12px; color: #c0c4cc; }

.ticket-body { margin-bottom: 12px; }

.ticket-route { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.route-endpoint { display: flex; align-items: center; gap: 6px; }
.ep-label {
  width: 22px; height: 22px; border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600; color: #fff;
  background: #67C23A;
}
.ep-label.end { background: #ff6b35; }
.ep-name { font-size: 16px; font-weight: 600; color: #303133; }

.route-arrow { flex: 1; display: flex; align-items: center; gap: 4px; }
.arrow-line { flex: 1; height: 1px; background: #dcdfe6; }
.arrow-icon { color: #c0c4cc; }

.ticket-meta { display: flex; gap: 20px; align-items: flex-end; }
.meta-item { display: flex; flex-direction: column; align-items: center; }
.meta-val { font-size: 16px; font-weight: 600; color: #303133; }
.meta-lbl { font-size: 11px; color: #909399; }
.meta-item.price .meta-val { color: #ff6b35; font-size: 20px; }

.ticket-footer { display: flex; align-items: center; gap: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.qr-mini { text-align: center; flex-shrink: 0; }
.qr-label { display: block; font-size: 11px; color: #909399; margin-top: 4px; }

.footer-actions { flex: 1; }
.expire-hint { font-size: 12px; color: #909399; margin: 0 0 8px; display: flex; align-items: center; gap: 4px; }
.refresh-btn { margin-left: 4px; padding: 0 4px; height: auto; font-size: 12px; color: #409eff; }
</style>
