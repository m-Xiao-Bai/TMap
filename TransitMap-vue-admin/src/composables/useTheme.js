import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'tmap_admin_theme'
const TRANS_CLASS = 'theme-transitioning'
const TRANS_DURATION = 350
const theme = ref('light')
let initialized = false
let transTimer = null

function apply(t, animate = true) {
  const html = document.documentElement
  if (animate) {
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
  apply(theme.value, false)
}

watch(theme, (t) => {
  apply(t, true)
  try { localStorage.setItem(STORAGE_KEY, t) } catch {}
})

export function useTheme() {
  onMounted(init)
  return {
    theme,
    toggle: () => (theme.value = theme.value === 'dark' ? 'light' : 'dark'),
    setTheme: (t) => { if (t === 'dark' || t === 'light') theme.value = t },
    isDark: () => theme.value === 'dark'
  }
}
