import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

export interface ThemePreset {
  label: string
  key: string           // 唯一标识，用于 class
  primary: string       // 主题色
  bgPage: string
  bgCard: string
  bgHeader: string
  textColor: string
  textSecondary: string
  borderColor: string
}

export const themePresets: ThemePreset[] = [
  {
    label: '极简白',
    key: 'light',
    primary: '#1890ff',
    bgPage: '#f0f2f5',
    bgCard: '#ffffff',
    bgHeader: '#ffffff',
    textColor: '#1a1a1a',
    textSecondary: '#8c8c8c',
    borderColor: '#f0f0f0',
  },
  {
    label: '暗夜黑',
    key: 'dark',
    primary: '#1677ff',
    bgPage: '#0d1117',
    bgCard: '#161b22',
    bgHeader: '#161b22',
    textColor: '#e6edf3',
    textSecondary: '#8b949e',
    borderColor: '#30363d',
  },
  {
    label: '暖阳',
    key: 'warm',
    primary: '#d46b08',
    bgPage: '#faf6f0',
    bgCard: '#fffef9',
    bgHeader: '#fffef9',
    textColor: '#2c2c2c',
    textSecondary: '#8c8c8c',
    borderColor: '#f0e8e0',
  },
]

export const useThemeStore = defineStore('theme', () => {
  const savedIndex = Number(localStorage.getItem('themeIndex') || '0')
  const currentIndex = ref(savedIndex)
  const currentTheme = ref<ThemePreset>(themePresets[savedIndex] || themePresets[0])

  function setTheme(index: number) {
    currentIndex.value = index
    currentTheme.value = themePresets[index] || themePresets[0]
    localStorage.setItem('themeIndex', String(index))
    applyTheme(currentTheme.value)
  }

  function applyTheme(theme: ThemePreset) {
    const root = document.documentElement
    root.style.setProperty('--bg-page', theme.bgPage)
    root.style.setProperty('--bg-card', theme.bgCard)
    root.style.setProperty('--bg-header', theme.bgHeader)
    root.style.setProperty('--text-color', theme.textColor)
    root.style.setProperty('--text-secondary', theme.textSecondary)
    root.style.setProperty('--border-color', theme.borderColor)
    root.style.setProperty('--primary-color', theme.primary)

    // 给 html 加 class，方便覆盖 antd 组件样式
    document.documentElement.className = 'theme-' + theme.key
  }

  applyTheme(currentTheme.value)

  const primaryColor = ref(currentTheme.value.primary)
  watch(currentTheme, (t) => {
    primaryColor.value = t.primary
  })

  return { currentIndex, currentTheme, primaryColor, setTheme }
})
