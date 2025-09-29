import apiClient from './index'
import type { HealthStatus } from '@/types/api'

// 基础健康检查
export const getHealthStatusApi = () => {
  return apiClient.get<HealthStatus>('/health')
}

// 详细健康检查
export const getDetailedHealthApi = () => {
  return apiClient.get<HealthStatus>('/health/detailed')
}

// AI服务功能测试
export const testAiServiceApi = () => {
  return apiClient.get('/health/ai/test')
}

// 系统信息
export const getSystemInfoApi = () => {
  return apiClient.get('/health/info')
}
