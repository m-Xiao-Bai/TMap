<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="购票确认"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 步骤1：确认路线 + 票数 + 验证码 -->
    <div v-if="step === 1" class="purchase-step">
      <div class="route-summary">
        <div class="summary-route">
          <span class="station-name">{{ routeData?.startName }}</span>
          <el-icon class="arrow"><ArrowRight /></el-icon>
          <span class="station-name">{{ routeData?.endName }}</span>
        </div>
        <div class="summary-meta">
          <div class="meta-item">
            <span class="meta-label">途经</span>
            <span class="meta-value">{{ routeData?.stationCount }} 站</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">距离</span>
            <span class="meta-value">{{ routeData?.distance }} 公里</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">耗时</span>
            <span class="meta-value">约 {{ routeData?.duration }} 分钟</span>
          </div>
        </div>
        <div class="summary-price">
          <span class="price-label">单张票价</span>
          <span class="price-value">¥ {{ routeData?.price }}</span>
        </div>
      </div>

      <!-- 票数选择 -->
      <div class="quantity-row">
        <span class="quantity-label">购买张数</span>
        <el-input-number v-model="quantity" :min="1" :max="10" :step="1" size="default" />
        <span class="quantity-unit">张</span>
        <span class="quantity-total">合计：<b>¥ {{ (routeData?.price || 0) * quantity }}</b></span>
      </div>

      <!-- 验证码 -->
      <div class="captcha-row">
        <span class="captcha-label">验证码</span>
        <el-input v-model="captchaCode" placeholder="请输入验证码" size="default" style="width:140px" @keyup.enter="handleCreate" />
        <img v-if="captchaImage" :src="captchaImage" class="captcha-img" title="点击刷新验证码" @click="refreshCaptcha" />
        <el-button v-else text size="small" @click="refreshCaptcha">获取验证码</el-button>
      </div>

      <!-- 途经站点 -->
      <div class="station-list-wrap" v-if="routeData?.stationNames?.length">
        <div class="list-header" @click="showStations = !showStations">
          <span>途经站点 ({{ routeData.stationNames.length }})</span>
          <el-icon><ArrowDown v-if="!showStations" /><ArrowUp v-else /></el-icon>
        </div>
        <transition name="el-zoom-in-top">
          <div v-show="showStations" class="station-timeline">
            <div v-for="(name, idx) in routeData.stationNames" :key="idx" class="timeline-item">
              <div class="timeline-dot" :class="{ start: idx === 0, end: idx === routeData.stationNames.length - 1 }"></div>
              <span class="timeline-name">{{ name }}</span>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <!-- 步骤2：模拟支付 -->
    <div v-else-if="step === 2" class="purchase-step">
      <div class="pay-info">
        <el-icon class="pay-icon" color="#E6A23C" :size="48"><Warning /></el-icon>
        <h3>模拟支付</h3>
        <p class="pay-desc">本系统为演示系统，点击下方按钮完成模拟支付</p>
        <div class="pay-amount">
          <span>{{ orderResults.length }} 张票，合计：</span>
          <span class="amount">¥ {{ totalPrice }}</span>
        </div>
      </div>
    </div>

    <!-- 步骤3：支付成功，显示二维码 -->
    <div v-else-if="step === 3" class="purchase-step">
      <div class="success-info">
        <el-icon class="success-icon" color="#67C23A" :size="48"><CircleCheck /></el-icon>
        <h3>支付成功</h3>
      </div>

      <!-- 单张票：直接显示 -->
      <template v-if="orderResults.length === 1">
        <div class="qr-section">
          <QrcodeVue :value="orderResults[0].qrCode || ''" :size="180" level="M" />
          <p class="qr-hint">请凭此二维码进站乘车</p>
          <p class="qr-expire">有效期至：{{ formatTime(qrExpireTime) }}</p>
        </div>
        <div class="order-detail">
          <div class="detail-row">
            <span class="detail-label">订单号</span>
            <span class="detail-value">{{ orderResults[0].orderNo }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">票价</span>
            <span class="detail-value price">¥ {{ orderResults[0].price }}</span>
          </div>
        </div>
      </template>

      <!-- 多张票：Tab切换 -->
      <template v-else>
        <el-tabs v-model="activeQrTab" type="card" class="qr-tabs">
          <el-tab-pane v-for="(order, idx) in orderResults" :key="order.orderId" :label="'第' + (idx+1) + '张'" :name="String(idx)">
            <div class="qr-section">
              <QrcodeVue :value="order.qrCode || ''" :size="160" level="M" />
              <p class="qr-hint">第 {{ idx + 1 }} 张票 - 二维码</p>
              <p class="qr-expire">有效期至：{{ formatTime(qrExpireTime) }}</p>
            </div>
            <div class="order-detail">
              <div class="detail-row">
                <span class="detail-label">订单号</span>
                <span class="detail-value">{{ order.orderNo }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">票价</span>
                <span class="detail-value price">¥ {{ order.price }}</span>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <template #footer>
      <template v-if="step === 1">
        <el-button @click="$emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确认购票</el-button>
      </template>
      <template v-else-if="step === 2">
        <el-button @click="step = 1">返回</el-button>
        <el-button type="primary" :loading="paying" @click="handlePay">确认支付</el-button>
      </template>
      <template v-else-if="step === 3">
        <el-button type="primary" @click="handleDone">完成</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, ArrowDown, ArrowUp, Warning, CircleCheck } from '@element-plus/icons-vue'
import QrcodeVue from 'qrcode.vue'
import { createTicketOrder, payTicketOrder } from '@/api/ticket'
import { getCaptchaImage } from '@/api/user'
import { useSystemConfig } from '@/composables/useSystemConfig'

const { ensureLoaded, getConfigValue } = useSystemConfig()

const props = defineProps({
  visible: Boolean,
  routeData: Object,
})
const emit = defineEmits(['update:visible', 'purchased'])

const step = ref(1)
const creating = ref(false)
const paying = ref(false)
const showStations = ref(false)
const quantity = ref(1)
const orderResults = ref([])
const activeQrTab = ref('0')

// 验证码
const captchaImage = ref('')
const captchaKey = ref('')
const captchaCode = ref('')

const totalPrice = computed(() => {
  return (props.routeData?.price || 0) * quantity.value
})

const qrExpireTime = computed(() => {
  if (!orderResults.value.length) return null
  const hours = parseInt(getConfigValue('ticket.qr_validity_hours', '24')) || 24
  return new Date(Date.now() + hours * 60 * 60 * 1000)
})

// 弹窗打开时刷新验证码
watch(() => props.visible, (val) => {
  if (val) {
    step.value = 1
    quantity.value = 1
    orderResults.value = []
    captchaCode.value = ''
    activeQrTab.value = '0'
    ensureLoaded()
    refreshCaptcha()
  }
})

async function refreshCaptcha() {
  try {
    const res = await getCaptchaImage()
    captchaImage.value = res.data.captchaImage
    captchaKey.value = res.data.captchaKey
  } catch {
    captchaImage.value = ''
    captchaKey.value = ''
  }
}

async function handleCreate() {
  if (!captchaCode.value.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  creating.value = true
  try {
    const res = await createTicketOrder({
      startStationId: props.routeData.startId,
      endStationId: props.routeData.endId,
      quantity: quantity.value,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value.trim(),
    })
    orderResults.value = res.data
    step.value = 2
  } catch (e) {
    // 验证码失败时刷新
    refreshCaptcha()
    captchaCode.value = ''
  } finally {
    creating.value = false
  }
}

async function handlePay() {
  paying.value = true
  try {
    // 逐张支付
    for (const order of orderResults.value) {
      await payTicketOrder({ orderId: order.orderId })
    }
    step.value = 3
    emit('purchased')
  } catch (e) {
    // error handled by interceptor
  } finally {
    paying.value = false
  }
}

function handleDone() {
  emit('update:visible', false)
}

function formatTime(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.purchase-step { min-height: 200px; }

.route-summary { padding: 16px; background: #f5f7fa; border-radius: 8px; }
.summary-route { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.station-name { font-size: 18px; font-weight: 600; color: #303133; }
.arrow { color: #909399; font-size: 18px; }

.summary-meta { display: flex; gap: 24px; margin-bottom: 12px; }
.meta-item { display: flex; flex-direction: column; }
.meta-label { font-size: 12px; color: #909399; }
.meta-value { font-size: 14px; color: #303133; font-weight: 500; }

.summary-price { display: flex; align-items: center; gap: 8px; padding-top: 12px; border-top: 1px solid #e4e7ed; }
.price-label { font-size: 14px; color: #606266; }
.price-value { font-size: 24px; font-weight: 700; color: #ff6b35; }

/* 票数选择 */
.quantity-row {
  display: flex; align-items: center; gap: 10px;
  margin-top: 16px; padding: 12px 16px;
  background: #fafafa; border-radius: 8px;
}
.quantity-label { font-size: 14px; color: #606266; flex-shrink: 0; }
.quantity-unit { font-size: 14px; color: #909399; }
.quantity-total { margin-left: auto; font-size: 14px; color: #303133; }
.quantity-total b { color: #ff6b35; font-size: 18px; }

/* 验证码 */
.captcha-row {
  display: flex; align-items: center; gap: 10px;
  margin-top: 12px; padding: 12px 16px;
  background: #fafafa; border-radius: 8px;
}
.captcha-label { font-size: 14px; color: #606266; flex-shrink: 0; }
.captcha-img {
  height: 36px; cursor: pointer; border-radius: 4px;
  border: 1px solid #dcdfe6;
}

.station-list-wrap { margin-top: 16px; border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; }
.list-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 16px; cursor: pointer; font-size: 14px; color: #606266; background: #fafafa; }
.list-header:hover { background: #f0f2f5; }

.station-timeline { padding: 12px 16px; max-height: 240px; overflow-y: auto; }
.timeline-item { display: flex; align-items: center; gap: 10px; padding: 4px 0; position: relative; }
.timeline-item:not(:last-child)::after { content: ''; position: absolute; left: 5px; top: 16px; width: 1px; height: calc(100% - 4px); background: #dcdfe6; }
.timeline-dot { width: 11px; height: 11px; border-radius: 50%; background: #c0c4cc; flex-shrink: 0; }
.timeline-dot.start { background: #67C23A; }
.timeline-dot.end { background: #ff6b35; }
.timeline-name { font-size: 13px; color: #303133; }

.pay-info { text-align: center; padding: 20px 0; }
.pay-icon { margin-bottom: 12px; }
.pay-info h3 { margin: 0 0 8px; font-size: 18px; }
.pay-desc { color: #909399; font-size: 13px; margin: 0 0 16px; }
.pay-amount { font-size: 16px; }
.amount { font-size: 28px; font-weight: 700; color: #ff6b35; }

.success-info { text-align: center; margin-bottom: 16px; }
.success-icon { margin-bottom: 8px; }
.success-info h3 { margin: 0; font-size: 18px; color: #67C23A; }

.qr-section { text-align: center; padding: 16px; background: #f5f7fa; border-radius: 8px; margin-bottom: 16px; }
.qr-hint { margin: 8px 0 4px; font-size: 14px; color: #303133; }
.qr-expire { margin: 0; font-size: 12px; color: #909399; }

.order-detail { padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; }
.detail-row { display: flex; justify-content: space-between; padding: 6px 0; }
.detail-label { color: #909399; font-size: 13px; }
.detail-value { font-size: 13px; color: #303133; font-weight: 500; }
.detail-value.price { color: #ff6b35; font-weight: 700; font-size: 16px; }

.qr-tabs { margin-top: 8px; }
</style>
