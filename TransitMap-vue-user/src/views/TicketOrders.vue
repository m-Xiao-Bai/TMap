<template>
  <div class="ticket-orders-page">
    <header class="top-bar">
      <el-button text @click="router.push('/')">
        <el-icon><ArrowLeft /></el-icon> 返回首页
      </el-button>
      <h2>我的订单</h2>
      <div style="width:80px"></div>
    </header>

    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" size="small">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button :label="0">待支付</el-radio-button>
        <el-radio-button :label="1">已支付</el-radio-button>
        <el-radio-button :label="2">已使用</el-radio-button>
        <el-radio-button :label="5">审核中</el-radio-button>
        <el-radio-button :label="4">已退票</el-radio-button>
      </el-radio-group>
    </div>

    <div class="order-list" v-loading="loading">
      <template v-if="filteredOrders.length">
        <TicketCard
          v-for="order in filteredOrders"
          :key="order.id"
          :order="order"
          @pay="handlePay"
          @cancel="handleCancel"
          @refund="handleRefund"
          @update-qr="handleUpdateQr"
        />
      </template>
      <el-empty v-else description="暂无订单" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getMyTicketOrders, payTicketOrder, refundTicketOrder } from '@/api/ticket'
import TicketCard from '@/components/TicketCard.vue'

const router = useRouter()
const loading = ref(false)
const orders = ref([])
const statusFilter = ref('')

const filteredOrders = computed(() => {
  if (statusFilter.value === '') return orders.value
  return orders.value.filter(o => o.status === statusFilter.value)
})

async function fetchOrders() {
  loading.value = true
  try {
    const res = await getMyTicketOrders()
    orders.value = res.data || []
  } catch {
    ElMessage.error('获取订单失败')
  } finally {
    loading.value = false
  }
}

async function handlePay(order) {
  try {
    await ElMessageBox.confirm(`确认支付订单 ¥${order.price}？`, '确认支付', {
      confirmButtonText: '确认支付',
      cancelButtonText: '取消',
    })
    await payTicketOrder({ orderId: order.id })
    ElMessage.success('支付成功')
    fetchOrders()
  } catch {}
}

function handleCancel(order) {
  ElMessageBox.confirm('确定取消该订单？取消后不可恢复', '取消订单', {
    confirmButtonText: '确定取消',
    cancelButtonText: '返回',
    type: 'warning',
  }).then(async () => {
    await refundTicketOrder({ orderId: order.id, reason: '用户主动取消' })
    ElMessage.success('订单已取消')
    fetchOrders()
  }).catch(() => {})
}

async function handleRefund(order) {
  try {
    await ElMessageBox.confirm(`确定退票？退款 ¥${order.price}`, '申请退票', {
      confirmButtonText: '确认退票',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await refundTicketOrder({ orderId: order.id, reason: '用户主动退票' })
    ElMessage.success('退票成功')
    fetchOrders()
  } catch {}
}

function handleUpdateQr({ orderId, qrCode, qrExpireTime }) {
  const order = orders.value.find(o => o.id === orderId)
  if (order) {
    order.qrCode = qrCode
    order.qrExpireTime = qrExpireTime
  }
}

onMounted(() => { fetchOrders() })
</script>

<style scoped>
.ticket-orders-page {
  max-width: 640px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 24px;
}
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.top-bar h2 { margin: 0; font-size: 17px; }

.filter-bar { padding: 12px 16px; background: #fff; margin-bottom: 12px; }

.order-list { padding: 0 16px; display: flex; flex-direction: column; gap: 12px; }
</style>
