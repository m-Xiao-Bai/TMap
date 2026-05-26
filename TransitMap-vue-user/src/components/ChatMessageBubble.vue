<template>
  <div class="message-bubble" :class="[msg.role, { streaming: msg.streaming, error: msg.error }]">
    <div class="bubble-content">
      <div v-if="msg.role === 'assistant'" class="avatar">🚇</div>
      <div class="text-wrap">
        <div class="text" v-if="msg.role === 'user'">{{ msg.content }}</div>
        <div v-else-if="msg.streaming && !msg.content" class="text thinking">
          <span class="thinking-dots"><i></i><i></i><i></i></span>
          <span class="thinking-text">{{ msg.status || '正在思考' }}</span>
        </div>
        <div v-else class="text" v-html="renderAssistant(msg.content)"></div>
        <span v-if="msg.streaming && msg.content" class="cursor">|</span>
        <div v-if="msg.error" class="error-tip">{{ msg.error.message || '出错了' }}</div>
      </div>
    </div>

    <!-- 路线卡片 -->
    <ChatRouteCard
      v-if="msg.extras?.kind === 'ROUTE_CARD'"
      :route="msg.extras.payload"
      @order="$emit('order', msg.extras.payload)"
    />

    <!-- 订单卡片 -->
    <ChatOrderCard
      v-if="msg.extras?.kind === 'ORDER_CARD'"
      :order="msg.extras.payload"
    />

    <!-- 快捷词 -->
    <div v-if="msg.chips?.length" class="chips-row">
      <button
        v-for="chip in msg.chips"
        :key="chip"
        class="chip-btn"
        :class="{ 'chip-cmd': isCommand(chip), 'chip-end': isEndChip(chip) }"
        @click="onChipClick(chip)"
      >{{ chipLabel(chip) }}</button>
    </div>

    <!-- 操作栏（hover 显示） -->
    <div v-if="msg.role === 'assistant' && !msg.streaming" class="actions">
      <button class="action-btn" title="复制" @click="copyText">
        <svg viewBox="0 0 24 24" width="14"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="currentColor"/></svg>
      </button>
      <button v-if="msg.id" class="action-btn" title="重新生成" @click="$emit('regenerate', msg.id)">
        <svg viewBox="0 0 24 24" width="14"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z" fill="currentColor"/></svg>
      </button>
      <button v-if="msg.id" class="action-btn" title="赞" :class="{ active: msg.feedback === 1 }" @click="$emit('feedback', msg.id, 1)">
        <svg viewBox="0 0 24 24" width="14"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z" fill="currentColor"/></svg>
      </button>
      <button v-if="msg.id" class="action-btn" title="踩" :class="{ active: msg.feedback === -1 }" @click="$emit('feedback', msg.id, -1)">
        <svg viewBox="0 0 24 24" width="14"><path d="M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2zm4 0v12h4V3h-4z" fill="currentColor"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import ChatRouteCard from './ChatRouteCard.vue'
import ChatOrderCard from './ChatOrderCard.vue'

const props = defineProps({
  msg: { type: Object, required: true }
})

const emit = defineEmits(['order', 'chip', 'regenerate', 'feedback', 'command'])

function renderAssistant(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

function copyText() {
  if (props.msg.content) {
    navigator.clipboard.writeText(props.msg.content)
  }
}

/**
 * chip 格式约定：
 *   普通 chip：原样发送给 Agent
 *   ::cmd:NAME:PAYLOAD|LABEL  → 触发命令，不发送
 *   "算了，结束对话"           → 结束对话（关闭面板/清空 chips）
 */
function isCommand(chip) {
  return typeof chip === 'string' && chip.startsWith('::cmd:')
}
function isEndChip(chip) {
  return chip === '算了，结束对话' || chip === '结束对话'
}
function chipLabel(chip) {
  if (!isCommand(chip)) return chip
  const idx = chip.indexOf('|')
  return idx > 0 ? chip.substring(idx + 1) : chip.substring(6)
}
function parseCommand(chip) {
  if (!isCommand(chip)) return null
  const body = chip.substring(6) // 去掉 "::cmd:"
  const pipeIdx = body.indexOf('|')
  const head = pipeIdx > 0 ? body.substring(0, pipeIdx) : body
  const colonIdx = head.indexOf(':')
  return {
    name: colonIdx > 0 ? head.substring(0, colonIdx) : head,
    payload: colonIdx > 0 ? head.substring(colonIdx + 1) : ''
  }
}
function onChipClick(chip) {
  if (isCommand(chip)) {
    const cmd = parseCommand(chip)
    if (cmd) emit('command', cmd, props.msg)
    return
  }
  if (isEndChip(chip)) {
    emit('command', { name: 'end_session', payload: '' }, props.msg)
    return
  }
  emit('chip', chip)
}
</script>

<style scoped>
.message-bubble {
  margin-bottom: 12px;
  position: relative;
}

.bubble-content {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.message-bubble.user .bubble-content {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.text-wrap {
  max-width: 85%;
  position: relative;
}

.text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-bubble.user .text {
  background: #1a73e8;
  color: #fff;
  border-top-right-radius: 4px;
}

.message-bubble.assistant .text {
  background: #f5f6f8;
  color: #1d1d1f;
  border-top-left-radius: 4px;
}

.message-bubble.error .text {
  background: #fef2f2;
  color: #dc2626;
}

.cursor {
  display: inline-block;
  animation: blink 0.8s step-end infinite;
  color: #1a73e8;
  font-weight: 600;
}

.thinking {
  display: flex !important;
  align-items: center;
  gap: 8px;
  padding: 10px 14px !important;
}

.thinking-dots {
  display: flex;
  gap: 4px;
}

.thinking-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1a73e8;
  display: block;
  animation: dotPulse 1.4s ease-in-out infinite;
}

.thinking-dots i:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dots i:nth-child(3) {
  animation-delay: 0.4s;
}

.thinking-text {
  font-size: 13px;
  color: #909399;
}

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

@keyframes blink {
  50% { opacity: 0; }
}

.error-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #dc2626;
}

.chips-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding-left: 40px;
}

.chip-btn {
  padding: 6px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 16px;
  background: #fff;
  font-size: 12px;
  color: #1a73e8;
  cursor: pointer;
  transition: all 0.15s;
}

.chip-btn:hover {
  background: #f0f7ff;
  border-color: #1a73e8;
}

.chip-btn.chip-cmd {
  background: #fff7e6;
  border-color: #ffd28a;
  color: #d97706;
}
.chip-btn.chip-cmd:hover {
  background: #fff1d5;
  border-color: #ea580c;
}

.chip-btn.chip-end {
  background: transparent;
  border-color: #e0e0e0;
  color: #909399;
}
.chip-btn.chip-end:hover {
  background: #f5f7fa;
}

.actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  padding-left: 40px;
  opacity: 0;
  transition: opacity 0.15s;
}

.message-bubble:hover .actions {
  opacity: 1;
}

.action-btn {
  width: 28px;
  height: 28px;
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

.action-btn:hover {
  background: #f0f0f0;
  color: #1a73e8;
}

.action-btn.active {
  color: #1a73e8;
  background: #f0f7ff;
}
</style>
