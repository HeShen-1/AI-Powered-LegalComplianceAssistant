import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User } from '@/types/api'
import { loginApi, getUserInfoApi, refreshTokenApi, logoutApi } from '@/api/authService'
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
      // 调用后端API登录（包括演示账户也使用真实的JWT token）
      const response = await loginApi({ username, password })
      
      // 后端使用ApiResponse包装，所以数据在response.data.data中
      let responseToken = null
      let responseUser = null
      
      if (response.data && (response.data as any).success) {
        // 从ApiResponse的data字段中提取
        const apiData = (response.data as any).data
        if (apiData) {
          responseToken = apiData.token
          responseUser = apiData.user
        }
      }
      
      if (responseToken) {
        token.value = responseToken
        localStorage.setItem('auth_token', token.value)
        
        // 设置用户信息
        if (responseUser) {
          userInfo.value = responseUser
          localStorage.setItem('user_info', JSON.stringify(userInfo.value))
        }
        
        ElMessage.success('登录成功')
        return { success: true }
      } else {
        throw new Error('登录失败：未收到有效的认证令牌')
      }
    } catch (error: any) {
      console.error('Login failed:', error)
      
      // 如果是网络错误
      if (error.code === 'ERR_NETWORK' || error.message.includes('Network Error')) {
        ElMessage.error('无法连接到后端服务，请确保后端应用已启动（http://localhost:8080）')
      } else {
        ElMessage.error(error.response?.data?.message || '登录失败，请检查用户名和密码')
      }
      throw error
    }
  }

  // 获取用户信息
  const fetchUserInfo = async () => {
    if (!userInfo.value?.id && token.value) {
      try {
        const response = await getUserInfoApi()
        if (response.data) {
          userInfo.value = response.data
          localStorage.setItem('user_info', JSON.stringify(userInfo.value))
        }
      } catch (error) {
        console.error('Failed to fetch user info:', error)
        // 如果获取用户信息失败，可能token已过期
        logout()
      }
    }
  }

  // 刷新Token
  const refreshToken = async () => {
    try {
      const response = await refreshTokenApi()
      if (response.data.success && response.data.token) {
        token.value = response.data.token
        localStorage.setItem('auth_token', token.value)
        return true
      }
      return false
    } catch (error) {
      console.error('Token refresh failed:', error)
      logout()
      return false
    }
  }

  // 登出
  const logout = async () => {
    try {
      // 调用后端登出API
      if (token.value) {
        await logoutApi()
      }
    } catch (error) {
      console.error('Logout API failed:', error)
    } finally {
      // 清除本地状态
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('auth_token')
      localStorage.removeItem('user_info')
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
    refreshToken,
    fetchUserInfo,
    updateUserInfo
  }
})
