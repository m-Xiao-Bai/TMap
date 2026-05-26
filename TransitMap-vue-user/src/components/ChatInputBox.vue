<template>
  <div class="chat-input-wrap" :class="{ focused: isFocused }">
    <ChatQuickChips
      v-if="showWelcomeChips"
      :items="welcomeChips"
      @pick="onPickChip"
    />

    <div class="input-row">
      <div class="textarea-wrap">
        <textarea
          ref="taRef"
          v-model="text"
          class="chat-textarea"
          :placeholder="placeholder"
          :maxlength="maxLength"
          :rows="autoRows"
          :aria-label="'消息输入框'"
          @input="onInput"
          @focus="isFocused = true"
          @blur="onBlur"
          @keydown="onKeydown"
          @compositionstart="composing = true"
          @compositionend="composing = false"
        />
        <SuggestPopover
          :visible="suggest.visible.value"
          :items="suggest.items.value"
          :highlight-index="suggest.highlightIndex.value"
          @select="suggest.confirm"
          @hover="(i) => (suggest.highlightIndex.value = i)"
        />
      </div>

      <ChatVoiceButton
        v-if="voiceEnabled"
        :mode="voiceMode"
        @transcript="onTranscript"
        @error="onVoiceError"
      />

      <button
        class="send-btn"
        :class="{ streaming: isStreaming }"
        :disabled="sendDisabled"
        :aria-label="isStreaming ? '停止生成' : '发送'"
        @click="onClickSend"
      >
        <svg v-if="!isStreaming" viewBox="0 0 24 24" width="20">
          <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" fill="currentColor"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" width="20">
          <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor"/>
        </svg>
      </button>
    </div>

    <div class="input-foot">
      <span class="hint">Enter 发送 · Shift+Enter 换行 · @ 选站点 · # 选城市</span>
      <span class="counter" :class="{ warn: text.length > maxLength * 0.95 }">
        {{ text.length }} / {{ maxLength }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import ChatQuickChips from './ChatQuickChips.vue'
import ChatVoiceButton from './ChatVoiceButton.vue'
import SuggestPopover from './SuggestPopover.vue'
import { useRotatingPlaceholder } from '@/composables/useRotatingPlaceholder'
import { useSystemConfig } from '@/composables/useSystemConfig'
import { useInputSuggest } from '@/composables/useInputSuggest'

const props = defineProps({
  isStreaming: Boolean,
  welcomeChips: { type: Array, default: () => [] },
  cityId: { type: [Number, String], default: null }
})

const emit = defineEmits(['send', 'stop'])

const { getConfigValue, getConfigJson } = useSystemConfig()

const text = ref('')
const composing = ref(false)
const isFocused = ref(false)
const taRef = ref(null)
const history = ref([])
const historyIdx = ref(-1)
let voiceSourceFlag = false

const maxLength = computed(() => {
  const v = getConfigValue('agent.input.max_length')
  return v ? parseInt(v) : 500
})

const voiceEnabled = computed(() => {
  const v = getConfigValue('agent.voice.enabled')
  return v == null ? true : parseInt(v) === 1
})
const voiceMode = computed(() => getConfigValue('agent.voice.mode') || 'push_to_talk')

const showWelcomeChips = computed(() => !text.value && !isFocused.value && history.value.length === 0)

const { placeholder } = useRotatingPlaceholder({
  source: () => getConfigJson('agent.input.placeholders') || ['告诉我你想去哪儿...'],
  intervalMs: () => {
    const v = getConfigValue('agent.input.rotate_interval_ms')
    return v ? parseInt(v) : 3500
  },
  pauseWhen: isFocused
})

const suggest = useInputSuggest({
  text,
  taRef: () => taRef.value,
  cityId: () => props.cityId
})

const autoRows = computed(() => Math.min(5, Math.max(1, text.value.split('\n').length)))
const sendDisabled = computed(() => !text.value.trim() && !props.isStreaming)

function onPickChip(s) {
  text.value = (text.value + s).slice(0, maxLength.value)
  taRef.value?.focus()
}

function onInput() {
  suggest.update()
  historyIdx.value = -1
}

function onBlur() {
  // 给点击 suggest 让出时间
  setTimeout(() => {
    isFocused.value = false
    suggest.close()
  }, 150)
}

function onKeydown(e) {
  // suggest 优先消费
  if (suggest.handleKey(e)) {
    e.preventDefault()
    return
  }

  if (e.key === 'Enter' && !e.shiftKey && !composing.value) {
    e.preventDefault()
    doSend()
    return
  }

  // ↑/↓ 历史回顾（仅输入为空时）
  if (e.key === 'ArrowUp' && !text.value && history.value.length) {
    e.preventDefault()
    historyIdx.value = Math.min(history.value.length - 1, historyIdx.value + 1)
    text.value = history.value[history.value.length - 1 - historyIdx.value] || ''
    return
  }
  if (e.key === 'ArrowDown' && historyIdx.value >= 0) {
    e.preventDefault()
    historyIdx.value -= 1
    text.value = historyIdx.value < 0 ? '' : history.value[history.value.length - 1 - historyIdx.value]
    return
  }
}

function onClickSend() {
  props.isStreaming ? emit('stop') : doSend()
}

function doSend() {
  const t = text.value.trim()
  if (!t) return
  emit('send', t, voiceSourceFlag ? 'voice' : 'text')
  history.value.push(t)
  text.value = ''
  historyIdx.value = -1
  voiceSourceFlag = false
  suggest.close()
}

function onTranscript(transcribed, isFinal) {
  const sendToInput = parseInt(getConfigValue('agent.voice.send_to_input') ?? '1') === 1
  if (sendToInput) {
    text.value = transcribed
    voiceSourceFlag = true
    taRef.value?.focus()
  } else if (isFinal) {
    emit('send', transcribed, 'voice')
  }
}

function onVoiceError(err) {
  console.warn('voice error', err)
}

defineExpose({
  focus() { taRef.value?.focus() }
})
</script>

<style scoped>
.chat-input-wrap {
  padding: 12px 16px;
  border-top: 1px solid #e8eaed;
  background: #fff;
  position: relative;
}

.chat-input-wrap.focused {
  border-top-color: #1a73e8;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.textarea-wrap {
  flex: 1;
  position: relative;
}

.chat-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1.5px solid #e0e0e0;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  font-family: inherit;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.chat-textarea:focus {
  border-color: #1a73e8;
}

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: #1a73e8;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #1557b0;
}

.send-btn:disabled {
  background: #c0c4cc;
  cursor: not-allowed;
}

.send-btn.streaming {
  background: #ea4335;
}

.send-btn.streaming:hover {
  background: #c5221f;
}

.input-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
}

.counter.warn {
  color: #ea4335;
}

@media (max-width: 600px) {
  .input-foot .hint {
    display: none;
  }
}
</style>
