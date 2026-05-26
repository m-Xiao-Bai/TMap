<template>
  <div v-if="visible" class="onboarding-overlay" @click="next">
    <div
      class="tooltip"
      :class="step.position"
      :style="tooltipStyle"
      @click.stop
    >
      <div class="step-num">{{ currentStep + 1 }} / {{ steps.length }}</div>
      <div class="step-title">{{ step.title }}</div>
      <div class="step-desc">{{ step.desc }}</div>
      <div class="step-actions">
        <button class="btn-skip" @click="finish">跳过</button>
        <button class="btn-next" @click="next">
          {{ currentStep === steps.length - 1 ? '完成' : '下一步' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'

const FLAG_KEY = 'agent_onboarded_v1'

const steps = [
  {
    selector: '.agent-panel-wrap .panel-header .title',
    title: '欢迎使用路线助手 🚇',
    desc: '我可以帮你规划地铁路线、推荐换乘方案、引导你下单。',
    position: 'bottom'
  },
  {
    selector: '.agent-panel-wrap .session-dropdown',
    title: '多会话切换',
    desc: '点这里可以切换历史会话或新开一段对话。',
    position: 'bottom'
  },
  {
    selector: '.agent-panel-wrap .chat-textarea',
    title: '快捷输入',
    desc: '试试输入 @ 选站点、# 选城市，或者按住右侧麦克风说话。',
    position: 'top'
  },
  {
    selector: '.agent-panel-wrap .header-actions .header-btn',
    title: '面板布局',
    desc: '右上角可以切换全屏 / 收起，桌面拖拽左边缘还能改宽度。',
    position: 'bottom'
  }
]

const visible = ref(false)
const currentStep = ref(0)
const targetRect = ref(null)

const step = computed(() => steps[currentStep.value] || {})

const tooltipStyle = computed(() => {
  const r = targetRect.value
  if (!r) return {}
  const TT_W = 280
  const TT_H = 140
  const margin = 12
  let left, top
  if (step.value.position === 'top') {
    top = Math.max(margin, r.top - TT_H - margin)
    left = Math.min(window.innerWidth - TT_W - margin, Math.max(margin, r.left + r.width / 2 - TT_W / 2))
  } else {
    top = Math.min(window.innerHeight - TT_H - margin, r.bottom + margin)
    left = Math.min(window.innerWidth - TT_W - margin, Math.max(margin, r.left + r.width / 2 - TT_W / 2))
  }
  return {
    top: top + 'px',
    left: left + 'px',
    width: TT_W + 'px'
  }
})

async function locateTarget() {
  await nextTick()
  const sel = step.value.selector
  if (!sel) return
  const el = document.querySelector(sel)
  if (!el) {
    // 找不到就跳过
    next()
    return
  }
  targetRect.value = el.getBoundingClientRect()
  el.classList.add('onboard-target')
}

function clearHighlight() {
  document.querySelectorAll('.onboard-target').forEach(el => {
    el.classList.remove('onboard-target')
  })
}

function next() {
  clearHighlight()
  if (currentStep.value === steps.length - 1) {
    finish()
    return
  }
  currentStep.value++
  locateTarget()
}

function finish() {
  clearHighlight()
  visible.value = false
  try { localStorage.setItem(FLAG_KEY, '1') } catch {}
}

function start() {
  try {
    if (localStorage.getItem(FLAG_KEY) === '1') return
  } catch {}
  visible.value = true
  currentStep.value = 0
  setTimeout(() => locateTarget(), 800) // 等面板渲染稳定
}

function onResize() {
  if (visible.value) locateTarget()
}

onMounted(() => {
  start()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  clearHighlight()
})

defineExpose({ start, finish })
</script>

<style>
/* 全局：高亮目标 */
.onboard-target {
  position: relative;
  z-index: 10001 !important;
  box-shadow: 0 0 0 4px rgba(26, 115, 232, 0.6), 0 0 0 9999px rgba(0, 0, 0, 0.5);
  border-radius: 8px;
}
</style>

<style scoped>
.onboarding-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  pointer-events: auto;
}

.tooltip {
  position: fixed;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0,0,0,0.2);
  padding: 16px;
  z-index: 10002;
  border: 1px solid rgba(26, 115, 232, 0.2);
  pointer-events: auto;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
}

.step-num {
  font-size: 11px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 600;
}

.step-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 6px;
}

.step-desc {
  font-size: 13px;
  color: #5f6368;
  line-height: 1.5;
  margin-bottom: 14px;
}

.step-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn-skip,
.btn-next {
  border: none;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 14px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-skip {
  background: transparent;
  color: #909399;
}
.btn-skip:hover {
  background: #f5f7fa;
}

.btn-next {
  background: #1a73e8;
  color: #fff;
}
.btn-next:hover {
  background: #1557b0;
}
</style>
