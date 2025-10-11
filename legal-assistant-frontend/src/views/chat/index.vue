<template>
  <div class="chat-container">
    <!-- 聊天历史侧边栏 -->
    <div class="history-sidebar" :class="{ collapsed: isSidebarCollapsed }">
      <ChatHistoryPanel 
        @new-chat="handleNewChat" 
        @select-session="handleSelectSession" 
      />
    </div>

    <!-- 侧边栏折叠/展开按钮 -->
    <div class="sidebar-toggle" @click="toggleSidebar">
      <el-icon>
        <DArrowLeft v-if="!isSidebarCollapsed" />
        <DArrowRight v-if="isSidebarCollapsed" />
      </el-icon>
    </div>

    <!-- 主聊天区域 -->
    <div class="main-chat-area">
      <el-card class="chat-card" shadow="never">
        <!-- 聊天头部 -->
        <template #header>
        <div class="chat-header">
          <div class="header-left">
            <h3 class="chat-title">🤖 AI智能问答</h3>
            <el-tag :type="modeTagType" size="small">{{ modeText }}</el-tag>
          </div>
        </div>
      </template>
      
      <!-- 聊天内容区域包装器 -->
      <div class="chat-content-wrapper">
        <!-- 消息列表区域 -->
        <div ref="messagesContainer" class="messages-container" @scroll="handleScroll">
          <div v-if="messages.length === 0" class="empty-state">
            <el-empty description="开始您的AI法律咨询之旅">
              <template #image>
                <el-icon size="60" color="#409EFF">
                  <ChatDotRound />
                </el-icon>
              </template>
            </el-empty>
          </div>
          
          <div
            v-for="message in messages"
            :key="message.id"
            :class="['message-item', message.role]"
          >
            <!-- 用户消息 -->
            <div v-if="message.role === 'user'" class="message-wrapper user-message">
              <div class="message-content">
                <div class="message-text">{{ message.content }}</div>
                <div class="message-time">{{ formatTime(message.timestamp) }}</div>
              </div>
            </div>
            
            <!-- AI消息 -->
            <div v-else class="message-wrapper ai-message">
              <div class="message-content">
                <div class="message-text">
                  <!-- 使用v-html渲染Markdown内容 - 实时流式渲染 -->
                  <div
                    class="markdown-content"
                    v-html="renderMarkdown(message.content)"
                  ></div>
                  <!-- 流式输入时的打字光标 -->
                  <span v-if="message.isStreaming" class="typing-cursor"></span>
                </div>
                <div class="message-time">{{ formatTime(message.timestamp) }}</div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 滚动到底部按钮 - 移到滚动容器外部 -->
        <transition name="fade-slide">
          <div v-show="showScrollToBottom" class="scroll-to-bottom" @click="scrollToBottom">
            <el-icon :size="20">
              <ArrowDown />
            </el-icon>
          </div>
        </transition>
      </div>
      
      <!-- 输入区域 -->
      <div class="input-area">
        <!-- 输入框 -->
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入您的法律问题..."
            :disabled="isLoading"
            @keydown.ctrl.enter="sendMessage"
            @keydown.meta.enter="sendMessage"
          />
          <div class="input-actions">
            <div class="input-tip">
              <el-text size="small" type="info">Ctrl + Enter 发送</el-text>
            </div>
            <el-button
              type="primary"
              :loading="isLoading"
              :disabled="!inputMessage.trim()"
              @click="sendMessage"
            >
              {{ isLoading ? '发送中...' : '发送' }}
            </el-button>
          </div>
        </div>
      </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import {
  ChatDotRound,
  User,
  Delete,
  DArrowLeft,
  DArrowRight,
  ArrowDown
} from '@element-plus/icons-vue'
import { createChatStreamPost, resetChatSessionApi, getChatHistoryApi } from '@/api/chatService'
import type { ChatMessage, UnifiedChatRequest, ChatMessageDto } from '@/types/api'
import ChatHistoryPanel from './ChatHistoryPanel.vue'
import { useChatHistoryStore } from '@/store/modules/chatHistory'

// 配置marked - 支持流式渲染的配置
marked.setOptions({
  highlight: function(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  },
  langPrefix: 'hljs language-',
  breaks: true,
  gfm: true, // 支持GitHub风格的Markdown
  pedantic: false // 不严格遵循markdown.pl，更宽容的解析
})

