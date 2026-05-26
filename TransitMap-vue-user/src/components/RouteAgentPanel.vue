<template>
  <div
    class="agent-panel-wrap"
    :data-panel-state="store.panelState"
    :style="{ width: panelWidth }"
  >
    <!-- 拖拽手柄 -->
    <div
      v-if="isDesktop && store.panelState === 'expanded'"
      class="resize-handle"
      @mousedown="onDragStart"
      @touchstart="onDragStart"
    />

    <!-- 面板头部 -->
    <div class="panel-header">
      <div class="header-left">
        <span class="title">🚇 路线助手</span>
        <ChatSessionDropdown
          ref="sessionDropdownRef"
          :current-session-id="store.currentSessionId"
          @select="onSelectSession"
          @create="onCreateSession"
        />
        <span v-if="connState === 'reconnecting'" class="conn-tip reconnecting">重连中...</span>
        <span v-else-if="connState === 'closed'" class="conn-tip closed">已断开</span>
        <span
          v-if="quota && !quota.unlimited"
          class="quota-tip"
          :class="{ exhausted: quota.remaining <= 0, low: quota.remaining > 0 && quota.remaining <= 3 }"
          :title="`未登录用户每天 ${quota.limit} 条，登录后无限制`"
        >
          剩 {{ quota.remaining }}/{{ quota.limit }}
        </span>
      </div>
      <div class="header-actions">
        <button class="header-btn" title="新会话 (Ctrl+N)" @click="onNewSession">
          <svg viewBox="0 0 24 24" width="16"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" fill="currentColor"/></svg>
        </button>
        <button v-if="store.panelState !== 'fullscreen'" class="header-btn" title="全屏 (Ctrl+B)" @click="store.setState('fullscreen')">
          <svg viewBox="0 0 24 24" width="16"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z" fill="currentColor"/></svg>
        </button>
        <button v-if="store.panelState === 'fullscreen'" class="header-btn" title="退出全屏 (Ctrl+B)" @click="store.setState('expanded')">
          <svg viewBox="0 0 24 24" width="16"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z" fill="currentColor"/></svg>
        </button>
        <button class="header-btn" title="收起 (Esc)" @click="store.setState('collapsed')">
          <svg viewBox="0 0 24 24" width="16"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/></svg>
        </button>
      </div>
    </div>

    <!-- 消息列表 -->
    <ChatMessageList
      ref="msgListRef"
      :messages="ws.messages.value"
      :conn-state="connState.value"
      @order="openPurchase"
      @chip="onChipPick"
      @regenerate="onRegenerate"
      @feedback="onFeedback"
      @command="onCommand"
    />

    <!-- 输入框 -->
    <ChatInputBox
      :is-streaming="ws.isStreaming.value"
      :welcome-chips="welcomeChips"
      @send="onSend"
      @stop="ws.stop()"
    />

    <!-- 首次引导 -->
    <ChatOnboarding v-if="store.panelState !== 'collapsed'" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAgentChatStore } from '@/store/agentChat'
import { usePanelLayout } from '@/composables/usePanelLayout'
import { useAgentWebSocket } from '@/composables/useAgentWebSocket'
import { useGeolocation } from '@/composables/useGeolocation'
import { useChatHotkeys } from '@/composables/useChatHotkeys'
import { getTodaySession, getWelcomeChips, getMessages, newSession as apiNewSession, feedback as apiFeedback, getQuota, requestCity } from '@/api/agent'
import { useSystemConfig } from '@/composables/useSystemConfig'
import ChatMessageList from './ChatMessageList.vue'
import ChatInputBox from './ChatInputBox.vue'
import ChatSessionDropdown from './ChatSessionDropdown.vue'
import ChatOnboarding from './ChatOnboarding.vue'

const emit = defineEmits(['openPurchase'])

const store = useAgentChatStore()
const { currentSessionId, pendingOrderCard } = storeToRefs(store)
const { panelWidth, isDesktop, onDragStart } = usePanelLayout(store)
const { getConfigJson } = useSystemConfig()
const router = useRouter()

const msgListRef = ref(null)
const sessionDropdownRef = ref(null)
const welcomeChips = ref([])
const quota = ref(null)

// WebSocket（传响应式 sessionId，会话切换时自动重连）
const ws = useAgentWebSocket(currentSessionId)
const connState = ws.connState

// 定位
const geo = useGeolocation()

