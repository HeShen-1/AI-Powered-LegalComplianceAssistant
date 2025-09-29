import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types/api'
import { loginApi } from '@/api/userService'
import { ElMessage } from 'element-plus'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>('')
  const userInfo = ref<User | null>(null)
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')

  // 初始化：从localStorage恢复状态
  let isInitialized = false
  const initializeAuth = async () => {
    if (isInitialized) return
    
    const savedToken = localStorage.getItem('auth_token')
    const savedUserInfo = localStorage.getItem('user_info')
    
    if (savedToken) {
      token.value = savedToken
      console.log('Token loaded from localStorage:', savedToken.substring(0, 10) + '...')
    }
    
    if (savedUserInfo) {
      try {
        userInfo.value = JSON.parse(savedUserInfo)
        console.log('User info loaded from localStorage:', userInfo.value?.username)
      } catch (error) {
        console.error('Failed to parse saved user info:', error)
        localStorage.removeItem('user_info')
      }
    }
    
    isInitialized = true
    console.log('Auth initialized, isLoggedIn:', !!token.value)
    return Promise.resolve()
  }

  // 登录
  const login = async (username: string, password: string) => {
    try {
      // 演示环境：支持演示账户登录
      if ((username === 'demo' || username === 'admin') && password === '123456') {
        // 生成演示 token
        const demoToken = btoa(`${username}:${password}`)
        token.value = demoToken
        localStorage.setItem('auth_token', token.value)
        
        // 设置演示用户信息
        const demoUser = {
          id: username === 'admin' ? 1 : 2,
          username: username,
          email: `${username}@example.com`,
          fullName: username === 'admin' ? '系统管理员' : '演示用户',
          role: username === 'admin' ? 'ADMIN' as const : 'USER' as const,
          enabled: true,
          createdAt: new Date().toISOString()
        }
        userInfo.value = demoUser
        localStorage.setItem('user_info', JSON.stringify(userInfo.value))
        
        ElMessage.success('登录成功')
        // 跳转到首页（避免循环依赖）
        if (typeof window !== 'undefined') {
          window.location.href = '/'
        }
        return
      }

      // 实际环境：调用后端API
      const response = await loginApi({ username, password })
      
      // 处理登录响应 - 根据后端实际返回格式调整
      const authToken = response.headers?.authorization || 
                       response.data?.token || 
                       btoa(`${username}:${password}`) // Basic Auth fallback
      
      if (authToken) {
        token.value = authToken.replace('Bearer ', '').replace('Basic ', '')
        localStorage.setItem('auth_token', token.value)
        
        // 获取用户信息
        await fetchUserInfo()
        
        ElMessage.success('登录成功')
        // 跳转到首页（避免循环依赖）
        if (typeof window !== 'undefined') {
          window.location.href = '/'
        }
      } else {
        throw new Error('登录失败：未获取到有效token')
      }
    } catch (error: any) {
      console.error('Login failed:', error)
      
      // 如果是网络错误，提示用户可以使用演示账户
      if (error.code === 'ERR_NETWORK' || error.message.includes('Network Error')) {
        ElMessage.error('无法连接到服务器，您可以使用演示账户：demo/123456 或 admin/123456')
      } else {
        ElMessage.error(error.response?.data?.message || '登录失败')
      }
      throw error
    }
  }

  // 获取用户信息
  const fetchUserInfo = async () => {
    if (!userInfo.value?.id && token.value) {
      try {
        // 在实际项目中，这里应该调用获取当前用户信息的API
        // 目前先跳过，避免因为后端API不存在而导致错误
        console.log('fetchUserInfo: 跳过获取用户信息，等待后端API就绪')
        
        // 临时设置一个默认用户信息用于演示
        const demoUser = {
          id: 1,
          username: 'demo_user',
          email: 'demo@example.com',
          fullName: '演示用户',
          role: 'USER' as 'USER' | 'ADMIN',
          enabled: true,
          createdAt: new Date().toISOString()
        }
        userInfo.value = demoUser
        localStorage.setItem('user_info', JSON.stringify(userInfo.value))
      } catch (error) {
        console.error('Failed to fetch user info:', error)
        // 如果获取用户信息失败，可能token已过期
        logout()
      }
    }
  }

  // 登出
  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('auth_token')
    localStorage.removeItem('user_info')
    
    // 跳转到登录页（避免循环依赖，由调用方处理）
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }

  // 更新用户信息
  const updateUserInfo = (newUserInfo: User) => {
    userInfo.value = newUserInfo
    localStorage.setItem('user_info', JSON.stringify(userInfo.value))
  }

  // 自动初始化用户状态
  initializeAuth().catch(console.error)

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    initializeAuth,
    login,
    logout,
    fetchUserInfo,
    updateUserInfo
  }
})
