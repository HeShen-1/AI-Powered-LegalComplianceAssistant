<template>
  <div class="chat-container">
    <!-- 聊天模式选择器 -->
    <div class="chat-header">
      <el-card shadow="never" class="mode-selector">
        <div class="mode-options">
          <el-radio-group v-model="chatMode" size="large" @change="handleModeChange">
            <el-radio-button label="basic">💬 基础聊天</el-radio-button>
            <el-radio-button label="rag">📚 知识问答</el-radio-button>
            <el-radio-button label="agent">🤖 智能顾问</el-radio-button>
          </el-radio-group>
          
          <div class="mode-description">
            <span v-if="chatMode === 'basic'">与AI进行基础对话交流</span>
            <span v-else-if="chatMode === 'rag'">基于法律知识库的专业问答</span>
            <span v-else-if="chatMode === 'agent'">具备工具调用能力的智能法律顾问</span>
          </div>
        </div>
        
        <div class="chat-actions">
          <el-button @click="clearMessages" :disabled="messages.length === 0">
            <el-icon><Delete /></el-icon>
            清空对话
          </el-button>
          
          <el-button @click="exportChat" :disabled="messages.length === 0">
            <el-icon><Download /></el-icon>
            导出对话
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 消息列表区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <el-empty description="暂无对话记录">
          <template #image>
            <el-icon size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
          </template>
          <div class="quick-questions">
            <p>试试问我：</p>
            <div class="question-chips">
              <el-button
                v-for="question in quickQuestions"
                :key="question"
                text
                type="primary"
                @click="sendQuickQuestion(question)"
              >
                {{ question }}
              </el-button>
            </div>
          </div>
        </el-empty>
      </div>

      <div
        v-for="message in messages"
        :key="message.id"
        :class="['chat-message', message.type]"
      >
        <div :class="['message-bubble', message.type]">
          <!-- 用户消息 -->
          <div v-if="message.type === 'user'" class="user-message">
            {{ message.content }}
          </div>
          
          <!-- AI消息 -->
          <div v-else class="ai-message">
            <div v-if="message.isStreaming" class="streaming-indicator">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
              AI正在思考中...
            </div>
            
            <div
              v-if="message.content"
              class="markdown-content"
              v-html="renderMarkdown(message.content)"
            ></div>
          </div>
          
          <!-- 消息时间 -->
          <div class="message-time">
            {{ formatTime(message.timestamp) }}
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input">
      <el-card shadow="never" class="input-card">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="请输入您的问题..."
            :disabled="isLoading"
            @keydown="handleKeyDown"
          />
          
          <div class="input-actions">
            <div class="input-tips">
              <span v-if="chatMode === 'rag'">
                <el-icon><InfoFilled /></el-icon>
                支持检索{{ maxResults }}条相关法律条文
              </span>
              <span v-else-if="chatMode === 'agent'">
                <el-icon><InfoFilled /></el-icon>
                可调用外部工具进行深度分析
              </span>
            </div>
            
            <div class="input-buttons">
              <el-button
                v-if="chatMode === 'rag'"
                text
                @click="showRagSettings = true"
              >
                <el-icon><Setting /></el-icon>
                设置
              </el-button>
              
              <el-button
                type="primary"
                :loading="isLoading"
                :disabled="!inputMessage.trim()"
                @click="sendMessage"
              >
                <el-icon><Promotion /></el-icon>
                {{ isLoading ? '发送中' : '发送' }}
              </el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- RAG设置对话框 -->
    <el-dialog
      v-model="showRagSettings"
      title="知识问答设置"
      width="400px"
    >
      <el-form :model="ragSettings" label-width="120px">
        <el-form-item label="检索条数:">
          <el-slider
            v-model="ragSettings.maxResults"
            :min="1"
            :max="10"
            show-stops
            show-input
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showRagSettings = false">取消</el-button>
        <el-button type="primary" @click="saveRagSettings">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import type { ChatMessage } from '@/types/api'
import {
  basicChatApi,
  ragChatApi,
  agentConsultApi
} from '@/api/aiService'
import {
  ChatDotRound,
  Delete,
  Download,
  InfoFilled,
  Setting,
  Promotion
} from '@element-plus/icons-vue'

// 聊天模式
type ChatMode = 'basic' | 'rag' | 'agent'

// 组件状态
const chatMode = ref<ChatMode>('basic')
const inputMessage = ref('')
const isLoading = ref(false)
const messages = ref<ChatMessage[]>([])
const messagesContainer = ref<HTMLElement>()
const showRagSettings = ref(false)
const maxResults = ref(5)

// RAG设置
const ragSettings = reactive({
  maxResults: 5
})

// 快速问题
const quickQuestions = [
  '合同违约责任包括哪些？',
  '如何处理合同纠纷？',
  '劳动合同应该注意什么？',
  '知识产权保护有哪些方式？'
]

// 配置marked
marked.setOptions({
  highlight: function(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  },
  langPrefix: 'hljs language-'
})

// 切换聊天模式
const handleModeChange = (mode: ChatMode) => {
  if (messages.value.length > 0) {
    ElMessageBox.confirm(
      '切换模式将清空当前对话记录，确定要继续吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      clearMessages()
    }).catch(() => {
      chatMode.value = chatMode.value // 恢复之前的模式
    })
  }
}

// 发送快速问题
const sendQuickQuestion = (question: string) => {
  inputMessage.value = question
  sendMessage()
}