// 加载会话历史
async function loadHistory(sessionId) {
  try {
    const res = await getMessages(sessionId)
    if (res.code === 200 && Array.isArray(res.data)) {
      ws.clearMessages()
      for (const m of res.data) {
        const msg = {
          id: m.id,
          role: m.role,
          content: m.content,
          inputMethod: m.inputMethod,
          feedback: m.feedback
        }
        if (m.extras) {
          try {
            const ex = typeof m.extras === 'string' ? JSON.parse(m.extras) : m.extras
            msg.extras = ex
            if (Array.isArray(ex?.chips)) {
              msg.chips = ex.chips
            }
          } catch {}
        }
        ws.messages.value.push(msg)
      }
    }
  } catch (e) {
    console.warn('Failed to load message history', e)
  }
}

// 初始化会话
onMounted(async () => {
  try {
    const res = await getTodaySession()
    if (res.code === 200 && res.data) {
      store.currentSessionId = res.data.id
      // 加载历史，再连接
      await loadHistory(res.data.id)
      ws.connect()
    } else {
      ElMessage.warning('会话初始化失败：' + (res?.message || '未知原因'))
    }
  } catch (e) {
    console.warn('Failed to init session', e)
    ElMessage.warning('会话初始化失败，部分功能可能不可用')
  }

  // 加载欢迎快捷词
  try {
    const chipsRes = await getWelcomeChips()
    if (chipsRes.code === 200) {
      welcomeChips.value = chipsRes.data || []
    }
  } catch (e) {
    welcomeChips.value = getConfigJson('agent.welcome_chips') || ['我要去...', '附近地铁', '换乘建议']
  }

  // 预获取定位
  geo.getLocation()

  // 拉取配额
  refreshQuota()
})

async function refreshQuota() {
  try {
    const res = await getQuota()
    if (res.code === 200) {
      quota.value = res.data
    }
  } catch (e) {
    // 静默失败
  }
}

// 监听订单卡片回插
watch(pendingOrderCard, (order) => {
  if (!order) return
  ws.pushMessage({
    role: 'assistant',
    content: '已为你下单成功 🎫',
    extras: { kind: 'ORDER_CARD', payload: order }
  })
  store.consumeOrderCard()
})

// 流式结束时刷新配额（处理后端先返回 error 再 done 的情况）
watch(() => ws.isStreaming.value, (streaming) => {
  if (!streaming) refreshQuota()
})

async function onSend(text, inputMethod = 'text') {
  // 0. 前端先做一次配额检查（避免无意义的 WS 请求）
  if (quota.value && !quota.value.unlimited && quota.value.remaining <= 0) {
    ElMessage.warning(`未登录用户每天最多 ${quota.value.limit} 条消息，请登录后继续`)
    return
  }

  // 1. 确保有会话
  if (!store.currentSessionId) {
    try {
      const res = await getTodaySession()
      if (res.code === 200 && res.data) {
        store.currentSessionId = res.data.id
        ws.connect()
      } else {
        ElMessage.error('无法创建会话，请检查登录状态')
        return
      }
    } catch (e) {
      console.error('Failed to create session', e)
      ElMessage.error('会话创建失败：' + (e?.message || '未知错误'))
      return
    }
  }

  // 2. 确保 WS 连接
  if (ws.connState.value !== 'open') {
    ws.connect()
    const connected = await waitForConnection(5000)
    if (!connected) {
      ElMessage.error('连接服务器失败，请检查网络或刷新页面重试')
      // send 内部会显示错误气泡，所以这里只用 toast，不再调用 send
      return
    }
  }

  // 3. 发送
  const location = geo.location.value
  ws.send(text, location, inputMethod)

  // 4. 异步刷新配额
  refreshQuota()
}

function waitForConnection(timeoutMs = 5000) {
  return new Promise((resolve) => {
    if (ws.connState.value === 'open') return resolve(true)
    const start = Date.now()
    const timer = setInterval(() => {
      if (ws.connState.value === 'open') {
        clearInterval(timer)
        resolve(true)
      } else if (Date.now() - start > timeoutMs) {
        clearInterval(timer)
        resolve(false)
      }
    }, 100)
  })
}

function onChipPick(chip) {
  onSend(chip, 'chip')
}

/**
 * 处理 chip 触发的命令（不发消息，执行业务动作）
 *   notify_admin:cityName  → 通知管理员添加该城市
 *   view_order             → 跳转到订单详情页
 *   end_session            → 收起面板
 */
