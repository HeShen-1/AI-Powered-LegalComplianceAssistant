<template>
  <div class="contract-container">
    <!-- 上传区域 -->
    <el-card v-if="!currentReview" class="upload-card" shadow="never">
      <template #header>
        <div class="card-header">
          <h3>📄 合同审查</h3>
          <p class="card-subtitle">上传合同文件，AI将为您进行全面的法律风险分析</p>
        </div>
      </template>

      <el-upload
        ref="uploadRef"
        class="upload-dragger"
        drag
        :action="uploadAction"
        :headers="uploadHeaders"
        :before-upload="beforeUpload"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :show-file-list="false"
        accept=".pdf,.doc,.docx"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将合同文件拖拽到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、DOC、DOCX 格式，文件大小不超过 10MB
          </div>
        </template>
      </el-upload>

      <!-- 上传进度 -->
      <div v-if="uploadProgress > 0 && uploadProgress < 100" class="upload-progress">
        <el-progress :percentage="uploadProgress" :show-text="true" />
        <p class="progress-text">正在上传文件...</p>
      </div>
    </el-card>

    <!-- 分析进度区域 -->
    <el-card v-if="currentReview && analysisStatus !== 'completed'" class="analysis-card" shadow="never">
      <template #header>
        <div class="card-header">
          <h3>🔍 正在分析合同</h3>
          <p class="card-subtitle">{{ currentReview.originalFilename }}</p>
        </div>
      </template>

      <!-- 分析步骤 -->
      <el-steps :active="currentStep" align-center class="analysis-steps">
        <el-step title="文档解析" description="提取合同文本内容" />
        <el-step title="风险识别" description="识别潜在法律风险" />
        <el-step title="条款分析" description="分析关键条款" />
        <el-step title="生成报告" description="生成详细审查报告" />
      </el-steps>

      <!-- 实时日志 -->
      <div class="analysis-logs">
        <h4>分析日志</h4>
        <div ref="logsContainer" class="logs-container">
          <div
            v-for="(log, index) in analysisLogs"
            :key="index"
            :class="['log-item', log.type]"
          >
            <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </div>

      <!-- 取消按钮 -->
      <div class="analysis-actions">
        <el-button type="danger" @click="cancelAnalysis">取消分析</el-button>
      </div>
    </el-card>
    
    <!-- 分析结果区域 -->
    <div v-if="analysisStatus === 'completed' && analysisResult" class="result-section">
      <!-- 结果概览 -->
      <el-card class="result-overview" shadow="never">
        <template #header>
          <div class="result-header">
            <div class="header-left">
              <h3>📊 审查结果</h3>
              <p class="file-name">{{ currentReview?.originalFilename }}</p>
            </div>
            <div class="header-right">
            <el-tag
                :type="riskLevelType"
              size="large"
              effect="dark"
            >
                {{ riskLevelText }}
            </el-tag>
          </div>
          </div>
        </template>
        
        <el-row :gutter="20">
          <el-col :xs="24" :sm="8">
            <div class="stat-item">
              <div class="stat-number">{{ analysisResult.riskCount || 0 }}</div>
              <div class="stat-label">风险项</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="stat-item">
              <div class="stat-number">{{ analysisResult.clauseCount || 0 }}</div>
              <div class="stat-label">关键条款</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="8">
            <div class="stat-item">
              <div class="stat-number">{{ analysisResult.score || 0 }}</div>
              <div class="stat-label">
                综合评分
                <el-tooltip 
                  v-if="analysisResult.scoringRules" 
                  :content="getScoringRulesTooltip()" 
                  placement="top" 
                  :show-after="300"
                  effect="light"
                  :width="350"
                  raw-content
                >
                  <el-icon class="score-info-icon" :size="16">
                    <InfoFilled />
                  </el-icon>
                </el-tooltip>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>
      
      <!-- 详细结果 -->
      <el-row :gutter="20">
        <!-- 风险项列表 -->
        <el-col :xs="24" :lg="12">
          <el-card class="risk-card" shadow="never">
            <template #header>
              <h4>⚠️ 风险项分析</h4>
            </template>
            
            <div v-if="analysisResult.risks && analysisResult.risks.length > 0">
              <div
                v-for="(risk, index) in analysisResult.risks"
              :key="index"
                class="risk-item"
            >
                <div class="risk-header">
                <el-tag
                    :type="getRiskTagType(risk.level)"
                  size="small"
                >
                    {{ risk.level }}
                </el-tag>
                  <span class="risk-title">{{ risk.title }}</span>
                  <el-tag v-if="risk.source" size="small" type="info" effect="plain" style="margin-left: 8px;">
                    📍 {{ risk.source }}
                  </el-tag>
                </div>
                <p class="risk-description">{{ risk.description }}</p>
                <div v-if="risk.suggestion" class="risk-suggestion">
                  <strong>💡 建议：</strong>{{ risk.suggestion }}
                </div>
                <div v-if="risk.legalBasis" class="risk-legal-basis">
                  <strong>⚖️ 法律依据：</strong>{{ risk.legalBasis }}
                </div>
              </div>
            </div>
            <el-empty v-else description="未发现明显风险" />
          </el-card>
        </el-col>
        
        <!-- 关键条款 -->
        <el-col :xs="24" :lg="12">
          <el-card class="clause-card" shadow="never">
            <template #header>
              <h4>📋 关键条款</h4>
            </template>
            
            <div v-if="analysisResult.clauses && analysisResult.clauses.length > 0">
              <el-collapse v-model="activeClause">
                <el-collapse-item
                  v-for="(clause, index) in analysisResult.clauses"
                  :key="index"
                  :title="clause.title"
                  :name="index"
                >
              <div class="clause-content">
                    <p><strong>内容：</strong>{{ clause.content }}</p>
                    <p v-if="clause.analysis"><strong>分析：</strong>{{ clause.analysis }}</p>
                  </div>
                </el-collapse-item>
              </el-collapse>
              </div>
            <el-empty v-else description="未识别到关键条款" />
            </el-card>
        </el-col>
      </el-row>
      
      <!-- 操作按钮 -->
      <div class="result-actions">
        <el-button type="primary" :icon="Download" @click="downloadReport">
          下载报告
        </el-button>
        <el-button type="success" @click="startNewAnalysis">
          分析新合同
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  UploadFilled,
  Download,
  InfoFilled
} from '@element-plus/icons-vue'
import type { TagProps, UploadInstance, UploadProps, UploadRawFile } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { createAnalysisSSEAuth, type ContractAnalysisStream } from '@/api/contractService'

