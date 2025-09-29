import axios, { AxiosError, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 创建Axios实例
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
apiClient.interceptors.request.use(
  async (config) => {
    try {
      // 动态导入 store 以避免循环依赖
      const { useUserStore } = await import('@/store/modules/user')
      const userStore = useUserStore()
      
      // 如果是表单数据，设置正确的Content-Type
      if (config.data instanceof FormData) {
        config.headers['Content-Type'] = 'multipart/form-data'
      }
      
      // 如果是登录请求，使用form-urlencoded
      if (config.url === '/login' && config.method === 'post') {
        config.headers['Content-Type'] = 'application/x-www-form-urlencoded'
      }
      
      // 添加认证头
      if (userStore.token && config.url !== '/login') {
        config.headers.Authorization = `Basic ${userStore.token}`
      }
    } catch (error) {
      console.error('Request interceptor error:', error)
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  async (error: AxiosError) => {
    try {
      // 动态导入以避免循环依赖
      const { useUserStore } = await import('@/store/modules/user')
      const userStore = useUserStore()
      
      if (error.response?.status === 401) {
        // 未授权，清除token并跳转到登录页
        userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
      } else if (error.response?.status === 403) {
        ElMessage.error('没有权限访问该资源')
      } else if (error.response?.status === 500) {
        ElMessage.error('服务器内部错误')
      } else if (error.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请稍后重试')
      } else if (error.code === 'ERR_NETWORK') {
        // 网络错误，不显示错误信息（在登录时会有特殊处理）
        console.log('Network error:', error.message)
      } else {
        ElMessage.error(error.message || '网络错误')
      }
    } catch (storeError) {
      console.error('Response interceptor error:', storeError)
    }
    
    return Promise.reject(error)
  }
)

export default apiClient