async function onCommand(cmd) {
  if (!cmd) return
  if (cmd.name === 'end_session') {
    store.setState('collapsed')
    return
  }
  if (cmd.name === 'view_order') {
    // 跳转到我的订单页（带 orderId 时优先精确跳转，否则跳列表）
    if (cmd.payload) {
      router.push({ path: '/tickets', query: { orderId: cmd.payload } })
    } else {
      router.push('/tickets')
    }
    return
  }
  if (cmd.name === 'notify_admin') {
    const cityName = cmd.payload || '该城市'
    try {
      await ElMessageBox.confirm(
        `是否通知管理员尽快接入「${cityName}」的地铁线路？\n\n通知后你会被排入需求队列，开通后会优先通知你。`,
        '请管理员接入城市',
        { confirmButtonText: '通知管理员', cancelButtonText: '不用了', type: 'info' }
      )
      const res = await requestCity(cityName)
      if (res.code === 200) {
        ws.pushMessage({
          role: 'assistant',
          content: `✅ 已通知管理员，请耐心等待。「${cityName}」开通后会通过站内消息通知你。`,
          chips: ['好的，结束对话']
        })
      }
    } catch (e) {
      // 用户点取消 / 接口失败：插一条结束对话提示
      ws.pushMessage({
        role: 'assistant',
        content: '好的，本次对话已结束。需要时可随时叫我 🚇'
      })
    }
  }
}

function onRegenerate(messageId) {
  ws.regenerate(messageId)
}

async function onFeedback(messageId, fb) {
  try {
    await apiFeedback(messageId, { feedback: fb })
    const msg = ws.messages.value.find(m => m.id === messageId)
    if (msg) msg.feedback = fb
  } catch (e) {
    console.warn('Feedback failed', e)
  }
}

function openPurchase(route) {
  emit('openPurchase', route)
}

async function onNewSession() {
  try {
    const res = await apiNewSession()
    if (res.code === 200 && res.data) {
      ws.clearMessages()
      store.currentSessionId = res.data.id
      sessionDropdownRef.value?.refresh()
    }
  } catch (e) {
    console.warn('Failed to create new session', e)
  }
}

async function onSelectSession(id) {
  if (id === store.currentSessionId) return
  ws.clearMessages()
  store.currentSessionId = id
  await loadHistory(id)
}

async function onCreateSession(id) {
  ws.clearMessages()
  store.currentSessionId = id
}

// 全局快捷键
useChatHotkeys({
  togglePanel: () => store.togglePanel(),
  collapsePanel: () => {
    if (store.panelState !== 'collapsed') store.setState('collapsed')
  },
  focusInput: () => {
    document.querySelector('.agent-panel-wrap .chat-textarea')?.focus()
  },
  newSession: onNewSession,
  toggleFullscreen: () => {
    store.setState(store.panelState === 'fullscreen' ? 'expanded' : 'fullscreen')
  }
})
</script>

<style scoped>
.agent-panel-wrap {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  background: #fff;
  border-left: 1px solid #e8eaed;
  position: fixed;
  right: 0;
  top: 56px;
  overflow: hidden;
  transition: width 0.25s ease;
  z-index: 100;
}

.agent-panel-wrap[data-panel-state="collapsed"] {
  width: 0 !important;
  border-left: none;
  overflow: hidden;
}

.agent-panel-wrap[data-panel-state="fullscreen"] {
  position: fixed;
  inset: 0;
  z-index: 1000;
  width: 100% !important;
  border-left: none;
}

.resize-handle {
  position: absolute;
  left: -3px;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  z-index: 10;
  transition: background 0.15s;
}

.resize-handle:hover,
.resize-handle:active {
  background: rgba(26, 115, 232, 0.3);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #e8eaed;
  background: #fafbfc;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.conn-tip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.conn-tip.reconnecting {
  background: #fff3cd;
  color: #856404;
}

.conn-tip.closed {
  background: #f8d7da;
  color: #721c24;
}

.quota-tip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #e8f0fe;
  color: #1a73e8;
  font-weight: 600;
  cursor: help;
}

.quota-tip.low {
  background: #fff3cd;
  color: #856404;
}

.quota-tip.exhausted {
  background: #f8d7da;
  color: #721c24;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  transition: all 0.15s;
}

.header-btn:hover {
  background: #f0f0f0;
  color: #1a73e8;
}

/* 移动端底部 sheet */
@media (max-width: 600px) {
  .agent-panel-wrap {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 60vh;
    border-left: none;
    border-top: 1px solid #e8eaed;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -4px 20px rgba(0,0,0,0.1);
    z-index: 999;
    width: 100% !important;
  }

  .agent-panel-wrap[data-panel-state="collapsed"] {
    transform: translateY(calc(100% - 48px));
  }

  .resize-handle {
    display: none;
  }
}
</style>