// 使用chatHistory store
const chatHistoryStore = useChatHistoryStore()

// 响应式数据
const messagesContainer = ref<HTMLElement>()
const inputMessage = ref('')
const isLoading = ref(false)
const chatMode = ref<'BASIC' | 'ADVANCED' | 'ADVANCED_RAG' | 'UNIFIED'>('UNIFIED') // 统一模式
const sessionId = ref<string | null>(null) // 初始化为null，表示新会话
const messages = ref<ChatMessage[]>([])
const aiAvatar = ref('')
const isSidebarCollapsed = ref(false) // 侧边栏折叠状态
const isNewSession = ref(true) // 标记是否为新会话
const showScrollToBottom = ref(false) // 控制滚动到底部按钮的显示
const isUserScrolling = ref(false) // 标记用户是否手动滚动到非底部位置

// 计算属性
const modeText = computed(() => {
  const modeMap = {
    BASIC: '基础RAG',
    ADVANCED: '高级Agent',
    ADVANCED_RAG: '高级RAG',
    UNIFIED: '智能模式'
  }
  return modeMap[chatMode.value]
})

const modeTagType = computed(() => {
  const typeMap = {
    BASIC: 'success',
    ADVANCED: 'warning',
    ADVANCED_RAG: 'danger',
    UNIFIED: 'primary'
  }
  return typeMap[chatMode.value] as 'success' | 'warning' | 'danger' | 'primary'
})

// 工具函数
const formatTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 实时Markdown渲染函数
const renderMarkdown = (content: string) => {
  try {
    if (!content || typeof content !== 'string') {
      console.warn('⚠️ 无效的内容类型:', typeof content, content)
      return ''
    }
    
    // 清理内容：移除可能的JSON残留
    let cleanContent = content.trim()
    
    // 检测并清理JSON格式的内容
    if (cleanContent.includes('{"type":"content"')) {
      console.warn('⚠️ 检测到JSON格式内容，尝试清理:', cleanContent.substring(0, 100) + '...')
      // 这种情况不应该发生，但作为安全措施
      return '⚠️ 数据格式错误，请刷新页面重试'
    }
    
    console.log('📄 Markdown渲染内容:', cleanContent.length > 50 ? cleanContent.substring(0, 50) + '...' : cleanContent)
    
    // 对于流式内容，即使不完整也尝试渲染
    const rendered = marked.parse(cleanContent)
    return rendered
  } catch (error) {
    console.warn('❌ Markdown渲染错误:', error)
    // 如果渲染失败，返回原始内容（防止渲染错误导致显示异常）
    return content ? content.replace(/\n/g, '<br>') : ''
  }
}

// 重新应用代码高亮 - 优化为只处理最新的或未高亮的代码块
const reapplyCodeHighlight = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      // 查找最后一个AI消息中的代码块
      const lastAiMessageElement = messagesContainer.value.querySelector('.message-item.ai:last-child')
      if (lastAiMessageElement) {
        const codeBlocks = lastAiMessageElement.querySelectorAll('pre code')
        codeBlocks.forEach((block) => {
          // 检查是否已经高亮，如果没有则应用高亮
          if (!block.classList.contains('hljs')) {
            hljs.highlightElement(block as HTMLElement)
          }
        })
      }
    }
  })
}

/**
 * 检查滚动容器是否在底部
 */
const isAtBottom = () => {
  if (!messagesContainer.value) return false
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value
  // 允许5px的误差，防止因为小数精度导致的判断不准确
  return scrollHeight - scrollTop - clientHeight < 5
}

/**
 * 检查是否有明显的向上滚动（用于控制按钮显示）
 */
const hasSignificantScroll = () => {
  if (!messagesContainer.value) return false
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight
  // 只有向上滚动超过150px时才显示按钮，避免小幅滚动或初始加载时显示
  return distanceFromBottom > 150
}

/**
 * 处理滚动事件
 */
const handleScroll = () => {
  if (!messagesContainer.value) return
  
  const atBottom = isAtBottom()
  const significantScroll = hasSignificantScroll()
  
  // 只有明显向上滚动时才显示按钮
  showScrollToBottom.value = significantScroll
  
  // 更新用户滚动状态（用于控制自动滚动）
  isUserScrolling.value = !atBottom
}

