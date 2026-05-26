<template>
  <div class="order-card">
    <div class="order-header">
      <div class="order-icon">🎫</div>
      <div class="order-title">
        <div class="order-status">下单成功</div>
        <div class="order-no">订单号 {{ order.orderNo || order.id }}</div>
      </div>
      <div class="order-amount">¥{{ totalAmount }}</div>
    </div>

    <div class="order-route">
      <span class="dot start"></span>
      <span class="name">{{ order.startName || order.startStationName }}</span>
      <svg viewBox="0 0 24 24" width="14" class="arrow">
        <path d="M16.01 11H4v2h12.01v3L20 12l-3.99-4v3z" fill="currentColor"/>
      </svg>
      <span class="dot end"></span>
      <span class="name">{{ order.endName || order.endStationName }}</span>
    </div>

    <div class="order-meta">
      <span class="meta-item">
        <span class="meta-label">张数</span>
        <span class="meta-value">{{ order.quantity || 1 }}</span>
      </span>
      <span class="meta-item">
        <span class="meta-label">单价</span>
        <span class="meta-value">¥{{ order.price }}</span>
      </span>
      <span class="meta-item">
        <span class="meta-label">状态</span>
        <span class="meta-value status-badge">{{ statusText }}</span>
      </span>
    </div>

    <div class="order-actions">
      <button class="action-btn primary" @click="onViewDetail">
        <svg viewBox="0 0 24 24" width="14">
          <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z" fill="currentColor"/>
        </svg>
        查看订单
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  order: { type: Object, required: true }
})

const router = useRouter()

const totalAmount = computed(() => {
  const price = props.order.price || 0
  const qty = props.order.quantity || 1
  return (price * qty).toFixed(2)
})

const statusText = computed(() => {
  const s = props.order.status
  if (s === 'paid' || s === 2) return '已支付'
  if (s === 'unpaid' || s === 1) return '待支付'
  if (s === 'cancelled' || s === 0) return '已取消'
  return '已下单'
})

function onViewDetail() {
  router.push('/tickets')
}
</script>

<style scoped>
.order-card {
  margin: 8px 0 8px 40px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fff7e6, #fff);
  border: 1px solid #ffe0a1;
  border-radius: 12px;
  max-width: 400px;
}

.order-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.order-icon {
  font-size: 22px;
}

.order-title {
  flex: 1;
  min-width: 0;
}

.order-status {
  font-size: 14px;
  font-weight: 700;
  color: #1d1d1f;
}

.order-no {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  font-family: ui-monospace, monospace;
  word-break: break-all;
}

.order-amount {
  font-size: 18px;
  font-weight: 800;
  color: #ea580c;
}

.order-route {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot.start { background: #34a853; }
.dot.end { background: #ea4335; }

.name {
  color: #1d1d1f;
  font-weight: 600;
}

.arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

.order-meta {
  display: flex;
  gap: 14px;
  padding: 10px 0;
  border-top: 1px dashed #ffe0a1;
  border-bottom: 1px dashed #ffe0a1;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 10px;
  color: #909399;
}

.meta-value {
  font-size: 13px;
  font-weight: 700;
  color: #1d1d1f;
}

.status-badge {
  color: #16a34a;
}

.order-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}

.action-btn.primary {
  background: #ea580c;
  color: #fff;
}

.action-btn.primary:hover {
  background: #c2410c;
}
</style>
