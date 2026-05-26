import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 全局快捷键
 *
 * @param {Object} handlers
 *   - togglePanel: Ctrl/⌘+K        开关面板
 *   - collapsePanel: Esc           收起
 *   - focusInput: Ctrl+/          聚焦输入框
 *   - newSession: Ctrl+N          新会话
 *   - toggleFullscreen: Ctrl+B    全屏切换
 */
export function useChatHotkeys(handlers = {}) {
  function isEditable(el) {
    if (!el) return false
    const tag = el.tagName
    return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable
  }

  function onKey(e) {
    const ctrl = e.ctrlKey || e.metaKey
    const key = e.key.toLowerCase()

    // Ctrl/⌘+K 开关面板
    if (ctrl && key === 'k') {
      e.preventDefault()
      handlers.togglePanel?.()
      return
    }

    // Ctrl+N 新会话（避免和浏览器 Ctrl+N=新窗口冲突，只在面板内 active 时拦截）
    if (ctrl && key === 'n' && handlers.newSession) {
      // 浏览器原生 Ctrl+N 大多无法被 preventDefault 拦截，留作可选触发
      // 这里只在 alt 一起按下时启用：Ctrl+Alt+N
      if (e.altKey) {
        e.preventDefault()
        handlers.newSession?.()
        return
      }
    }

    // Ctrl+B 全屏切换
    if (ctrl && key === 'b') {
      e.preventDefault()
      handlers.toggleFullscreen?.()
      return
    }

    // Ctrl+/ 聚焦输入框
    if (ctrl && key === '/') {
      e.preventDefault()
      handlers.focusInput?.()
      return
    }

    // Esc 收起（仅当面板可见 & 用户没在编辑输入框）
    if (key === 'escape') {
      if (!isEditable(e.target)) {
        handlers.collapsePanel?.()
      }
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKey)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKey)
  })
}