// 类型定义
interface AnalysisLog {
  timestamp: string
  type: 'info' | 'success' | 'warning' | 'error'
  message: string
}

interface RiskItem {
  level: 'HIGH' | 'MEDIUM' | 'LOW'
  title: string
  description: string
  suggestion?: string
  legalBasis?: string
  source?: string
  clauseText?: string
}

interface ClauseItem {
  title: string
  content: string
  analysis?: string
  importance?: string
  section?: string
}

interface AnalysisResult {
  id?: number
  riskCount: number
  clauseCount: number
  score: number
  riskLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  risks: RiskItem[]
  clauses: ClauseItem[]
  summary?: any
  detailedAnalysis?: any
  riskClauses?: any[]
  scoringRules?: {
    method: string
    rules: string[]
    description: string
  }
  originalFilename?: string
}

interface ContractReview {
  id: number
  originalFilename: string
  reviewStatus: string
  riskLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
}

const userStore = useUserStore()

// 响应式数据
const uploadRef = ref<UploadInstance>()
const logsContainer = ref<HTMLElement>()
const uploadProgress = ref(0)
const currentReview = ref<ContractReview | null>(null)
const analysisStatus = ref<'pending' | 'processing' | 'completed' | 'failed' | 'timeout'>('pending')
const currentStep = ref(0)
const analysisLogs = ref<AnalysisLog[]>([])
const analysisResult = ref<AnalysisResult | null>(null)
const activeClause = ref<number[]>([])
const isNormalClose = ref(false) // 标记是否为正常关闭SSE连接
let eventSource: ContractAnalysisStream | null = null

// 计算属性
const uploadAction = computed(() => '/api/v1/contracts/upload')

