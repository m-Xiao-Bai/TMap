import { ref, watch, onUnmounted } from 'vue'

export function useRotatingPlaceholder({ source, intervalMs, pauseWhen }) {
  const placeholder = ref('')
  let timer = null
  let currentIndex = 0

  function startRotation() {
    stopRotation()
    const items = source()
    if (!items || items.length === 0) return

    placeholder.value = items[currentIndex]
    timer = setInterval(() => {
      currentIndex = (currentIndex + 1) % items.length
      placeholder.value = items[currentIndex]
    }, intervalMs())
  }

  function stopRotation() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  // 当 pauseWhen 为 true 时暂停轮播
  if (pauseWhen) {
    watch(pauseWhen, (paused) => {
      if (paused) {
        stopRotation()
      } else {
        startRotation()
      }
    })
  }

  // 初始化
  startRotation()

  onUnmounted(stopRotation)

  return { placeholder }
}