/**
 * 滚动到底部
 */
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      // 使用smooth滚动，提供更好的用户体验
      messagesContainer.value.scrollTo({
        top: messagesContainer.value.scrollHeight,
        behavior: 'smooth'
      })
      // 滚动后重置用户滚动状态
      isUserScrolling.value = false
      showScrollToBottom.value = false
    }
  })
}

/**
 * 强制滚动到底部（不使用动画）
 * 只在用户没有手动向上滚动时才自动滚动
 */
const forceScrollToBottom = () => {
  // 如果用户正在查看历史消息，不自动滚动
  if (isUserScrolling.value) return
  
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const generateMessageId = () => {
  return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

// 发送消息 - 支持流式Markdown实时渲染
const sendMessage = async () => {
  if (!inputMessage.value.trim() || isLoading.value) return
  
  const userMessage: ChatMessage = {
    id: generateMessageId(),
    content: inputMessage.value.trim(),
    role: 'user',
    timestamp: new Date().toISOString()
  }
  
  // 添加用户消息
  messages.value.push(userMessage)
  
  // 创建AI回复消息（初始为空，用于流式更新）
  const aiMessage: ChatMessage = {
    id: generateMessageId(),
    content: '',
    role: 'ai',
    timestamp: new Date().toISOString(),
    isStreaming: true
  }
  messages.value.push(aiMessage)
  
  // 获取AI消息在数组中的索引，用于响应式更新
  const aiMessageIndex = messages.value.length - 1
  
  // 清空输入框并滚动到底部
  const prompt = inputMessage.value.trim()
  inputMessage.value = ''
  isLoading.value = true
  // 用户发送新消息时，重置滚动状态，确保可以看到自己的消息
  isUserScrolling.value = false
  scrollToBottom()
  
  // 如果没有会话ID，生成一个新的
  if (!sessionId.value) {
    sessionId.value = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
    isNewSession.value = true
    console.log('🆕 生成新会话ID:', sessionId.value)
  }
  
  // 如果是新会话，立即刷新会话列表，确保用户切换页面时不会丢失对话
  if (isNewSession.value && sessionId.value) {
    console.log('🔄 新会话开始，立即刷新会话列表以防止数据丢失')
    chatHistoryStore.fetchSessions().catch(err => {
      console.error('刷新会话列表失败:', err)
    })
    // 标记为非新会话，避免后续重复刷新
    isNewSession.value = false
  }
  
  try {
    const request: UnifiedChatRequest = {
      message: prompt,
      modelType: chatMode.value,
      conversationId: sessionId.value || undefined
    }
    
    // 使用流式聊天 - 实现"边接收边渲染"
    let scrollTimer: number | null = null
    let highlightTimer: number | null = null
    let hasReceivedData = false
    let startTime = Date.now()
    
    console.log('🚀 开始流式聊天处理...')
    
    // 设置超时检测 - 如果10秒内没有收到任何数据，显示错误信息
    const timeoutTimer = setTimeout(() => {
      if (!hasReceivedData) {
        console.warn('⏰ 流式聊天超时，没有收到任何数据')
        aiMessage.content = '⚠️ 服务器响应超时，请检查网络连接或稍后重试。'
        aiMessage.isStreaming = false
        ElMessage.error('服务器响应超时')
      }
    }, 10000)
    
    await createChatStreamPost(
      request,
      // onMessage: 核心流式处理循环 - 读取 -> 解码 -> 累积 -> 渲染 -> 更新
      (data: string) => {
        if (!hasReceivedData) {
          hasReceivedData = true
          console.log('✅ 首次接收到数据，耗时:', Date.now() - startTime, 'ms')
          clearTimeout(timeoutTimer)
        }
        
        // 1. 累积内容：通过数组索引更新确保Vue响应式系统正确触发
        const currentMessage = messages.value[aiMessageIndex]
        const previousLength = currentMessage.content.length
        const newContent = currentMessage.content + data
        
        console.log(`📝 内容更新: ${previousLength} -> ${newContent.length} (+${data.length})`)
        console.log('🔄 更新前内容预览:', currentMessage.content.substring(0, 50) + '...')
        console.log('📦 新增数据:', JSON.stringify(data))
        console.log('🔍 数据类型检查:', typeof data, '是否为字符串:', typeof data === 'string')
        
        // 数据验证：确保接收到的是纯文本而不是JSON
        if (typeof data === 'string' && (data.includes('{"type"') || data.includes('"type":'))) {
          console.error('❌ 检测到JSON格式数据，这不应该发生:', data)
          console.error('💡 这通常表示数据解析逻辑有问题，跳过此次更新')
          return // 跳过这次更新
        }
        
        // 额外验证：确保数据不是空或undefined
        if (data === undefined || data === null || data === '') {
          console.warn('⚠️ 接收到空数据，跳过更新')
          return
        }
        
        // 使用响应式更新方式：替换整个消息对象
        messages.value[aiMessageIndex] = {
          ...currentMessage,
          content: newContent
        }
        
        console.log('✅ 响应式更新完成，新长度:', messages.value[aiMessageIndex].content.length)
        console.log('📋 更新后内容预览:', newContent.length > 50 ? newContent.substring(0, 50) + '...' : newContent)
        
        // 2. 强制Vue重新渲染（备用方案）
        nextTick(() => {
          console.log('🔄 nextTick强制渲染完成')
        })
        
        // 3. 代码高亮：使用防抖优化，避免过于频繁的高亮操作
        if (highlightTimer) {
          clearTimeout(highlightTimer)
        }
        highlightTimer = window.setTimeout(() => {
          reapplyCodeHighlight()
        }, 100) as unknown as number
        
        // 4. 自动滚动：保持用户始终能看到最新内容
        if (scrollTimer) {
          clearTimeout(scrollTimer)
        }
        scrollTimer = window.setTimeout(() => {
          forceScrollToBottom()
        }, 50) as unknown as number
      },
      // onError: 处理错误
      (error: Error) => {
        console.error('💥 Chat stream error:', error)
        clearTimeout(timeoutTimer)
        
        const currentMessage = messages.value[aiMessageIndex]
        let errorContent: string
        
        // 如果没有收到任何数据，显示连接失败的信息
        if (!hasReceivedData) {
          errorContent = '❌ 无法连接到AI服务，请检查网络连接或稍后重试。\n\n可能的原因：\n- 网络连接不稳定\n- 服务器暂时不可用\n- 认证token已过期'
        } else {
          // 如果已经收到了部分数据，在末尾添加错误信息
          errorContent = currentMessage.content + '\n\n⚠️ 连接中断，响应可能不完整。'
        }
        
        // 使用响应式更新
        messages.value[aiMessageIndex] = {
          ...currentMessage,
          content: errorContent,
          isStreaming: false
        }
        
        console.log('❌ 错误处理完成，更新消息内容')
        
        // 清理定时器
        if (scrollTimer) clearTimeout(scrollTimer)
        if (highlightTimer) clearTimeout(highlightTimer)
        ElMessage.error('AI服务连接失败: ' + error.message)
      },
      // onComplete: 流式传输完成
      () => {
        const currentMessage = messages.value[aiMessageIndex]
        console.log('🎉 流式传输完成，总耗时:', Date.now() - startTime, 'ms, 最终长度:', currentMessage.content.length)
        clearTimeout(timeoutTimer)
        
        // 如果没有收到任何内容，显示提示信息
        let finalContent = currentMessage.content
        if (!currentMessage.content.trim()) {
          finalContent = '❓ AI服务没有返回任何内容，请尝试重新提问或稍后重试。'
        }
        
        // 使用响应式更新完成流式状态
        messages.value[aiMessageIndex] = {
          ...currentMessage,
          content: finalContent,
          isStreaming: false
        }
        
        console.log('✅ 流式完成处理，最终消息:', messages.value[aiMessageIndex])
        
        // 清理定时器
        if (scrollTimer) clearTimeout(scrollTimer)
        if (highlightTimer) clearTimeout(highlightTimer)
        // 最终渲染和滚动
        reapplyCodeHighlight()
        forceScrollToBottom()
      }
    )
  } catch (error) {
    console.error('Send message error:', error)
    
    // 如果aiMessageIndex存在，更新错误消息
    if (messages.value[aiMessageIndex]) {
      messages.value[aiMessageIndex] = {
        ...messages.value[aiMessageIndex],
        content: '抱歉，发送消息失败，请检查网络连接后重试。',
        isStreaming: false
      }
    }
    
    ElMessage.error('发送消息失败')
  } finally {
    isLoading.value = false
  }
}

/**
 * 处理新建聊天事件
 */
const handleNewChat = () => {
  sessionId.value = null
  isNewSession.value = true
  messages.value = []
  chatHistoryStore.setActiveSessionId(null)
  
  // 重置滚动状态
  showScrollToBottom.value = false
  isUserScrolling.value = false
  
  // 添加欢迎消息
  const welcomeMessage: ChatMessage = {
    id: generateMessageId(),
    content: `👋 您好！我是您的AI法律助手。\n请直接输入您的法律问题。`,
    role: 'ai',
    timestamp: new Date().toISOString()
  }
  
  messages.value.push(welcomeMessage)
  scrollToBottom()
  ElMessage.success('已创建新会话')
}

/**
 * 处理选择会话事件
 * @param selectedSessionId 选中的会话ID
 */
const handleSelectSession = async (selectedSessionId: string) => {
  try {
    // ✅ 修复：检查是否有正在进行的流式传输
    if (isLoading.value) {
      const hasStreamingMessage = messages.value.some(msg => msg.isStreaming)
      if (hasStreamingMessage) {
        ElMessage.warning({
          message: 'AI正在回答中，请等待完成后再切换会话',
          duration: 2000
        })
        return
      }
    }
    
    // 重置滚动状态
    showScrollToBottom.value = false
    isUserScrolling.value = false
    
    // 加载会话消息
    await chatHistoryStore.fetchMessages(selectedSessionId)
    
    // 转换消息格式
    messages.value = chatHistoryStore.messages.map((msg: ChatMessageDto) => ({
      id: `msg_${msg.id}`,
      content: msg.content,
      role: msg.role === 'assistant' ? 'ai' : msg.role,
      timestamp: msg.createdAt
    }))
    
    // 设置当前会话ID
    sessionId.value = selectedSessionId
    isNewSession.value = false
    
    scrollToBottom()
    // ✅ 优化：缩短提示显示时间到1秒
    ElMessage.success({
      message: '已加载历史会话',
      duration: 800
    })
  } catch (error) {
    console.error('加载会话失败:', error)
    ElMessage.error('加载会话失败')
  }
}

// 开始新会话
const startNewSession = () => {
  sessionId.value = null
  isNewSession.value = true
  messages.value = []
  chatHistoryStore.setActiveSessionId(null)
  
  // 重置滚动状态
  showScrollToBottom.value = false
  isUserScrolling.value = false
  
  // 添加欢迎消息
  const welcomeMessage: ChatMessage = {
    id: generateMessageId(),
    content: `👋 您好！我是您的AI法律助手。
请直接输入您的法律问题。`,
    role: 'ai',
    timestamp: new Date().toISOString()
  }
  
  messages.value.push(welcomeMessage)
  scrollToBottom()
  ElMessage.success('已开始新会话')
}

// 清空对话
const clearChat = async () => {
  try {
    await ElMessageBox.confirm('确定要清空所有对话记录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 调用后端API重置会话
    try {
      await resetChatSessionApi(sessionId.value)
    } catch (error) {
      console.error('Failed to reset chat session:', error)
      // 即使API调用失败，也继续清空本地消息
    }
    
    messages.value = []
    sessionId.value = `session_${Date.now()}`
    ElMessage.success('对话已清空')
  } catch {
    // 用户取消操作
  }
}

/**
 * 切换侧边栏展开/收起状态
 */
const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

// 组件挂载时的初始化
onMounted(() => {
  // 注意：不在这里加载会话列表，由ChatHistoryPanel子组件负责加载
  // 避免重复调用导致速率限制
  
  // 初始化滚动状态
  showScrollToBottom.value = false
  isUserScrolling.value = false
  
  // 添加欢迎消息
  const welcomeMessage: ChatMessage = {
    id: generateMessageId(),
    content: `👋 您好！我是您的AI法律助手。
请直接输入您的法律问题。`,
    role: 'ai',
    timestamp: new Date().toISOString()
  }
  
  messages.value.push(welcomeMessage)
  scrollToBottom()
})

// 组件卸载时的清理
onUnmounted(() => {
  // 清理可能存在的SSE连接
})
</script>

<style scoped>
.chat-container {
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: row;
  gap: 0;
}

/* 聊天历史侧边栏 */
.history-sidebar {
  width: 280px;
  flex-shrink: 0;
  height: 100%;
  transition: width 0.3s ease;
  overflow: hidden;
  
  &.collapsed {
    width: 0;
  }
}

/* 侧边栏切换按钮 */
.sidebar-toggle {
  width: 32px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-left: none;
  border-radius: 0 8px 8px 0;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  z-index: 10;
  position: relative;
  flex-shrink: 0;
  
  &:hover {
    background: #f5f7fa;
    border-color: #409eff;
    
    .el-icon {
      color: #409eff;
    }
  }
  
  .el-icon {
    font-size: 18px;
    color: #606266;
    transition: color 0.3s ease;
  }
}

/* 主聊天区域 */
.main-chat-area {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-title {
  margin: 0;
  font-size: 18px;
  color: #2c3e50;
}

/* 聊天内容区域包装器 */
.chat-content-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.messages-container {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px;
  background-color: #fafafa;
  scroll-behavior: smooth;
}

/* 滚动到底部按钮 - 相对于包装器定位，始终显示在可视区域底部 */
.scroll-to-bottom {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 40px;
  background: #409EFF;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.4);
  transition: all 0.3s ease;
  z-index: 100;
  pointer-events: all;
  
  &:hover {
    background: #66b1ff;
    box-shadow: 0 4px 16px rgba(64, 158, 255, 0.6);
    transform: translateX(-50%) scale(1.1);
  }
  
  &:active {
    transform: translateX(-50%) scale(0.95);
  }
  
  .el-icon {
    color: white;
  }
}

/* 按钮淡入淡出动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-item {
  margin-bottom: 16px;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.user-message .message-content {
  background-color: #f8faff;
  color: #2c3e50;
  border: 1px solid #d4e3ff;
  border-radius: 16px 16px 4px 16px;
}

.ai-message .message-content {
  background-color: white;
  border: 1px solid #e4e7ed;
  border-radius: 16px 16px 16px 4px;
  color: #2c3e50;
}

.message-content {
  max-width: 85%;
  min-width: 100px;
  padding: 14px 18px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.3s ease;
}

.message-content:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
  font-size: 14px;
}

.message-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 6px;
  text-align: right;
}

.ai-message .message-time {
  text-align: left;
}

/* Markdown样式 - 优化流式渲染显示 */
.markdown-content {
  line-height: 1.6;
  word-wrap: break-word;
  word-break: break-word;
}

.markdown-content:empty {
  display: none;
}

/* 确保最后一个元素与光标正确对齐 */
.markdown-content :deep(> *:last-child) {
  margin-bottom: 0 !important;
}

/* 允许段落和光标在同一行显示 */
.markdown-content :deep(p:last-child) {
  display: inline;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 16px 0 8px 0;
  font-weight: 600;
}

.markdown-content :deep(p) {
  margin: 8px 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-content :deep(li) {
  margin: 4px 0;
}

.markdown-content :deep(code) {
  background-color: #f1f2f6;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background-color: #f8f8f8;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 16px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding-left: 16px;
  margin: 12px 0;
  color: #6a737d;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 8px 12px;
  text-align: left;
}

.markdown-content :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

/* 打字光标效果 - 模拟真实的文本编辑器光标 */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background-color: #409EFF;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 49% {
    opacity: 1;
  }
  50%, 100% {
    opacity: 0;
  }
}

.input-area {
  padding: 20px;
  background-color: white;
  border-top: 1px solid #e4e7ed;
}

.input-wrapper {
  position: relative;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-tip {
  color: #909399;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-container {
    height: calc(100vh - 120px);
  }
  
  .history-sidebar {
    width: 240px;
    position: absolute;
    z-index: 100;
    background: white;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
    
    &.collapsed {
      transform: translateX(-100%);
    }
  }
  
  .sidebar-toggle {
    position: absolute;
    left: 0;
    top: 20px;
    z-index: 101;
  }
  
  .messages-container {
    padding: 15px;
  }
  
  .message-content {
    max-width: 85%;
  }
  
  .input-area {
    padding: 15px;
  }
  
  .mode-selector {
    margin-bottom: 12px;
  }
  
  .mode-selector :deep(.el-radio-button__inner) {
    padding: 8px 12px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .chat-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  
  .header-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .message-content {
    max-width: 90%;
  }
  
  .input-actions {
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
  }
}
</style>