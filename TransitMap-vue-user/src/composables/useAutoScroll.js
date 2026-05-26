import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'

/**
 * 智能自动滚动到底部 + 上滑停止跟底
 *
 * @param {() => HTMLElement} containerGetter - 滚动容器
 */
export function useAutoScroll(containerGetter) {
  const isAtBottom = ref(true)
  const newCount = ref(0)
  let observer = null
  let sentinel = null

  function ensureSentinel(container) {
    if (sentinel && container.contains(sentinel)) return sentinel
    sentinel = document.createElement('div')
    sentinel.className = 'autoscroll-sentinel'
    sentinel.style.cssText = 'height:1px;width:100%;'
    container.appendChild(sentinel)
    return sentinel
  }

  function setup() {
    const container = containerGetter()
    if (!container) return
    const sn = ensureSentinel(container)
    observer = new IntersectionObserver((entries) => {
      const e = entries[0]
      isAtBottom.value = e.isIntersecting
      if (e.isIntersecting) newCount.value = 0
    }, { root: container, threshold: 0.01 })
    observer.observe(sn)
  }

  function scrollToBottom() {
    nextTick(() => {
      const container = containerGetter()
      if (container) container.scrollTop = container.scrollHeight
    })
  }

  function onNewMessage() {
    if (isAtBottom.value) {
      scrollToBottom()
    } else {
      newCount.value++
    }
  }

  onMounted(() => setup())

  onBeforeUnmount(() => {
    if (observer) observer.disconnect()
    if (sentinel?.parentNode) sentinel.parentNode.removeChild(sentinel)
  })

  return { isAtBottom, newCount, scrollToBottom, onNewMessage }
}
