import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'tmap_theme'
const TRANS_CLASS = 'theme-transitioning'
const TRANS_DURATION = 350
const theme = ref('light') // light / dark
let initialized = false
let transTimer = null

function apply(t, animate = true) {
  const html = document.documentElement
  if (animate) {
    // 切换前打开过渡类，让所有元素的 color/bg/border 在 350ms 内平滑过渡
    html.classList.add(TRANS_CLASS)
    if (transTimer) clearTimeout(transTimer)
    transTimer = setTimeout(() => {
      html.classList.remove(TRANS_CLASS)
      transTimer = null
    }, TRANS_DURATION + 50)
  }
  if (t === 'dark') {
    html.classList.add('dark')
    html.setAttribute('data-theme', 'dark')
  } else {
    html.classList.remove('dark')
    html.setAttribute('data-theme', 'light')
  }
}

function init() {
  if (initialized) return
  initialized = true
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'dark' || saved === 'light') {
      theme.value = saved
    } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      theme.value = 'dark'
    }
  } catch {}
  // 首次应用不加过渡（避免初始闪烁）
  apply(theme.value, false)

  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
      if (!localStorage.getItem(STORAGE_KEY)) {
        theme.value = e.matches ? 'dark' : 'light'
      }
    })
  }
}

watch(theme, (t) => {
  apply(t, true)
  try { localStorage.setItem(STORAGE_KEY, t) } catch {}
})

export function useTheme() {
  onMounted(init)

  function toggle() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }
  function setTheme(t) {
    if (t === 'dark' || t === 'light') theme.value = t
  }
  function isDark() {
    return theme.value === 'dark'
  }
  return { theme, toggle, setTheme, isDark }
}