const uploadHeaders = computed(() => {
  const headers: Record<string, string> = {}
  if (userStore.token) {
    headers['Authorization'] = `Bearer ${userStore.token}`
  }
  return headers
})

const riskLevelType = computed<TagProps['type']>(() => {
  if (!analysisResult.value) return 'info'
  const typeMap = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  } as const
  return typeMap[analysisResult.value.riskLevel]
})

const riskLevelText = computed(() => {
  if (!analysisResult.value) return '未知'
  const textMap = {
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险'
  }
  return textMap[analysisResult.value.riskLevel]
})

// 工具函数
const formatLogTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN')
}

const getRiskTagType = (level: string): TagProps['type'] => {
  const typeMap = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  } as const
  return typeMap[level as keyof typeof typeMap] || 'info'
}

// 获取评分细则的tooltip内容
const getScoringRulesTooltip = () => {
  if (!analysisResult.value?.scoringRules) {
    return '暂无评分细则说明'
  }
  
  const rules = analysisResult.value.scoringRules
  let content = `<div style="max-width: 350px;">
    <div style="font-weight: bold; margin-bottom: 8px; color: #409EFF;">${rules.method || '评分方法'}</div>`
  
  if (rules.rules && rules.rules.length > 0) {
    content += '<div style="margin-bottom: 8px;">'
    rules.rules.forEach((rule) => {
      content += `<div style="margin-bottom: 4px;">• ${rule}</div>`
    })
    content += '</div>'
  }
  
  if (rules.description) {
    content += `<div style="padding: 8px; background: #f5f7fa; border-radius: 4px; font-size: 12px; color: #666;">
      ${rules.description}
    </div>`
  }
  
  content += '</div>'
  return content
}

const scrollLogsToBottom = () => {
  nextTick(() => {
    if (logsContainer.value) {
      logsContainer.value.scrollTop = logsContainer.value.scrollHeight
    }
  })
}

const addLog = (type: AnalysisLog['type'], message: string) => {
  analysisLogs.value.push({
    timestamp: new Date().toISOString(),
    type,
    message
  })
  scrollLogsToBottom()
}

// 上传相关函数
const beforeUpload: UploadProps['beforeUpload'] = (rawFile: UploadRawFile) => {
  const isValidType = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'].includes(rawFile.type)
  const isLt10M = rawFile.size / 1024 / 1024 < 10

  if (!isValidType) {
    ElMessage.error('只支持 PDF、DOC、DOCX 格式的文件!')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB!')
    return false
  }

  uploadProgress.value = 0
  return true
}

