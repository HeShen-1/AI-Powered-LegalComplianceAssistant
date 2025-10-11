import apiClient from './index'
import { UnifiedChatRequest, UnifiedChatResponse } from '@/types/api'

/**
 * 从可能包含多个JSON对象的字符串中提取单个JSON对象
 * 处理类似 {"a":1}{"b":2} 这种连在一起的JSON对象
 */
function extractJsonObjects(str: string): string[] {
  const objects: string[] = []
  let braceCount = 0
  let start = 0
  
  for (let i = 0; i < str.length; i++) {
    const char = str[i]
    
    if (char === '{') {
      if (braceCount === 0) {
        start = i // 记录新JSON对象的开始位置
      }
      braceCount++
    } else if (char === '}') {
      braceCount--
      if (braceCount === 0) {
        // 完整的JSON对象结束
        const jsonStr = str.substring(start, i + 1)
        if (jsonStr.trim()) {
          objects.push(jsonStr.trim())
        }
      }
    }
  }
  
  // 如果只有一个对象或者解析失败，返回原字符串
  if (objects.length === 0) {
    return [str.trim()]
  }
  
  return objects
}

// 统一聊天接口 (非流式)
export const unifiedChatApi = (data: UnifiedChatRequest) => {
  return apiClient.post<UnifiedChatResponse>('/chat', data)
}

// 重置会话记忆
export const resetChatSessionApi = (sessionId: string) => {
  return apiClient.delete(`/chat/session/${sessionId}`)
}

// 获取会话历史
export const getChatHistoryApi = (sessionId: string) => {
  return apiClient.get(`/chat/session/${sessionId}/history`)
}

// ==================== 聊天历史管理 API ====================

// 获取当前用户的所有聊天会话
export const getChatSessions = () => {
  return apiClient.get('/chat/sessions')
}

// 获取指定会话的所有消息
export const getChatMessages = (sessionId: string) => {
  return apiClient.get(`/chat/sessions/${sessionId}`)
}

// 删除指定的聊天会话
export const deleteChatSession = (sessionId: string) => {
  return apiClient.delete(`/chat/sessions/${sessionId}`)
}

// ==================== 流式聊天 ====================

// 流式聊天 - 创建SSE连接的辅助函数
// 注意：EventSource不支持自定义header，建议使用createChatStreamPost代替
export const createChatStream = (
  request: UnifiedChatRequest,
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void
) => {
  const token = localStorage.getItem('auth_token')
  
  // 检查token是否存在
  if (!token) {
    onError(new Error('未登录或登录已过期，请先登录'))
    if (typeof window !== 'undefined') {
      window.location.href = '/login'
    }
    return null as any
  }
  
  // 构建查询参数（包括token，因为EventSource不支持自定义header）
  const params = new URLSearchParams({
    message: request.message,
    modelType: request.modelType,
    token: token, // 通过URL传递token（注意：这种方式不太安全，建议使用POST方式）
    ...(request.conversationId && { conversationId: request.conversationId })
  })

  // 创建EventSource连接
  const eventSource = new EventSource(`/api/v1/chat/stream?${params.toString()}`)

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'content') {
        onMessage(data.content)
      } else if (data.type === 'complete') {
        onComplete()
        eventSource.close()
      }
    } catch (error) {
      console.error('Failed to parse SSE message:', error)
      onMessage(event.data) // 如果解析失败，直接传递原始数据
    }
  }

  eventSource.onerror = (event) => {
    console.error('SSE connection error:', event)
    onError(new Error('SSE connection failed'))
    eventSource.close()
  }

  return eventSource
}

