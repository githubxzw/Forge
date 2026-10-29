import { ref } from 'vue'

/** 日间/夜间主题：localStorage 记忆，未设置时跟随系统偏好 */
export type ThemeMode = 'light' | 'dark'
const KEY = 'forge_theme'

const mode = ref<ThemeMode>('dark')

function systemTheme(): ThemeMode {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: light)').matches
    ? 'light' : 'dark'
}

function apply(m: ThemeMode) {
  const el = document.documentElement
  el.classList.toggle('dark', m === 'dark')
  // 原生滚动条/表单控件跟随主题
  el.style.colorScheme = m
}

/** 应用启动时调用（在 mount 前，避免主题闪烁） */
export function initTheme() {
  const saved = localStorage.getItem(KEY)
  mode.value = saved === 'light' || saved === 'dark' ? saved : systemTheme()
  apply(mode.value)
}

export function setTheme(m: ThemeMode) {
  mode.value = m
  localStorage.setItem(KEY, m)
  apply(m)
}

export function useTheme() {
  return {
    mode,
    toggle: () => setTheme(mode.value === 'dark' ? 'light' : 'dark'),
  }
}
