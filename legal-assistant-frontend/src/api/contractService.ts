import apiClient from './index'
import type { 
  ContractUploadData,
  ContractReview, 
  PaginatedResponse, 
  PaginationParams,
  ApiResponse 
} from '@/types/api'

// 上传合同文件
export const uploadContractApi = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  
  return apiClient.post<ApiResponse<ContractUploadData>>('/contracts/upload', formData)
}

// 同步合同审查
export const analyzeContractApi = (reviewId: number) => {
  return apiClient.post<ApiResponse<ContractReview>>(`/contracts/${reviewId}/analyze`)
}

// 获取我的审查记录
export const getMyReviewsApi = (params?: PaginationParams) => {
  return apiClient.get<ApiResponse<PaginatedResponse<ContractReview>>>('/contracts/my-reviews', { params })
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

export interface ContractAnalysisStream {
  readyState: number
  url: string
  onmessage: ((event: MessageEvent<string>) => void) | null
  onerror: ((event: Event) => void) | null
  onopen: ((event: Event) => void) | null
  addEventListener: (type: string, listener: (event: MessageEvent<string>) => void) => void
  close: () => void
}

const STREAM_CONNECTING = 0
const STREAM_OPEN = 1
const STREAM_CLOSED = 2

const getContractStreamBaseUrl = () => (import.meta.env.DEV ? 'http://localhost:8080' : '')

export const createAnalysisSSEAuth = (reviewId: number, token: string): ContractAnalysisStream => {
  const url = `${getContractStreamBaseUrl()}/api/v1/contracts/${reviewId}/analyze-async-auth`
  const controller = new AbortController()
  const listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>()

  let closedByUser = false

  const stream: ContractAnalysisStream = {
    readyState: STREAM_CONNECTING,
    url,
    onmessage: null,
    onerror: null,
    onopen: null,
    addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
      const bucket = listeners.get(type) || []
      bucket.push(listener)
      listeners.set(type, bucket)
    },
    close() {
      closedByUser = true
      stream.readyState = STREAM_CLOSED
      controller.abort()
    }
  }

  const emitMessageEvent = (type: string, data: string) => {
    const event = new MessageEvent<string>(type, { data })
    if (type === 'message') {
      stream.onmessage?.(event)
    }
    ;(listeners.get(type) || []).forEach(listener => listener(event))
  }

  const emitErrorEvent = () => {
    stream.onerror?.(new Event('error'))
  }

  const dispatchBlock = (block: string) => {
    if (!block.trim()) return

    let eventName = 'message'
    const dataLines: string[] = []

    block.split('\n').forEach(line => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim() || 'message'
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    })

    emitMessageEvent(eventName, dataLines.join('\n'))
  }

  ;(async () => {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`
        },
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      if (!response.body) {
        throw new Error('No response body')
      }

      stream.readyState = STREAM_OPEN
      stream.onopen?.(new Event('open'))

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() || ''
        blocks.forEach(dispatchBlock)
      }

      if (buffer.trim()) {
        dispatchBlock(buffer)
      }
    } catch (error) {
      if (!closedByUser && !controller.signal.aborted) {
        stream.readyState = STREAM_CLOSED
        emitErrorEvent()
        console.error('Contract SSE auth stream failed:', error)
      }
      return
    }

    stream.readyState = STREAM_CLOSED
  })()

  return stream
}
