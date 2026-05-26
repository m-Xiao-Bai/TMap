<template>
  <button
    class="voice-btn"
    :class="{ recording: isRecording }"
    :aria-label="isRecording ? '松开停止录音' : '按住说话'"
    @pointerdown="onPointerDown"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @pointerleave="onPointerLeaveSafe"
    @contextmenu.prevent
  >
    <svg v-if="!isRecording" viewBox="0 0 24 24" width="20">
      <path d="M12 14a3 3 0 003-3V5a3 3 0 00-6 0v6a3 3 0 003 3z M19 11a7 7 0 01-14 0 M12 18v3"
        fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
    </svg>
    <span v-else class="rec-anim">
      <span class="bar" v-for="i in 4" :key="i" :style="{ animationDelay: i * 0.1 + 's' }"></span>
    </span>
    <span v-if="isRecording" class="rec-timer">{{ duration }}s</span>
  </button>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useVoiceInput } from '@/composables/useVoiceInput'

const props = defineProps({
  mode: { type: String, default: 'push_to_talk' }
})

const emit = defineEmits(['transcript', 'error'])

const voice = useVoiceInput()
const isRecording = ref(false)
const duration = ref(0)
let timer = null
let starting = false

function onPointerDown(e) {
  if (props.mode === 'toggle') {
    isRecording.value ? stop() : start()
    return
  }
  e.preventDefault()
  start()
}

function onPointerUp() {
  if (props.mode === 'push_to_talk') stop()
}

function onPointerLeaveSafe() {
  if (props.mode === 'push_to_talk' && isRecording.value) stop()
}

async function start() {
  if (isRecording.value || starting) return
  starting = true
  try {
    if (!voice.isSupported()) {
      emit('error', { code: 'NOT_SUPPORTED', message: '当前浏览器不支持语音，请用 Chrome / Edge' })
      starting = false
      return
    }
    await voice.start({
      onPartial: (t) => emit('transcript', t, false),
      onFinal: (t) => emit('transcript', t, true),
      onSilence: () => stop()
    })
    isRecording.value = true
    duration.value = 0
    timer = setInterval(() => { duration.value++ }, 1000)
  } catch (err) {
    emit('error', { code: err.message || 'UNKNOWN', message: '麦克风启动失败' })
  } finally {
    starting = false
  }
}

function stop() {
  voice.stop()
  if (timer) { clearInterval(timer); timer = null }
  isRecording.value = false
}

onUnmounted(stop)
</script>

<style scoped>
.voice-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1.5px solid #e8eaed;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
  color: #606266;
  position: relative;
  user-select: none;
}

.voice-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

.voice-btn.recording {
  background: linear-gradient(135deg, #ea4335, #c5221f);
  border-color: transparent;
  color: #fff;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(234, 67, 53, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(234, 67, 53, 0); }
}

.rec-anim {
  display: flex;
  gap: 2px;
  align-items: center;
  height: 16px;
}

.rec-anim .bar {
  width: 2px;
  height: 100%;
  background: #fff;
  border-radius: 1px;
  animation: barWave 0.8s ease-in-out infinite;
  transform-origin: center;
}

@keyframes barWave {
  0%, 100% { transform: scaleY(0.3); }
  50% { transform: scaleY(1); }
}

.rec-timer {
  position: absolute;
  top: -22px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #ea4335;
  font-weight: 600;
  background: #fff;
  padding: 1px 6px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
</style>