const handleUploadSuccess = (response: any) => {
  uploadProgress.value = 100
  
  if (response.success && response.data) {
    currentReview.value = {
      id: response.data.reviewId,
      originalFilename: response.data.originalFilename || '未知文件',
      reviewStatus: response.data.status
    }
    
    ElMessage.success('文件上传成功，开始分析...')
    startAnalysis(response.data.reviewId)
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleUploadError = (error: any) => {
  console.error('Upload error:', error)
  uploadProgress.value = 0
  ElMessage.error('文件上传失败，请重试')
}

// 分析相关函数
const startAnalysis = (reviewId: number) => {
  // 确保reviewId是有效的数字
  if (!reviewId || isNaN(reviewId)) {
    console.error('Invalid reviewId:', reviewId)
    ElMessage.error('无效的审查ID')
    return
  }
  
  // 防止重复分析 - 如果当前正在分析，则不允许开始新的分析
  if (analysisStatus.value === 'processing') {
    console.warn('分析已在进行中，忽略重复请求')
    ElMessage.warning('合同正在分析中，请勿重复操作')
    return
  }
  
  analysisStatus.value = 'processing'
  currentStep.value = 0
  analysisLogs.value = []
  isNormalClose.value = false // 重置标志
  
  // 关闭之前的连接（如果存在）
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  
  addLog('info', '开始分析合同文件...')
  
  // 创建SSE连接监听分析进度，通过查询参数传递token
  const token = userStore.token
  if (!token) {
    ElMessage.error('用户未登录，请重新登录')
    return
  }
  
  // 在开发环境下需要使用完整的URL指向后端服务器
  addLog('info', '使用 Authorization Header 启动流式合同审查')
  console.log('Creating auth SSE stream for reviewId:', reviewId)
  eventSource = createAnalysisSSEAuth(reviewId, token)
  
  // 监听默认消息事件
  eventSource.onmessage = (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        handleAnalysisEvent(data)
      } else {
        console.warn('收到空数据的SSE消息')
      }
    } catch (error) {
      console.error('Failed to parse SSE data:', error)
      console.log('原始数据:', event.data)
      addLog('error', '解析分析数据失败')
    }
  }
  
  // 监听Named Events (后端发送的特定事件名称)
  eventSource.addEventListener('connected', (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        handleAnalysisEvent({ type: 'connected', ...data })
      } else {
        // 如果没有数据，使用默认消息
        handleAnalysisEvent({ type: 'connected', message: 'SSE连接已建立' })
      }
    } catch (error) {
      console.error('Failed to parse connected event:', error)
    }
  })
  
  eventSource.addEventListener('info', (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        handleAnalysisEvent({ type: 'info', ...data })
      }
    } catch (error) {
      console.error('Failed to parse info event:', error)
    }
  })
  
  eventSource.addEventListener('progress', (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        handleAnalysisEvent({ type: 'progress', ...data })
      }
    } catch (error) {
      console.error('Failed to parse progress event:', error)
    }
  })
  
  eventSource.addEventListener('result', (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        console.log('Received result event:', data)
        handleAnalysisEvent({ type: 'result', ...data })
      }
    } catch (error) {
      console.error('Failed to parse result event:', error)
    }
  })
  
  eventSource.addEventListener('complete', (event) => {
    try {
      if (event.data && event.data !== 'undefined') {
        const data = JSON.parse(event.data)
        console.log('Received complete event:', data)
        handleAnalysisEvent({ type: 'complete', ...data })
      } else {
        // 如果没有数据，使用默认完成消息
        handleAnalysisEvent({ type: 'complete', message: '分析已完成' })
      }
    } catch (error) {
      console.error('Failed to parse complete event:', error)
    }
  })
  
  // 监听服务器主动发送的错误事件（如果有的话）
  eventSource.addEventListener('error', (event) => {
    const messageEvent = event as MessageEvent<string>
    try {
      // 检查是否有数据，如果没有数据则跳过JSON解析
      if (messageEvent.data && messageEvent.data !== 'undefined') {
        const data = JSON.parse(messageEvent.data)
        handleAnalysisEvent({ type: 'error', ...data })
      } else {
        console.log('收到error事件但无数据，可能是连接级错误')
        // 这种情况由onerror处理器处理
      }
    } catch (error) {
      console.error('Failed to parse error event:', error)
      console.log('原始事件数据:', messageEvent.data)
    }
  })
  
  eventSource.addEventListener('timeout', (event) => {
    const messageEvent = event as MessageEvent<string>
    try {
      if (messageEvent.data && messageEvent.data !== 'undefined') {
        const data = JSON.parse(messageEvent.data)
        handleAnalysisEvent({ type: 'timeout', ...data })
      } else {
        // 处理超时事件无数据的情况
        addLog('warning', '分析超时，连接已断开')
        analysisStatus.value = 'timeout'
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
      }
    } catch (error) {
      console.error('Failed to parse timeout event:', error)
      addLog('warning', '连接超时')
    }
  })
  
  eventSource.onopen = () => {
    console.log('SSE connection opened successfully')
    // 不在这里添加日志，等待服务器发送connected事件
  }
  
  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error)
    console.error('EventSource readyState:', eventSource?.readyState)
    console.error('EventSource URL:', eventSource?.url)
    
    // 添加更详细的错误诊断
    const readyStateText = eventSource?.readyState === 0 ? 'CONNECTING' : 
                          eventSource?.readyState === 1 ? 'OPEN' : 
                          eventSource?.readyState === 2 ? 'CLOSED' : 'UNKNOWN'
    console.error('EventSource状态:', readyStateText)
    
    // 如果是正常关闭（收到complete事件后的关闭），不显示错误
    if (isNormalClose.value) {
      console.log('分析已完成，SSE连接正常关闭')
      return
    }
    
    // 如果已经接收到分析结果，说明是正常完成后的关闭，不显示错误
    if (analysisStatus.value === 'completed' && analysisResult.value) {
      console.log('分析已完成，SSE连接正常关闭')
      return
    }
    
    addLog('warning', `SSE连接状态: ${readyStateText}`)
    
    // 检查连接状态
    if (eventSource?.readyState === EventSource.CLOSED) {
      // 连接已关闭，可能是超时、网络错误或服务器主动关闭
      addLog('warning', '连接已断开，分析可能仍在后台进行')
      addLog('info', '您可以稍后刷新页面查看分析结果')
      
        // 设置定时器，定期检查分析结果
        const checkInterval = setInterval(async () => {
          try {
            // 使用fetch直接调用API检查状态
            const token = userStore.token
            const response = await fetch(`/api/v1/contracts/${reviewId}`, {
              headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
              }
            })
            
            if (response.ok) {
              const result = await response.json()
              if (result.success && result.data) {
                const review = result.data
                if (review.reviewStatus === 'COMPLETED') {
                  clearInterval(checkInterval)
                  // 转换后端数据结构为前端期望格式
                  const transformed = {
                    ...review,
                    risks: review.riskClauses?.map((clause: any, index: number) => ({
                      level: clause.riskLevel || 'UNKNOWN',
                      title: clause.riskType || '未知风险',
                      description: clause.riskDescription || clause.clauseText || '',
                      suggestion: clause.suggestion || '',
                      legalBasis: clause.legalBasis || '',
                      clauseText: clause.clauseText || '',
                      source: clause.positionStart && clause.positionEnd 
                        ? `字符位置 ${clause.positionStart}-${clause.positionEnd}` 
                        : `风险项 ${index + 1}`
                    })) || [],
                    riskCount: review.totalRisks || review.riskClauses?.length || 0,
                    clauseCount: review.detailedAnalysis?.keyClauses?.length || 0,
                    score: review.summary?.complianceScore || review.summary?.completenessScore || 0,
                    clauses: review.detailedAnalysis?.keyClauses || 
                             review.detailedAnalysis?.key_clauses || [],
                    // 保留评分细则信息
                    scoringRules: review.summary?.scoringRules
                  }
                  analysisResult.value = transformed
                  analysisStatus.value = 'completed'
                  addLog('success', '分析已完成！')
                } else if (review.reviewStatus === 'FAILED') {  
                  clearInterval(checkInterval)
                  analysisStatus.value = 'failed'
                  addLog('error', '分析失败')
                }
              }
            }
          } catch (error) {
            console.error('检查分析状态失败:', error)
          }
        }, 5000) // 每5秒检查一次
      
      // 5分钟后停止检查
      setTimeout(() => {
        clearInterval(checkInterval)
      }, 5 * 60 * 1000)
      
    } else if (eventSource?.readyState === EventSource.CONNECTING) {
      addLog('info', '正在重新连接...')
    } else {
      addLog('warning', '连接出现问题，系统正在尝试恢复...')
    }
  }
}

