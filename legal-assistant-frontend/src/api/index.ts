import axios, { AxiosError, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 创建Axios实例
const apiClient = axios.create({
  baseURL: '/api/v1', // 后端API统一前缀
  timeout: 60000, // 增加超时以适应AI长响应
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
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
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

// 标记是否正在刷新token，防止重复刷新
let isRefreshing = false
// 存储等待刷新的请求
let failedQueue: Array<{ resolve: (value?: any) => void; reject: (reason?: any) => void }> = []

// 处理等待队列
const processQueue = (error: any = null) => {
  failedQueue.forEach(promise => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve()
    }
  })
  failedQueue = []
}

// 响应拦截器
apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as any
    
    try {
      // 动态导入以避免循环依赖
      const { useUserStore } = await import('@/store/modules/user')
      const userStore = useUserStore()
      
      if (error.response?.status === 401) {
        // 排除不需要刷新token的请求
        const excludedUrls = ['/auth/login', '/auth/register', '/auth/refresh']
        const isExcluded = excludedUrls.some(url => originalRequest?.url?.includes(url))
        
        if (isExcluded) {
          // 登录/注册/刷新请求失败，直接返回错误
          return Promise.reject(error)
        }
        
        // 如果已经重试过，不再重试
        if (originalRequest._retry) {
          // 清除token并跳转登录页
          ElMessage.error('登录已过期，请重新登录')
          userStore.logout()
          if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
            window.location.href = '/login'
          }
          return Promise.reject(error)
        }
        
        // 如果正在刷新token，将请求加入队列
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject })
          }).then(() => {
            return apiClient.request(originalRequest)
          }).catch(err => {
            return Promise.reject(err)
          })
        }
        
        originalRequest._retry = true
        isRefreshing = true
        
        try {
          // 尝试刷新token
          const refreshSuccess = await userStore.refreshToken()
          
          if (refreshSuccess) {
            // 刷新成功，处理队列中的请求
            processQueue(null)
            // 重新发送原请求
            return apiClient.request(originalRequest)
          } else {
            // 刷新失败
            processQueue(error)
            ElMessage.error('登录已过期，请重新登录')
            userStore.logout()
            if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
              window.location.href = '/login'
            }
            return Promise.reject(error)
          }
        } finally {
          isRefreshing = false
        }
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
