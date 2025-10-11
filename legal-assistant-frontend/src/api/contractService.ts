import apiClient from './index'
import type { 
  ContractUploadResponse, 
  ContractReview, 
  PaginatedResponse, 
  PaginationParams,
  ApiResponse 
} from '@/types/api'

// 上传合同文件
export const uploadContractApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  
  return apiClient.post<ContractUploadResponse>('/contracts/upload', formData)
}

// 同步合同审查
export const analyzeContractApi = (reviewId: number) => {
  return apiClient.post<ApiResponse<ContractReview>>(`/contracts/${reviewId}/analyze`)
}

// 获取我的审查记录
export const getMyReviewsApi = (params?: PaginationParams) => {
  return apiClient.get<PaginatedResponse<ContractReview>>('/contracts/my-reviews', { params })
}

// 获取审查详情
export const getReviewDetailApi = (reviewId: number) => {
  return apiClient.get<ApiResponse<ContractReview>>(`/contracts/${reviewId}`)
}

// 下载审查报告PDF
export const downloadReportApi = (reviewId: number) => {
  return apiClient.get(`/contracts/${reviewId}/report`, { 
    responseType: 'blob' 
  })
}

// 删除审查记录
export const deleteReviewApi = (reviewId: number) => {
  return apiClient.delete<ApiResponse<void>>(`/contracts/${reviewId}`)
}

// 创建SSE连接进行异步审查
export const createAnalysisSSE = (reviewId: number, token: string | null, onMessage: (event: MessageEvent) => void, onError?: (event: Event) => void) => {
  // 在开发环境下需要使用完整的URL指向后端服务器
  const isDev = import.meta.env.DEV
  const baseUrl = isDev ? 'http://localhost:8080' : ''
  
  const url = token 
    ? `${baseUrl}/api/v1/contracts/${reviewId}/analyze-async?token=${encodeURIComponent(token)}`
    : `${baseUrl}/api/v1/contracts/${reviewId}/analyze-async`
  
  const eventSource = new EventSource(url, {
    withCredentials: true
  })
  
  eventSource.onmessage = onMessage
  
  if (onError) {
    eventSource.onerror = onError
  }
  
  return eventSource
}
