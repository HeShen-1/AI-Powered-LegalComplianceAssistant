import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatSessionDto, ChatMessageDto } from '@/types/api'
import { getChatSessions, getChatMessages, deleteChatSession } from '@/api/chatService'
import { ElMessage } from 'element-plus'

/**
 * 聊天历史状态管理
 * 管理聊天会话列表和当前会话的消息
 */
export const useChatHistoryStore = defineStore('chatHistory', () => {
  // 状态
  const sessions = ref<ChatSessionDto[]>([])
  const activeSessionId = ref<string | null>(null)
  const messages = ref<ChatMessageDto[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  
  // 防抖：记录上次请求时间，避免短时间内重复请求
  let lastFetchTime = 0
  const FETCH_DEBOUNCE_MS = 1000 // 1秒内不重复请求

  /**
   * 获取所有会话列表
   */
  const fetchSessions = async () => {
    // 防抖检查：如果距离上次请求不到1秒，则跳过
    const now = Date.now()
    if (now - lastFetchTime < FETCH_DEBOUNCE_MS) {
      console.log('⏭️ 跳过重复的会话列表请求（距离上次请求不到1秒）')
      return
    }
    lastFetchTime = now
    
    loading.value = true
    error.value = null
    try {
      const response = await getChatSessions()
      
      // 处理不同的响应格式
      if (response.data) {
        if (Array.isArray(response.data)) {
          sessions.value = response.data
        } else if (response.data.data && Array.isArray(response.data.data)) {
          sessions.value = response.data.data
        } else {
          console.warn('意外的响应格式:', response.data)
          sessions.value = []
        }
      }
      
      console.log('获取会话列表成功，共', sessions.value.length, '个会话')
    } catch (err: any) {
      console.error('获取会话列表失败:', err)
      error.value = err.message || '获取会话列表失败'
      ElMessage.error('获取聊天历史失败')
      sessions.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取指定会话的消息
   * @param sessionId 会话ID
   */
  const fetchMessages = async (sessionId: string) => {
    if (!sessionId) {
      console.warn('会话ID为空，无法获取消息')
      return
    }

    loading.value = true
    error.value = null
    try {
      const response = await getChatMessages(sessionId)
      
      // 处理不同的响应格式
      if (response.data) {
        if (Array.isArray(response.data)) {
          messages.value = response.data
        } else if (response.data.data && Array.isArray(response.data.data)) {
          messages.value = response.data.data
        } else {
          console.warn('意外的响应格式:', response.data)
          messages.value = []
        }
      }
      
      activeSessionId.value = sessionId
      console.log('获取会话消息成功，共', messages.value.length, '条消息')
    } catch (err: any) {
      console.error('获取会话消息失败:', err)
      error.value = err.message || '获取会话消息失败'
      
      // 403错误表示没有权限
      if (err.response?.status === 403) {
        ElMessage.error('您没有权限访问该会话')
      } else {
        ElMessage.error('获取消息失败')
      }
      
      messages.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 删除会话
   * @param sessionId 会话ID
   */
  const deleteSessionAction = async (sessionId: string) => {
    if (!sessionId) {
      console.warn('会话ID为空，无法删除')
      return false
    }

    try {
      await deleteChatSession(sessionId)
      
      // 从列表中移除该会话
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      
      // 如果删除的是当前激活的会话，清空消息列表
      if (activeSessionId.value === sessionId) {
        activeSessionId.value = null
        messages.value = []
      }
      
      ElMessage.success('会话已删除')
      console.log('删除会话成功:', sessionId)
      return true
    } catch (err: any) {
      console.error('删除会话失败:', err)
      
      // 403错误表示没有权限
      if (err.response?.status === 403) {
        ElMessage.error('您没有权限删除该会话')
      } else {
        ElMessage.error('删除会话失败')
      }
      
      return false
    }
  }

  /**
   * 创建新会话（清空当前状态）
   */
  const createNewSession = () => {
    activeSessionId.value = null
    messages.value = []
    console.log('已创建新会话')
  }

  /**
   * 添加消息到当前会话（本地操作，用于实时更新UI）
   * @param message 消息对象
   */
  const addMessage = (message: ChatMessageDto) => {
    messages.value.push(message)
  }

  /**
   * 清空所有状态
   */
  const clearAll = () => {
    sessions.value = []
    activeSessionId.value = null
    messages.value = []
    error.value = null
  }

  /**
   * 设置活动会话ID（不加载消息）
   * @param sessionId 会话ID
   */
  const setActiveSessionId = (sessionId: string | null) => {
    activeSessionId.value = sessionId
  }

  return {
    // 状态
    sessions,
    activeSessionId,
    messages,
    loading,
    error,
    
    // Actions
    fetchSessions,
    fetchMessages,
    deleteSession: deleteSessionAction,
    createNewSession,
    addMessage,
    clearAll,
    setActiveSessionId
  }
})

