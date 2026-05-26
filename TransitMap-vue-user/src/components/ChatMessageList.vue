<template>
  <div
    class="message-list"
    ref="listRef"
    role="log"
    aria-live="polite"
    aria-label="对话内容"
    @scroll="onScroll"
  >
    <!-- 空状态 -->
    <div v-if="!messages.length" class="empty-state">
      <div class="empty-icon" aria-hidden="true">🚇</div>
      <div v-if="welcomeTitle" class="empty-text">{{ welcomeTitle }}</div>
      <div v-else class="empty-text">你好！我是路线助手</div>
      <div v-if="welcomeHint" class="empty-hint">{{ welcomeHint }}</div>
      <div v-else class="empty-hint">告诉我你想去哪儿，我帮你规划地铁路线</div>
    </div>

    <!-- 顶部连接状态条 -->
    <div v-if="connState === 'reconnecting'" class="state-banner warn" role="status">
      <span class="dot pulse" aria-hidden="true"></span>
      网络断开，正在重连…
    </div>
    <div v-else-if="connState === 'closed'" class="state-banner error" role="alert">
      <span class="dot" aria-hidden="true"></span>
      连接已断开，请刷新页面
    </div>

    <!-- 消息列表 -->
    <ChatMessageBubble
      v-for="(msg, i) in messages"
      :key="msg.id || i"
      :msg="msg"
      @order="$emit('order', $event)"
      @chip="$emit('chip', $event)"
      @regenerate="$emit('regenerate', $event)"
      @feedback="(id, fb) => $emit('feedback', id, fb)"
      @command="(cmd, m) => $emit('command', cmd, m)"
    />

    <!-- 新消息提示 -->
    <button v-if="showNewMsgTip" class="new-msg-tip" @click="scrollToBottom" aria-label="跳转到最新消息">
      ↓ {{ newMsgCount }} 条新消息
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import ChatMessageBubble from './ChatMessageBubble.vue'
import { useSystemConfig } from '@/composables/useSystemConfig'

const props = defineProps({
  messages: { type: Array, required: true },
  connState: { type: String, default: 'idle' }
})

defineEmits(['order', 'chip', 'regenerate', 'feedback', 'command'])

const { getConfigValue } = useSystemConfig()

// 拆分 agent.welcome_text 为标题 + 副标题
// 约定：第一个换行或第一个标点（。！？）为分界
const welcomeRaw = computed(() => getConfigValue('agent.welcome_text') || '')
const welcomeTitle = computed(() => {
  const raw = welcomeRaw.value.trim()
  if (!raw) return ''
  // 按换行优先切
  const nlIdx = raw.indexOf('\n')
  if (nlIdx > 0) return raw.substring(0, nlIdx).trim()
  // 否则按中文/英文句号切
  const m = raw.match(/^(.+?[。！？!?])/)
  return m ? m[1].trim() : raw
})
const welcomeHint = computed(() => {
  const raw = welcomeRaw.value.trim()
  const title = welcomeTitle.value
  if (!raw || !title || raw === title) return ''
  return raw.substring(title.length).trim()
})

const listRef = ref(null)
const showNewMsgTip = ref(false)
const newMsgCount = ref(0)
let isNearBottom = true

function onScroll() {
  if (!listRef.value) return
  const { scrollTop, scrollHeight, clientHeight } = listRef.value
  isNearBottom = scrollHeight - scrollTop - clientHeight < 100
  if (isNearBottom) {
    showNewMsgTip.value = false
    newMsgCount.value = 0
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
      showNewMsgTip.value = false
      newMsgCount.value = 0
    }
  })
}

watch(() => props.messages.length, (newLen, oldLen) => {
  if (isNearBottom) {
    scrollToBottom()
  } else if (newLen > oldLen) {
    newMsgCount.value += (newLen - oldLen)
    showNewMsgTip.value = true
  }
})

watch(() => {
  const last = props.messages[props.messages.length - 1]
  return last?.content
}, () => {
  if (isNearBottom) scrollToBottom()
})

defineExpose({ scrollToBottom })
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  scroll-behavior: smooth;
  position: relative;
  container-type: inline-size;
}

@container (max-width: 360px) {
  .empty-icon { font-size: 36px; }
  .empty-text { font-size: 16px; }
  .empty-hint { font-size: 12px; }
}

.state-banner {
  position: sticky;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 12px;
  margin: -8px -8px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  z-index: 5;
}

.state-banner.warn {
  background: #fff3cd;
  color: #856404;
}

.state-banner.error {
  background: #f8d7da;
  color: #721c24;
}

.state-banner .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.state-banner .dot.pulse {
  animation: dot-pulse 1s ease-in-out infinite;
}

@keyframes dot-pulse {
  50% { opacity: 0.3; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60%;
  text-align: center;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 18px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 13px;
}

.new-msg-tip {
  position: sticky;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: fit-content;
  margin: 0 auto;
  padding: 6px 16px;
  background: #1a73e8;
  color: #fff;
  border: none;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  transition: all 0.15s;
}

.new-msg-tip:hover {
  background: #1557b0;
  transform: translateX(-50%) translateY(-2px);
}

@media (prefers-reduced-motion: reduce) {
  .message-list { scroll-behavior: auto; }
  .state-banner .dot.pulse { animation: none; }
}
</style>

