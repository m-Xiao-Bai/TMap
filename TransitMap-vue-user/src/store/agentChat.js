import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

function loadPersisted(key, fallback) {
  try {
    const v = localStorage.getItem(`agent_${key}`)
    return v !== null ? JSON.parse(v) : fallback
  } catch {
    return fallback
  }
}

function persist(key, value) {
  try {
    localStorage.setItem(`agent_${key}`, JSON.stringify(value))
  } catch {}
}

export const useAgentChatStore = defineStore('agentChat', () => {
  // 面板状态：expanded / collapsed / fullscreen
  const panelState = ref(loadPersisted('panelState', 'expanded'))
  // 面板宽度（px）
  const panelWidth = ref(loadPersisted('panelWidth', null))
  // 当前会话 ID
  const currentSessionId = ref(null)
  // 待回插的订单卡片（由 Home.vue 在购票成功后写入；RouteAgentPanel 读取后追加到消息列表）
  const pendingOrderCard = ref(null)

  function setState(s) {
    panelState.value = s
    persist('panelState', s)
  }

  function setWidth(w) {
    panelWidth.value = w
    persist('panelWidth', w)
  }

  function togglePanel() {
    if (panelState.value === 'collapsed') {
      setState('expanded')
    } else {
      setState('collapsed')
    }
  }

  function appendOrderCard(order) {
    pendingOrderCard.value = { ...order, _ts: Date.now() }
  }

  function consumeOrderCard() {
    const o = pendingOrderCard.value
    pendingOrderCard.value = null
    return o
  }

  return {
    panelState,
    panelWidth,
    currentSessionId,
    pendingOrderCard,
    setState,
    setWidth,
    togglePanel,
    appendOrderCard,
    consumeOrderCard
  }
})