const handleAnalysisEvent = (data: any) => {
  console.log('Processing SSE event:', data)
  
  switch (data.type) {
    case 'connected':
      addLog('success', data.message || 'SSE连接已建立')
      break
    case 'info':
      addLog('info', data.message || '系统信息')
      break
    case 'progress':
      currentStep.value = data.step || 0
      addLog('info', data.message)
      // 检查是否是错误阶段
      if (data.stage === 'ERROR') {
        analysisStatus.value = 'failed'
        addLog('error', data.error || data.message || '分析过程中发生错误')
        // 关闭SSE连接
        if (eventSource) {
          eventSource.close()
          eventSource = null
        }
      }
      break
    case 'result':
      console.log('Setting analysis result:', data.result)
      // 转换后端数据结构为前端期望格式
      if (data.result) {
        const transformed = {
          ...data.result,
          // 将 riskClauses 转换为 risks，并添加来源信息
          risks: data.result.riskClauses?.map((clause: any, index: number) => ({
            level: clause.riskLevel || 'UNKNOWN',
            title: clause.riskType || '未知风险',
            description: clause.riskDescription || clause.clauseText || '',
            suggestion: clause.suggestion || '',
            legalBasis: clause.legalBasis || '',
            clauseText: clause.clauseText || '',
            // 添加来源信息
            source: clause.positionStart && clause.positionEnd 
              ? `字符位置 ${clause.positionStart}-${clause.positionEnd}` 
              : `风险项 ${index + 1}`
          })) || [],
          // 映射统计数据
          riskCount: data.result.totalRisks || data.result.riskClauses?.length || 0,
          clauseCount: data.result.detailedAnalysis?.keyClauses?.length || 0,
          score: data.result.summary?.complianceScore || data.result.summary?.completenessScore || 0,
          // 提取关键条款
          clauses: data.result.detailedAnalysis?.keyClauses || 
                   data.result.detailedAnalysis?.key_clauses || [],
          // 保留原始的 riskClauses 以备后用
          riskClauses: data.result.riskClauses || [],
          // 保留评分细则信息
          scoringRules: data.result.summary?.scoringRules
        }
        console.log('Transformed analysis result:', transformed)
        analysisResult.value = transformed
      } else {
        analysisResult.value = data.result
      }
      analysisStatus.value = 'completed'
      addLog('success', '分析完成！')
      isNormalClose.value = true // 标记为正常完成
      // 注意：不要在这里关闭连接，等待complete事件
      break
    case 'complete':
      addLog('success', data.message || '分析完成')
      analysisStatus.value = 'completed'
      isNormalClose.value = true // 标记为正常完成
      eventSource?.close()
      eventSource = null
      break
    case 'timeout':
      addLog('warning', data.message || '连接超时，分析将在后台继续')
      addLog('info', '请稍后刷新页面查看分析结果')
      eventSource?.close()
      eventSource = null
      break
    case 'error':
      analysisStatus.value = 'failed'
      addLog('error', data.error || data.message || '分析过程中发生错误')
      console.error('分析错误:', data)
      eventSource?.close()
      eventSource = null
      break
  }
}

