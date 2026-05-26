import { ref, reactive } from 'vue'
import { suggest as apiSuggest } from '@/api/agent'

/**
 * 输入框 @/# 智能联想
 *
 * @param {Object} opts
 *   - text:    ref<string> 当前输入文本
 *   - taRef:   () => HTMLTextAreaElement 获取 textarea 实例
 *   - cityId:  () => number|null 当前城市 id
 */
export function useInputSuggest({ text, taRef, cityId }) {
  const visible = ref(false)
  const items = ref([])
  const highlightIndex = ref(0)
  const trigger = ref(null)     // '@' / '#'
  const queryStart = ref(-1)    // 触发字符所在位置（不含触发字符）
  let debounceTimer = null
  let lastQuery = ''

  function close() {
    visible.value = false
    items.value = []
    highlightIndex.value = 0
    trigger.value = null
    queryStart.value = -1
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  /**
   * 在 input/keyup 时调用，检测光标前是否有未结束的 @ 或 # 触发段
   */
  function update() {
    const ta = taRef()
    if (!ta) return
    const caret = ta.selectionStart || 0
    const before = text.value.slice(0, caret)

    // 找最近的 @ 或 #（且其后不含空格/换行/换段）
    let triggerIdx = -1
    let triggerChar = null
    for (let i = before.length - 1; i >= 0; i--) {
      const c = before[i]
      if (c === '\n' || c === ' ') break
      if (c === '@' || c === '#') {
        triggerIdx = i
        triggerChar = c
        break
      }
    }

    if (triggerIdx === -1) {
      close()
      return
    }

    const query = before.slice(triggerIdx + 1)
    if (query === lastQuery && visible.value) return
    lastQuery = query
    trigger.value = triggerChar
    queryStart.value = triggerIdx

    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => doSearch(query), 200)
  }

  async function doSearch(query) {
    if (query.length === 0) {
      // 显示热门：不查也展示
      visible.value = true
      items.value = []
      return
    }
    try {
      const type = trigger.value === '#' ? 'city' : 'station'
      const res = await apiSuggest(query, cityId?.(), type)
      if (res.code === 200) {
        items.value = res.data || []
        highlightIndex.value = 0
        visible.value = items.value.length > 0
      }
    } catch (e) {
      console.warn('suggest failed', e)
    }
  }

  /**
   * 处理键盘事件。返回 true 表示已消费。
   */
  function handleKey(e) {
    if (!visible.value || items.value.length === 0) return false
    if (e.key === 'ArrowDown') {
      highlightIndex.value = (highlightIndex.value + 1) % items.value.length
      return true
    }
    if (e.key === 'ArrowUp') {
      highlightIndex.value =
        (highlightIndex.value - 1 + items.value.length) % items.value.length
      return true
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      confirm(items.value[highlightIndex.value])
      return true
    }
    if (e.key === 'Escape') {
      close()
      return true
    }
    return false
  }

  /**
   * 选中一项 → 替换输入框中 @xxx / #xxx 段为名称
   */
  function confirm(item) {
    if (!item) {
      close()
      return
    }
    const ta = taRef()
    if (!ta) {
      close()
      return
    }
    const caret = ta.selectionStart || 0
    const before = text.value.slice(0, queryStart.value)
    const after = text.value.slice(caret)
    const replacement = item.name + ' '
    text.value = before + replacement + after

    // 光标移到替换后位置
    const newCaret = before.length + replacement.length
    requestAnimationFrame(() => {
      ta.focus()
      ta.setSelectionRange(newCaret, newCaret)
    })
    close()
  }

  return {
    visible,
    items,
    highlightIndex,
    update,
    handleKey,
    confirm,
    close
  }
}
