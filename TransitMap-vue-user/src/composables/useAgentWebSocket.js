import { ref, reactive, watch, onUnmounted, isRef } from 'vue'
import { useUserStore } from '@/store/user'

/**
 * sessionIdSource: ref<Number|String> 或 () => Number|String
 */
export function useAgentWebSocket(sessionIdSource) {
  const messages = ref([])
  const currentAssistant = ref(null)
  const isStreaming = ref(false)
  const connState = ref('idle') // idle / connecting / open / reconnecting / closed
  const userStore = useUserStore()

  let ws = null
  let heartbeatTimer = null
  let reconnectAttempt = 0
  let manualClose = false
  const RECONNECT_DELAYS = [1000, 2000, 5000, 10000, 20000]

  function getSessionId() {
    if (typeof sessionIdSource === 'function') return sessionIdSource()
    if (isRef(sessionIdSource)) return sessionIdSource.value
    return sessionIdSource
  }

  function connect() {
    const sid = getSessionId()
    if (!sid) return
    if (ws && ws.readyState !== WebSocket.CLOSED && ws.readyState !== WebSocket.CLOSING) return

    manualClose = false
    connState.value = reconnectAttempt > 0 ? 'reconnecting' : 'connecting'

    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${location.host}/transitMap/ws/agent` +
      `?sessionId=${sid}` +
      `&token=${encodeURIComponent(userStore.token || '')}` +
      `&anon=${encodeURIComponent(userStore.anonToken || '')}`

    ws = new WebSocket(url)

    ws.onopen = () => {
      connState.value = 'open'
      reconnectAttempt = 0
      startHeartbeat()
    }

    ws.onmessage = (e) => {
      let msg
      try { msg = JSON.parse(e.data) } catch { return }
      switch (msg.type) {
        case 'ready':
          break
        case 'delta':
          appendDelta(msg.text)
          break
        case 'status':
          if (currentAssistant.value) {
            currentAssistant.value.status = msg.text || ''
          }
          break
        case 'card':
          if (currentAssistant.value) {
            currentAssistant.value.extras = msg.data
          }
          break
        case 'chips':
          if (currentAssistant.value) {
            currentAssistant.value.chips = msg.items
          }
          break
        case 'done':
          onDone(msg)
          break
        case 'error':
          onError(msg)
          break
        case 'pong':
          break
      }
    }

    ws.onclose = (e) => {
      stopHeartbeat()
      connState.value = 'closed'
      if (!manualClose && !e.wasClean && reconnectAttempt < RECONNECT_DELAYS.length) {
        const delay = RECONNECT_DELAYS[reconnectAttempt++]
        setTimeout(connect, delay)
      }
    }

    ws.onerror = () => {
      // 触发 onclose
    }
  }

  function send(content, location, inputMethod = 'text') {
    // 不管 WS 状态如何，先把用户消息显示出来（避免"点了发送毫无反应"的体验）
    messages.value.push({
      role: 'user',
      content,
      inputMethod
    })

    currentAssistant.value = reactive({
      role: 'assistant',
      content: '',
      extras: null,
      chips: [],
      streaming: true,
      status: ''
    })
    messages.value.push(currentAssistant.value)
    isStreaming.value = true

    if (!ws || ws.readyState !== WebSocket.OPEN) {
      // 标记 assistant 气泡为错误，给用户明确反馈
      currentAssistant.value.streaming = false
      currentAssistant.value.error = {
        code: 'NO_CONNECTION',
        message: '连接尚未建立，请稍后重试或刷新页面'
      }
      isStreaming.value = false
      return Promise.reject(new Error('WS not connected'))
    }

    ws.send(JSON.stringify({
      type: 'chat',
      content,
      inputMethod,
      ...(location ? { lat: location.lat, lng: location.lng } : {})
    }))
    return Promise.resolve()
  }

  function stop() {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'stop' }))
    }
    if (currentAssistant.value) {
      currentAssistant.value.streaming = false
    }
    isStreaming.value = false
  }

  function regenerate(messageId) {
    if (ws?.readyState === WebSocket.OPEN) {
      currentAssistant.value = reactive({
        role: 'assistant',
        content: '',
        extras: null,
        chips: [],
        streaming: true,
        status: ''
      })
      messages.value.push(currentAssistant.value)
      isStreaming.value = true
      ws.send(JSON.stringify({ type: 'regenerate', messageId }))
    }
  }

  function appendDelta(text) {
    if (!currentAssistant.value) return
    currentAssistant.value.content += text
    if (currentAssistant.value.status) currentAssistant.value.status = ''
  }

  function onDone(msg) {
    if (currentAssistant.value) {
      currentAssistant.value.streaming = false
      currentAssistant.value.id = msg.messageId
    }
    isStreaming.value = false
  }

  function onError(msg) {
    if (currentAssistant.value) {
      currentAssistant.value.streaming = false
      currentAssistant.value.error = msg
    }
    isStreaming.value = false
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, 25000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function close() {
    manualClose = true
    stopHeartbeat()
    if (ws) {
      try {
        // 移除回调，避免旧实例触发副作用
        ws.onopen = null
        ws.onmessage = null
        ws.onerror = null
        ws.onclose = null
        if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
          ws.close(1000)
        }
      } catch {}
    }
    ws = null
    connState.value = 'closed'
  }

  /**
   * 主动推入一条消息（外部代码用，例如订单卡片回插）
   */
  function pushMessage(msg) {
    messages.value.push(msg)
  }

  /**
   * 清空消息列表（切换会话时调用）
   */
  function clearMessages() {
    messages.value = []
    currentAssistant.value = null
    isStreaming.value = false
  }

  // sessionId 响应式：变化时重置连接（消息列表由调用方负责清理/重载历史）
  if (isRef(sessionIdSource)) {
    watch(sessionIdSource, (sid, oldSid) => {
      if (sid !== oldSid) {
        close()
        reconnectAttempt = 0
        if (sid) connect()
      }
    })
  }

  onUnmounted(close)

  return {
    messages,
    send,
    stop,
    regenerate,
    isStreaming,
    connState,
    connect,
    close,
    pushMessage,
    clearMessages
  }
}
