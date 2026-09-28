import { defineStore } from 'pinia'

const STORAGE_KEY = 'blog_theme'

function readStored() {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'dark' || v === 'light') return v
  } catch {
    /* ignore */
  }
  return null
}

function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    // 优先用户手动选择，其次跟随系统
    theme: readStored() || (systemPrefersDark() ? 'dark' : 'light'),
  }),

  getters: {
    isDark: (state) => state.theme === 'dark',
  },

  actions: {
    // 把主题同步到 <html>：切换 .dark class 与 data-scheme 属性
    apply() {
      const root = document.documentElement
      const dark = this.theme === 'dark'
      root.classList.toggle('dark', dark)
      root.setAttribute('data-scheme', dark ? 'dark' : 'light')
    },

    setTheme(theme) {
      this.theme = theme === 'dark' ? 'dark' : 'light'
      try {
        localStorage.setItem(STORAGE_KEY, this.theme)
      } catch {
        /* ignore */
      }
      this.apply()
    },

    toggle() {
      this.setTheme(this.theme === 'dark' ? 'light' : 'dark')
    },

    // 应用启动时调用一次，把持久化的主题挂到根节点
    init() {
      this.apply()
    },
  },
})
