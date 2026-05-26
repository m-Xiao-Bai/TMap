import { ref, computed, onMounted, onUnmounted } from 'vue'

export function usePanelLayout(store) {
  const windowWidth = ref(window.innerWidth)
  let resizeObserver = null
  let resizeTimer = null

  // 响应式断点
  const breakpoint = computed(() => {
    const w = windowWidth.value
    if (w <= 600) return 'xs'
    if (w <= 900) return 'sm'
    if (w <= 1366) return 'md'
    return 'lg'
  })

  // 是否移动端
  const isMobile = computed(() => breakpoint.value === 'xs')
  const isTablet = computed(() => breakpoint.value === 'sm')
  const isDesktop = computed(() => breakpoint.value === 'md' || breakpoint.value === 'lg')

  // 面板默认宽度
  const defaultWidth = computed(() => {
    switch (store.panelState) {
      case 'expanded':
        if (isMobile.value) return '100%'
        if (isTablet.value) return 'clamp(300px, 32vw, 420px)'
        if (breakpoint.value === 'md') return 'clamp(320px, 26vw, 440px)'
        return 'clamp(360px, 24vw, 520px)'
      case 'fullscreen':
        return '100%'
      default:
        return '0'
    }
  })

  // 实际面板宽度
  const panelWidth = computed(() => {
    if (store.panelWidth && isDesktop.value) {
      return store.panelWidth + 'px'
    }
    return defaultWidth.value
  })

  // 拖拽控制
  const isDragging = ref(false)
  let dragStartX = 0
  let dragStartWidth = 0

  function onDragStart(e) {
    if (!isDesktop.value) return
    isDragging.value = true
    dragStartX = e.clientX || e.touches?.[0]?.clientX || 0
    const panel = document.querySelector('.agent-panel-wrap')
    dragStartWidth = panel?.offsetWidth || 400
    document.addEventListener('mousemove', onDragMove)
    document.addEventListener('mouseup', onDragEnd)
    document.addEventListener('touchmove', onDragMove)
    document.addEventListener('touchend', onDragEnd)
    e.preventDefault()
  }

  function onDragMove(e) {
    if (!isDragging.value) return
    const clientX = e.clientX || e.touches?.[0]?.clientX || 0
    const diff = dragStartX - clientX
    const newWidth = Math.max(320, Math.min(600, dragStartWidth + diff))
    store.setWidth(newWidth)
  }

  function onDragEnd() {
    isDragging.value = false
    document.removeEventListener('mousemove', onDragMove)
    document.removeEventListener('mouseup', onDragEnd)
    document.removeEventListener('touchmove', onDragMove)
    document.removeEventListener('touchend', onDragEnd)
  }

  // 窗口大小监听
  function onResize() {
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      windowWidth.value = window.innerWidth
    }, 100)
  }

  onMounted(() => {
    window.addEventListener('resize', onResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', onResize)
    if (resizeTimer) clearTimeout(resizeTimer)
  })

  return {
    breakpoint,
    isMobile,
    isTablet,
    isDesktop,
    panelWidth,
    isDragging,
    onDragStart
  }
}
