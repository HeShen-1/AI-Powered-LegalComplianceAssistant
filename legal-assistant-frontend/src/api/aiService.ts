import apiClient from './index'
import type { 
  ChatRequest, 
  ChatResponse, 
  RagChatRequest, 
  AgentConsultRequest, 
  AgentAnalyzeRequest 
} from '@/types/api'

// 基础聊天
export const basicChatApi = (data: ChatRequest) => {
  return apiClient.post<ChatResponse>('/ai/chat', data)
}

// RAG增强聊天
export const ragChatApi = (data: RagChatRequest) => {
  return apiClient.post<ChatResponse>('/ai/chat/rag', data)
}

// 智能法律顾问
export const agentConsultApi = (data: AgentConsultRequest) => {
  return apiClient.post<ChatResponse>('/ai/agent/consult', data)
}

// 智能合同分析
export const agentAnalyzeContractApi = (data: AgentAnalyzeRequest) => {
  return apiClient.post<ChatResponse>('/ai/agent/analyze-contract', data)
}

// 创建聊天SSE连接
export const createChatSSE = (
  endpoint: string, 
  data: any, 
  onMessage: (event: MessageEvent) => void,
  onError?: (event: Event) => void,
  onOpen?: (event: Event) => void
) => {
  // 首先发送POST请求启动流式响应
  return apiClient.post(endpoint, data, {
    responseType: 'stream',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache'
    }
  }).then(response => {
    // 如果后端支持直接返回EventSource连接
    // 这里可能需要根据实际后端实现调整
    const eventSource = new EventSource(`/api${endpoint}`, {
      withCredentials: true
    })
    
    eventSource.onmessage = onMessage
    
    if (onError) {
      eventSource.onerror = onError
    }
    
    if (onOpen) {
      eventSource.onopen = onOpen
    }
    
    return eventSource
  })
}