// 发送消息
const sendMessage = async () => {
  const content = inputMessage.value.trim()
  if (!content || isLoading.value) return

  // 添加用户消息
  const userMessage: ChatMessage = {
    id: generateMessageId(),
    content,
    type: 'user',
    timestamp: new Date().toISOString()
  }
  messages.value.push(userMessage)

  // 创建AI消息占位符
  const aiMessage: ChatMessage = {
    id: generateMessageId(),
    content: '',
    type: 'ai',
    timestamp: new Date().toISOString(),
    isStreaming: true
  }
  messages.value.push(aiMessage)

  // 清空输入框
  inputMessage.value = ''
  isLoading.value = true

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  try {
    let response

    // 根据模式调用不同的API
    switch (chatMode.value) {
      case 'basic':
        response = await basicChatApi({ message: content })
        break
      case 'rag':
        response = await ragChatApi({ question: content, maxResults: maxResults.value })
        break
      case 'agent':
        response = await agentConsultApi({ message: content })
        break
    }

    // 模拟流式响应效果
    if (response?.data?.answer) {
      await simulateStreamingResponse(aiMessage, response.data.answer)
    } else {
      aiMessage.content = '抱歉，未能获取到有效回复。请稍后重试。'
    }

  } catch (error: any) {
    console.error('Chat error:', error)
    aiMessage.content = '抱歉，处理您的问题时出现了错误。请稍后重试。'
    aiMessage.isStreaming = false
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 模拟流式响应
const simulateStreamingResponse = async (message: ChatMessage, fullContent: string) => {
  if (!fullContent) {
    message.isStreaming = false
    return
  }
  
  message.isStreaming = true
  message.content = ''

  const words = fullContent.split('')
  const delay = Math.max(20, Math.min(50, 1000 / words.length)) // 动态调整延迟

  for (let i = 0; i < words.length; i++) {
    message.content += words[i]
    
    // 每隔几个字符滚动一次
    if (i % 10 === 0) {
      await nextTick()
      scrollToBottom()
    }
    
    await new Promise(resolve => setTimeout(resolve, delay))
  }

  message.isStreaming = false
  await nextTick()
  scrollToBottom()
}

// 处理键盘事件
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

// 清空消息
const clearMessages = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有对话记录吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    messages.value = []
    ElMessage.success('对话记录已清空')
  } catch {
    // 用户取消
  }
}

// 导出对话
const exportChat = () => {
  if (messages.value.length === 0) return

  const chatContent = messages.value.map(msg => {
    const time = formatTime(msg.timestamp)
    const sender = msg.type === 'user' ? '用户' : 'AI助手'
    return `[${time}] ${sender}: ${msg.content}`
  }).join('\n\n')

  const blob = new Blob([chatContent], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `法律助手对话记录_${new Date().toISOString().slice(0, 10)}.txt`
  link.click()
  URL.revokeObjectURL(url)

  ElMessage.success('对话记录导出成功')
}

// 保存RAG设置
const saveRagSettings = () => {
  maxResults.value = ragSettings.maxResults
  showRagSettings.value = false
  ElMessage.success('设置已保存')
}

// 渲染Markdown
const renderMarkdown = (content: string) => {
  return marked(content)
}

// 格式化时间
const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

// 生成消息ID
const generateMessageId = () => {
  return Date.now().toString() + Math.random().toString(36).substr(2, 9)
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 组件挂载时的处理
onMounted(() => {
  // 可以从localStorage恢复聊天记录
  const savedMessages = localStorage.getItem('chat_messages')
  if (savedMessages) {
    try {
      messages.value = JSON.parse(savedMessages)
      nextTick(() => scrollToBottom())
    } catch (error) {
      console.error('Failed to restore chat messages:', error)
    }
  }
})

// 组件卸载时保存聊天记录
onUnmounted(() => {
  if (messages.value.length > 0) {
    localStorage.setItem('chat_messages', JSON.stringify(messages.value))
  }
})
</script>

<style scoped>
.chat-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-header {
  flex-shrink: 0;
}

.mode-selector {
  padding: 16px 20px;
}

.mode-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.mode-description {
  font-size: 14px;
  color: var(--text-secondary);
}

.chat-actions {
  display: flex;
  gap: 12px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 0 20px;
  background: #f8f9fa;
  border-radius: 8px;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-questions {
  margin-top: 20px;
  text-align: center;
}

.quick-questions p {
  margin-bottom: 12px;
  color: var(--text-secondary);
}

.question-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.chat-message {
  margin-bottom: 20px;
  display: flex;
  animation: messageAppear 0.3s ease-out;
}

.chat-message.user {
  justify-content: flex-end;
}

.chat-message.ai {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 18px;
  position: relative;
  word-break: break-word;
}

.message-bubble.user {
  background: var(--primary-color);
  color: white;
  border-bottom-right-radius: 4px;
}

.message-bubble.ai {
  background: white;
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-bottom-left-radius: 4px;
}

.user-message {
  line-height: 1.5;
}

.ai-message {
  line-height: 1.6;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.message-time {
  font-size: 12px;
  color: var(--text-placeholder);
  margin-top: 5px;
  text-align: center;
}

.chat-input {
  flex-shrink: 0;
}

.input-card {
  padding: 16px 20px;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-tips {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--text-secondary);
}

.input-buttons {
  display: flex;
  gap: 12px;
}

@keyframes messageAppear {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .chat-container {
    gap: 12px;
  }
  
  .mode-options {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .message-bubble {
    max-width: 85%;
  }
  
  .input-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>
