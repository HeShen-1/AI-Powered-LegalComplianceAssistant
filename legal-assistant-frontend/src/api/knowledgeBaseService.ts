import apiClient from './index'
import type { 
  KnowledgeDocument, 
  DocumentUploadResponse, 
  PaginatedResponse, 
  PaginationParams,
  ApiResponse 
} from '@/types/api'

// 上传单个文档
export const uploadDocumentApi = (file: File, category?: string, description?: string) => {
  const formData = new FormData()
  formData.append('file', file)
  if (category) formData.append('category', category)
  if (description) formData.append('description', description)
  
  return apiClient.post<DocumentUploadResponse>('/knowledge-base/documents', formData)
}

// 批量上传文档
export const batchUploadDocumentsApi = (files: File[], category?: string) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  if (category) formData.append('category', category)
  
  return apiClient.post<ApiResponse<{
    totalFiles: number
    successCount: number
    failedCount: number
    details: any[]
  }>>('/knowledge-base/documents/batch', formData)
}

// 获取文档列表
export const getDocumentsApi = (params?: PaginationParams & { category?: string }) => {
  return apiClient.get<PaginatedResponse<KnowledgeDocument>>('/knowledge-base/documents', { params })
}

// 删除文档
export const deleteDocumentApi = (docId: string) => {
  return apiClient.delete<ApiResponse>(`/knowledge-base/documents/${docId}`)
}

// 重建索引
export const rebuildIndexApi = () => {
  return apiClient.post<ApiResponse>('/vector-index/rebuild')
}

// 重新处理单个文档
export const reprocessDocumentApi = (docId: string) => {
  return apiClient.post<ApiResponse>(`/knowledge-base/documents/${docId}/reprocess`)
}

// 获取文档的向量块信息
export const getDocumentChunksApi = (docId: string) => {
  return apiClient.get<ApiResponse<any[]>>(`/knowledge-base/documents/${docId}/chunks`)
}