const cancelAnalysis = async () => {
  try {
    await ElMessageBox.confirm('确定要取消当前分析吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    eventSource?.close()
    currentReview.value = null
    analysisStatus.value = 'pending'
    ElMessage.info('已取消分析')
  } catch {
    // 用户取消操作
  }
}

const downloadReport = async () => {
  if (!analysisResult.value || !analysisResult.value.id) {
    ElMessage.warning('无法获取审查记录ID')
    return
  }

  try {
    // 使用fetch下载，可以添加Authorization header
    const response = await fetch(`/api/v1/contracts/${analysisResult.value.id}/report`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${userStore.token}`
      }
    })

    if (!response.ok) {
      // 尝试解析错误消息
      const errorData = await response.json().catch(() => null)
      const errorMessage = errorData?.message || `下载失败 (${response.status})`
      ElMessage.error(errorMessage)
      return
    }

    // 获取文件内容
    const blob = await response.blob()
    
    // 从响应头获取文件名，如果没有则使用默认名称
    const contentDisposition = response.headers.get('Content-Disposition')
    let fileName = '合同审查报告.pdf'
    if (contentDisposition) {
      // 优先解析UTF-8编码的文件名 (filename*=UTF-8''...)
      const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/)
      if (utf8Match && utf8Match[1]) {
        try {
          fileName = decodeURIComponent(utf8Match[1])
        } catch (e) {
          console.warn('UTF-8文件名解码失败:', e)
        }
      } else {
        // 回退到普通文件名解析
        const fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
        if (fileNameMatch && fileNameMatch[1]) {
          fileName = fileNameMatch[1].replace(/['"]/g, '')
          try {
            fileName = decodeURIComponent(fileName)
          } catch (e) {
            // 解码失败，使用原始值
          }
        }
      }
    } else if (analysisResult.value.originalFilename) {
      const baseName = analysisResult.value.originalFilename.replace(/\.[^/.]+$/, '')
      fileName = `${baseName}_审查报告.pdf`
    }

    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    
    // 清理
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('报告下载成功')
  } catch (error) {
    console.error('下载报告失败:', error)
    ElMessage.error('下载报告失败，请稍后重试')
  }
}

const startNewAnalysis = () => {
  currentReview.value = null
  analysisStatus.value = 'pending'
  analysisResult.value = null
  analysisLogs.value = []
  currentStep.value = 0
  uploadProgress.value = 0
}

// 组件卸载时清理
onUnmounted(() => {
  eventSource?.close()
})
</script>

<style scoped>
.contract-container {
  max-width: 1200px;
  margin: 0 auto;
}

.upload-card,
.analysis-card {
  margin-bottom: 20px;
}

.card-header h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #2c3e50;
}

.card-subtitle {
  margin: 0;
  color: #7f8c8d;
  font-size: 14px;
}

.upload-dragger {
  width: 100%;
}

.upload-progress {
  margin-top: 20px;
  text-align: center;
}

.progress-text {
  margin-top: 10px;
  color: #606266;
}

.analysis-steps {
  margin: 30px 0;
}

.analysis-logs {
  margin: 30px 0;
}

.analysis-logs h4 {
  margin: 0 0 15px 0;
  color: #2c3e50;
}

.logs-container {
  max-height: 300px;
  overflow-y: auto;
  background-color: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 15px;
}

.log-item {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.4;
}

.log-time {
  color: #6c757d;
  margin-right: 10px;
  min-width: 80px;
}

.log-message {
  flex: 1;
}

.log-item.info .log-message {
  color: #17a2b8;
}

.log-item.success .log-message {
  color: #28a745;
}

.log-item.warning .log-message {
  color: #ffc107;
}

.log-item.error .log-message {
  color: #dc3545;
}

.analysis-actions {
  text-align: center;
  margin-top: 20px;
}

.result-section {
  margin-top: 20px;
}

.result-overview {
  margin-bottom: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left h3 {
  margin: 0 0 4px 0;
  font-size: 20px;
  color: #2c3e50;
}

.file-name {
  margin: 0;
  color: #7f8c8d;
  font-size: 14px;
}

.stat-item {
  text-align: center;
  padding: 20px;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 8px;
}

.stat-label {
  color: #606266;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.score-info-icon {
  color: #409EFF;
  cursor: help;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.score-info-icon:hover {
  opacity: 1;
}

.risk-card,
.clause-card {
  height: 500px;
}

.risk-card :deep(.el-card__body),
.clause-card :deep(.el-card__body) {
  height: calc(100% - 60px);
  overflow-y: auto;
}

.risk-item {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 4px solid #e9ecef;
}

.risk-item:last-child {
  margin-bottom: 0;
}

.risk-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.risk-title {
  margin-left: 10px;
  font-weight: 600;
  color: #2c3e50;
}

.risk-description {
  margin: 8px 0;
  color: #5a6c7d;
  line-height: 1.5;
}

.risk-suggestion {
  margin-top: 10px;
  padding: 8px;
  background-color: #e8f4fd;
  border-radius: 4px;
  font-size: 13px;
  color: #0c5aa6;
}

.risk-legal-basis {
  margin-top: 10px;
  padding: 8px;
  background-color: #fff3e0;
  border-radius: 4px;
  font-size: 13px;
  color: #666;
}

.clause-content {
  line-height: 1.6;
}

.clause-content p {
  margin: 8px 0;
}

.result-actions {
  text-align: center;
  margin-top: 30px;
  padding: 20px;
}

.result-actions .el-button {
  margin: 0 10px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .result-header {
    flex-direction: column;
    gap: 15px;
  }
  
  .stat-item {
    padding: 15px;
  }
  
  .stat-number {
    font-size: 24px;
  }
  
  .risk-card,
  .clause-card {
    height: auto;
    margin-bottom: 20px;
  }
  
  .result-actions .el-button {
    display: block;
    width: 100%;
    margin: 10px 0;
  }
}

@media (max-width: 480px) {
  .logs-container {
    padding: 10px;
  }
  
  .log-item {
    flex-direction: column;
    gap: 4px;
  }
  
  .log-time {
    min-width: auto;
    font-size: 12px;
  }
}
</style>