// POST方式的流式聊天 (使用fetch + ReadableStream)
export const createChatStreamPost = async (
  request: UnifiedChatRequest,
  onMessage: (data: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void
) => {
  try {
    const token = localStorage.getItem('auth_token')
    
    // 检查token是否存在
    if (!token) {
      const error = new Error('未登录或登录已过期，请先登录')
      onError(error)
      // 跳转到登录页
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
      return
    }
    
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'Authorization': `Bearer ${token}`
    }
    
    console.log('🚀 启动流式聊天请求:', request)
    
    const response = await fetch('/api/v1/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(request)
    })

    console.log('📡 流式响应状态:', response.status, response.statusText)

    if (!response.ok) {
      // 处理401未授权错误
      if (response.status === 401) {
        const error = new Error('登录已过期，请重新登录')
        onError(error)
        // 清除token并跳转到登录页
        localStorage.removeItem('auth_token')
        localStorage.removeItem('user_info')
        if (typeof window !== 'undefined') {
          window.location.href = '/login'
        }
        return
      }
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No readable stream available')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let messageCount = 0

    console.log('📖 开始读取流式数据...')

    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        console.log('✅ 流式数据读取完成，总消息数:', messageCount)
        onComplete()
        break
      }

      const chunk = decoder.decode(value, { stream: true })
      console.log('📦 接收数据块:', JSON.stringify(chunk))
      
      buffer += chunk
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留最后一个不完整的行

      for (const line of lines) {
        if (line.trim() === '') continue // 跳过空行

        console.log('📝 处理数据行:', JSON.stringify(line))

        // 处理SSE格式的数据
        let dataLine = line.trim()
        
        // 处理各种可能的SSE数据前缀格式
        if (dataLine.startsWith('data: ')) {
          dataLine = dataLine.substring(6) // 移除 'data: ' 前缀（有空格）
        } else if (dataLine.startsWith('data:')) {
          dataLine = dataLine.substring(5) // 移除 'data:' 前缀（无空格）
        }
        
        // 再次清理可能的空白字符
        dataLine = dataLine.trim()
        
        console.log('🧹 清理后的数据行:', JSON.stringify(dataLine))

        // 后端流式响应处理
        try {
          // 检查并处理可能的特殊标记，如[DONE]
          if (dataLine.includes('[DONE]')) {
            console.log('🏁 接收到完成标记 [DONE]')
            onComplete()
            return
          }

          // 处理可能包含多个JSON对象的情况
          if (dataLine.startsWith('{')) {
            // 尝试分割可能连在一起的多个JSON对象
            const jsonObjects = extractJsonObjects(dataLine)
            
            for (const jsonStr of jsonObjects) {
              try {
                const parsed = JSON.parse(jsonStr)
                console.log('🔍 解析的JSON数据:', parsed)
                
                // 修正：先检查type字段，确保正确处理所有事件类型
                console.log('🎯 事件类型判断:', parsed.type)
                
                if (parsed.type === 'complete') {
                  console.log('🎉 接收到完成事件，调用onComplete()')
                  onComplete()
                  return
                } else if (parsed.type === 'error') {
                  console.error('❌ 接收到错误事件，调用onError():', parsed.error)
                  onError(new Error(parsed.error || '服务器在流式传输中返回错误'))
                  return
                } else if (parsed.type === 'content') {
                  // 处理内容事件 - 支持content字段在任意位置
                  const content = parsed.content
                  console.log('📄 内容事件处理 - content值:', JSON.stringify(content), '类型:', typeof content)
                  
                  if (content !== undefined && content !== null) {
                    const contentStr = String(content)
                    console.log('✨ 发送内容片段给UI:', JSON.stringify(contentStr))
                    onMessage(contentStr) // 确保转换为字符串
                    messageCount++
                  } else {
                    console.warn('⚠️ content字段为空或未定义:', parsed)
                  }
                } else {
                  console.log('❓ 未知消息类型，跳过处理:', parsed.type)
                  // 对于未知类型，不进行任何操作，避免错误处理
                }
              } catch (parseError) {
                console.warn('⚠️ 单个JSON对象解析失败:', jsonStr, parseError)
                // 解析失败时不发送原始内容，避免显示JSON格式
              }
            }
          } else {
            // 非JSON数据，直接作为内容发送（但要验证不是意外的JSON）
            if (!dataLine.includes('{"type"') && !dataLine.includes('"content"')) {
              console.log('📤 发送原始内容:', JSON.stringify(dataLine))
              onMessage(dataLine)
              messageCount++
            } else {
              console.warn('⚠️ 疑似JSON格式但解析失败，跳过:', dataLine)
            }
          }
        } catch (error) {
          // 如果整体处理失败，记录错误但不发送原始数据
          console.error('⚠️ 数据处理失败，跳过该行:', JSON.stringify(dataLine), error)
        }
      }
    }
  } catch (error) {
    console.error('💥 流式聊天错误:', error)
    onError(error as Error)
  }
}
