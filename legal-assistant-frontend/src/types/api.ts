export type ReviewStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type ChatModelType = 'BASIC' | 'ADVANCED' | 'ADVANCED_RAG' | 'UNIFIED'

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
  originalFilename: string
  reviewStatus: ReviewStatus
  riskLevel?: RiskLevel
  totalRisks?: number
  createdAt: string
  completedAt?: string
  filePath?: string
  fileSize?: number
  fileHash?: string
  summary?: any
  detailedAnalysis?: any
  riskClauses?: any[]
  result?: any
}

export interface ContractUploadData {
  reviewId: number
  status: ReviewStatus
  supportedAnalysis: boolean
  originalFilename?: string
  fileHash?: string
  fileSize?: number
}

// 统一聊天接口类型
export interface UnifiedChatRequest {
  message: string
  modelType: ChatModelType
  conversationId?: string
  useKnowledgeBase?: boolean
  modelName?: string
  stream?: boolean
}

export interface UnifiedChatMetadata {
  actualModel?: string
  routeReason?: string
  fallbackUsed?: boolean
  sourceCount?: number
  latencyMs?: number
  requestedModel?: string
  modelType?: string
  responseType?: string
  usedKnowledgeBase?: boolean
  [key: string]: any
}

export interface UnifiedChatResponse {
  question: string
  answer: string
  conversationId: string
  modelType: string
  modelName?: string
  usedKnowledgeBase?: boolean
  hasKnowledgeMatch?: boolean
  sourceCount?: number
  sources?: string[]
  memoryEnabled?: boolean
  responseType?: string
  timestamp: string
  duration?: number
  metadata?: UnifiedChatMetadata
}

export interface ChatMessage {
  id: string
  content: string
  role: 'user' | 'ai'
  timestamp: string
  isStreaming?: boolean
  metadata?: UnifiedChatMetadata
}

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

export interface KnowledgeDocumentList {
  content: KnowledgeDocument[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
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
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
  first?: boolean
  last?: boolean
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
