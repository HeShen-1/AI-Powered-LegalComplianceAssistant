// 用户相关类型
export interface User {
  id: number
  username: string
  email: string
  fullName: string
  role: 'USER' | 'ADMIN'
  enabled: boolean
  createdAt: string
}

// 登录相关
export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
  fullName: string
}

// API响应基础类型
export interface ApiResponse<T = any> {
  success: boolean
  message: string
  data?: T
}

// 合同审查相关类型
export interface ContractReview {
  id: number
  userId: number
  filename: string
  filePath: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  createdAt: string
  completedAt?: string
  result?: any
}

export interface ContractUploadResponse {
  success: boolean
  message: string
  reviewId: number
  status: string
  supportedAnalysis: boolean
}

// AI聊天相关类型
export interface ChatMessage {
  id: string
  content: string
  type: 'user' | 'ai'
  timestamp: string
  isStreaming?: boolean
}

export interface ChatRequest {
  message: string
}

export interface ChatResponse {
  question: string
  answer: string
  timestamp: string
  type: string
}

export interface RagChatRequest {
  question: string
  maxResults?: number
}

// Agent相关类型
export interface AgentConsultRequest {
  message: string
}

export interface AgentAnalyzeRequest {
  contractContent: string
  question: string
}

// 知识库文档类型
export interface KnowledgeDocument {
  id: string
  filename: string
  category?: string
  description?: string
  uploadedAt: string
  chunksCount: number
}

export interface DocumentUploadResponse {
  success: boolean
  message: string
  docId: string
  chunksAdded: number
}

// 分页相关类型
export interface PaginationParams {
  page?: number
  size?: number
}

export interface PaginatedResponse<T> {
  success: boolean
  data: T[]
  totalElements: number
  totalPages: number
  currentPage: number
}

// SSE事件类型
export interface SSEEvent {
  type: 'progress' | 'result' | 'complete' | 'error'
  data: any
}

// 健康检查类型
export interface HealthStatus {
  status: 'UP' | 'DOWN'
  timestamp: string
  service: string
  version?: string
  database?: {
    healthy: boolean
    status: string
  }
  ai?: {
    healthy: boolean
    status: string
  }
}
