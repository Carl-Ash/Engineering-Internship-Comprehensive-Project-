import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface ThemePreset {
  label: string
  key: string
  primary: string
  primaryRgb: string
  bgPage: string
  bgCard: string
  bgHeader: string
  textColor: string
  textSecondary: string
  borderColor: string
  heroGradientStart: string
  heroGradientEnd: string
  heroGradientMid: string
}

export const themePresets: ThemePreset[] = [
  {
    label: '极简白',
    key: 'light',
    primary: '#1890ff',
    primaryRgb: '24, 144, 255',
    bgPage: '#f0f2f5',
    bgCard: '#ffffff',
    bgHeader: 'rgba(255,255,255,0.8)',
    textColor: '#1a1a1a',
    textSecondary: '#8c8c8c',
    borderColor: '#e8e8ec',
    heroGradientStart: '#667eea',
    heroGradientEnd: '#764ba2',
    heroGradientMid: '#f093fb',
  },
]

export const useThemeStore = defineStore('theme', () => {
  const currentIndex = ref(0)
  const currentTheme = ref<ThemePreset>(themePresets[0]!)

  function setTheme(index: number) {
    currentIndex.value = index
    const newIndex = Math.min(Math.max(index, 0), themePresets.length - 1)
    currentTheme.value = themePresets[newIndex]!
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
    root.style.setProperty('--primary-color-rgb', theme.primaryRgb)
    root.style.setProperty('--hero-gradient-start', theme.heroGradientStart)
    root.style.setProperty('--hero-gradient-end', theme.heroGradientEnd)
    root.style.setProperty('--hero-gradient-mid', theme.heroGradientMid)
    document.documentElement.className = 'theme-' + theme.key
  }

  applyTheme(currentTheme.value)

  const primaryColor = ref(currentTheme.value.primary)

  return { currentIndex, currentTheme, primaryColor, setTheme }
})
