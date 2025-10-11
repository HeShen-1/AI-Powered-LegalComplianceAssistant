import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // 侧边栏状态
  const sidebarCollapsed = ref<boolean>(false)
  
  // 加载状态
  const globalLoading = ref<boolean>(false)
  
  // 主题设置
  const theme = ref<'light' | 'dark'>('light')
  
  // 语言设置
  const locale = ref<'zh-CN' | 'en-US'>('zh-CN')
  
  // 页面标题
  const pageTitle = ref<string>('法律合规智能审查助手')
  
  // 初始化：从localStorage恢复状态
  const initializeApp = () => {
    const savedSidebarState = localStorage.getItem('sidebar_collapsed')
    const savedTheme = localStorage.getItem('app_theme')
    const savedLocale = localStorage.getItem('app_locale')
    
    if (savedSidebarState !== null) {
      sidebarCollapsed.value = JSON.parse(savedSidebarState)
    }
    
    if (savedTheme && (savedTheme === 'light' || savedTheme === 'dark')) {
      theme.value = savedTheme
    }
    
    if (savedLocale && (savedLocale === 'zh-CN' || savedLocale === 'en-US')) {
      locale.value = savedLocale
    }
  }
  
  // 切换侧边栏状态
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebar_collapsed', JSON.stringify(sidebarCollapsed.value))
  }
  
  // 设置侧边栏状态
  const setSidebarCollapsed = (collapsed: boolean) => {
    sidebarCollapsed.value = collapsed
    localStorage.setItem('sidebar_collapsed', JSON.stringify(collapsed))
  }
  
  // 设置全局加载状态
  const setGlobalLoading = (loading: boolean) => {
    globalLoading.value = loading
  }
  
  // 切换主题
  const toggleTheme = () => {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('app_theme', theme.value)
    
    // 应用主题到document
    document.documentElement.setAttribute('data-theme', theme.value)
  }
  
  // 设置主题
  const setTheme = (newTheme: 'light' | 'dark') => {
    theme.value = newTheme
    localStorage.setItem('app_theme', newTheme)
    document.documentElement.setAttribute('data-theme', newTheme)
  }
  
  // 设置语言
  const setLocale = (newLocale: 'zh-CN' | 'en-US') => {
    locale.value = newLocale
    localStorage.setItem('app_locale', newLocale)
  }
  
  // 设置页面标题
  const setPageTitle = (title: string) => {
    pageTitle.value = title
    document.title = title
  }
  
  // 初始化应用状态
  initializeApp()
  
  return {
    // 状态
    sidebarCollapsed,
    globalLoading,
    theme,
    locale,
    pageTitle,
    
    // 方法
    initializeApp,
    toggleSidebar,
    setSidebarCollapsed,
    setGlobalLoading,
    toggleTheme,
    setTheme,
    setLocale,
    setPageTitle
  }
})
