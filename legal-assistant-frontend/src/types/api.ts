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

export interface LoginResponse {
  success?: boolean
  message?: string
  token?: string
  access_token?: string
  accessToken?: string
  user?: User
  userInfo?: User
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
  originalFilename: string  // 修复：使用正确的字段名
  filePath: string
  reviewStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'  // 修复：使用正确的字段名
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

// 统一聊天接口类型
export interface UnifiedChatRequest {
  message: string  // 与后端字段名保持一致
  modelType: 'BASIC' | 'ADVANCED' | 'ADVANCED_RAG'  // 与后端字段名保持一致
  conversationId?: string  // 与后端字段名保持一致
  useKnowledgeBase?: boolean
  modelName?: string
  stream?: boolean
}

export interface UnifiedChatResponse {
  question: string              // 用户的原始问题
  answer: string                // AI生成的回答
  conversationId: string        // 会话ID
  modelType: string             // 使用的模型类型
  modelName?: string            // 使用的具体模型名称
  usedKnowledgeBase?: boolean   // 是否使用了知识库
  hasKnowledgeMatch?: boolean   // 是否找到了相关知识
  sourceCount?: number          // 知识来源数量
  sources?: string[]            // 知识来源列表
  memoryEnabled?: boolean       // 是否启用了对话记忆
  responseType?: string         // 响应类型
  timestamp: string             // 响应时间戳
  duration?: number             // 处理耗时（毫秒）
  metadata?: Record<string, any> // 额外的元数据
}

// AI聊天相关类型
export interface ChatMessage {
  id: string
  content: string
  role: 'user' | 'ai'
  timestamp: string
  isStreaming?: boolean
}

// 聊天历史相关类型
export interface ChatSessionDto {
  id: string
  title: string
  updatedAt: string
  messageCount?: number
}

export interface ChatMessageDto {
  id: number
  role: 'user' | 'assistant'
  content: string
  metadata?: Record<string, any>
  createdAt: string
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
  size: number
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
