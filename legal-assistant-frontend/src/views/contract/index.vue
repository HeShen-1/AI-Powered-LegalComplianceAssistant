<template>
  <div class="contract-review-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>合同智能审查</h2>
      <p>上传合同文件，AI将为您提供专业的风险分析和合规建议</p>
    </div>

    <!-- 上传区域 -->
    <el-card class="upload-section" shadow="never" v-if="!currentReview">
      <template #header>
        <div class="section-header">
          <el-icon><Upload /></el-icon>
          <span>上传合同文件</span>
        </div>
      </template>

      <el-upload
        ref="uploadRef"
        class="upload-dragger"
        drag
        :auto-upload="false"
        :on-change="handleFileSelect"
        :on-remove="handleFileRemove"
        :before-upload="beforeUpload"
        accept=".pdf,.docx,.doc,.txt"
        :limit="1"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将合同文件拖拽到此处，或<em>点击选择文件</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word (.docx/.doc)、文本 (.txt) 格式，单个文件不超过 10MB
          </div>
        </template>
      </el-upload>

      <div v-if="selectedFile" class="selected-file">
        <div class="file-info">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ selectedFile.name }}</span>
          <span class="file-size">({{ formatFileSize(selectedFile.size) }})</span>
        </div>
        
        <el-button
          type="primary"
          size="large"
          :loading="uploading"
          @click="startUpload"
          :disabled="!selectedFile"
        >
          <el-icon><Upload /></el-icon>
          {{ uploading ? '上传中...' : '开始审查' }}
        </el-button>
      </div>
    </el-card>

    <!-- 审查进度区域 -->
    <el-card v-if="currentReview" class="review-section" shadow="never">
      <template #header>
        <div class="section-header">
          <el-icon><DocumentChecked /></el-icon>
          <span>审查进度</span>
          <div class="header-actions">
            <el-button
              v-if="currentReview.status === 'COMPLETED'"
              type="primary"
              @click="downloadReport"
              :loading="downloadLoading"
            >
              <el-icon><Download /></el-icon>
              下载报告
            </el-button>
            <el-button @click="resetReview">
              <el-icon><RefreshRight /></el-icon>
              重新审查
            </el-button>
          </div>
        </div>
      </template>

      <!-- 文件信息 -->
      <div class="file-summary">
        <div class="file-item">
          <el-icon><Document /></el-icon>
          <span>{{ currentReview.filename }}</span>
          <el-tag :type="getStatusType(currentReview.status)">
            {{ getStatusText(currentReview.status) }}
          </el-tag>
        </div>
      </div>

      <!-- 进度步骤 -->
      <el-steps :active="currentStep" class="review-steps" finish-status="success">
        <el-step title="文件上传" description="正在上传合同文件..." />
        <el-step title="内容解析" description="正在提取文档内容..." />
        <el-step title="AI分析" description="正在进行智能分析..." />
        <el-step title="风险评估" description="正在评估潜在风险..." />
        <el-step title="生成报告" description="正在生成审查报告..." />
      </el-steps>

      <!-- 实时日志 -->
      <div class="log-section">
        <h4>实时日志</h4>
        <div class="log-container" ref="logContainer">
          <div
            v-for="(log, index) in reviewLogs"
            :key="index"
            class="log-item"
            :class="log.type"
          >
            <span class="log-time">{{ formatLogTime(log.timestamp) }}</span>
            <span class="log-content">{{ log.message }}</span>
          </div>
        </div>
      </div>

      <!-- 审查结果 -->
      <div v-if="currentReview.status === 'COMPLETED' && reviewResult" class="result-section">
        <h4>审查结果概览</h4>
        
        <!-- 风险等级 -->
        <div class="risk-overview">
          <div class="risk-level">
            <span class="label">整体风险等级：</span>
            <el-tag
              :type="getRiskLevelType(reviewResult.riskLevel)"
              size="large"
              effect="dark"
            >
              {{ getRiskLevelText(reviewResult.riskLevel) }}
            </el-tag>
          </div>
          
          <div class="risk-score" v-if="reviewResult.riskScore">
            <span class="label">风险评分：</span>
            <el-progress
              :percentage="reviewResult.riskScore"
              :color="getRiskScoreColor(reviewResult.riskScore)"
              :show-text="true"
              :format="(percentage) => `${percentage}分`"
            />
          </div>
        </div>

        <!-- 风险条款 -->
        <div v-if="reviewResult.riskClauses?.length" class="risk-clauses">
          <h5>发现的风险条款</h5>
          <div class="clauses-list">
            <el-card
              v-for="(clause, index) in reviewResult.riskClauses"
              :key="index"
              class="clause-item"
              shadow="hover"
            >
              <div class="clause-header">
                <el-tag
                  :type="getRiskLevelType(clause.riskLevel)"
                  size="small"
                >
                  {{ getRiskLevelText(clause.riskLevel) }}
                </el-tag>
                <span class="clause-type">{{ clause.clauseType }}</span>
              </div>
              
              <div class="clause-content">
                <p><strong>原文：</strong>{{ clause.originalText }}</p>
                <p><strong>风险描述：</strong>{{ clause.riskDescription }}</p>
                <p v-if="clause.suggestion"><strong>建议：</strong>{{ clause.suggestion }}</p>
              </div>
            </el-card>
          </div>
        </div>

        <!-- 总结建议 -->
        <div v-if="reviewResult.summary" class="summary-section">
          <h5>总结与建议</h5>
          <div class="summary-content" v-html="renderMarkdown(reviewResult.summary)"></div>
        </div>
      </div>
    </el-card>

    <!-- 错误状态 -->
    <el-card v-if="currentReview?.status === 'FAILED'" class="error-section" shadow="never">
      <el-result
        icon="error"
        title="审查失败"
        sub-title="文件处理过程中发生错误，请重新上传或联系技术支持"
      >
        <template #extra>
          <el-button type="primary" @click="resetReview">重新开始</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import type { UploadInstance, UploadRawFile } from 'element-plus'
import type { ContractReview } from '@/types/api'
import {
  uploadContractApi,
  createAnalysisSSE,
  downloadReportApi
} from '@/api/contractService'
import {
  Upload,
  UploadFilled,
  Document,
  DocumentChecked,
  Download,
  RefreshRight
} from '@element-plus/icons-vue'

// 组件状态
const uploadRef = ref<UploadInstance>()
const logContainer = ref<HTMLElement>()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const downloadLoading = ref(false)
const currentReview = ref<ContractReview | null>(null)
const currentStep = ref(0)
const reviewResult = ref<any>(null)
const eventSource = ref<EventSource | null>(null)

// 审查日志
interface ReviewLog {
  timestamp: string
  message: string
  type: 'info' | 'success' | 'warning' | 'error'
}
const reviewLogs = ref<ReviewLog[]>([])

// 文件选择处理
const handleFileSelect = (file: any) => {
  selectedFile.value = file.raw
}

// 文件移除处理
const handleFileRemove = () => {
  selectedFile.value = null
}

// 文件上传前验证
const beforeUpload = (file: UploadRawFile) => {
  const isValidType = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword', 'text/plain'].includes(file.type)
  const isValidSize = file.size / 1024 / 1024 < 10

  if (!isValidType) {
    ElMessage.error('只支持 PDF、Word、文本格式的文件')
    return false
  }

  if (!isValidSize) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }

  return false // 阻止自动上传
}

// 开始上传和审查
const startUpload = async () => {
  if (!selectedFile.value) return

  uploading.value = true
  addLog('开始上传文件...', 'info')

  try {
    // 上传文件
    const uploadResponse = await uploadContractApi(selectedFile.value)
    
    if (uploadResponse.data.success) {
      currentReview.value = {
        id: uploadResponse.data.reviewId,
        filename: selectedFile.value.name,
        status: 'PENDING'
      } as ContractReview

      addLog('文件上传成功，开始审查...', 'success')
      currentStep.value = 1

      // 开始异步审查
      startAsyncAnalysis(uploadResponse.data.reviewId)
    } else {
      throw new Error(uploadResponse.data.message || '上传失败')
    }
  } catch (error: any) {
    addLog(`上传失败: ${error.message}`, 'error')
    ElMessage.error('文件上传失败')
    resetReview()
  } finally {
    uploading.value = false
  }
}

// 开始异步分析
const startAsyncAnalysis = (reviewId: number) => {
  eventSource.value = createAnalysisSSE(
    reviewId,
    handleSSEMessage,
    handleSSEError
  )
}

// 处理SSE消息
const handleSSEMessage = (event: MessageEvent) => {
  try {
    const data = JSON.parse(event.data)
    
    switch (data.type) {
      case 'progress':
        handleProgressUpdate(data.data)
        break
      case 'result':
        handlePartialResult(data.data)
        break
      case 'complete':
        handleAnalysisComplete(data.data)
        break
      case 'error':
        handleAnalysisError(data.data)
        break
    }
  } catch (error) {
    console.error('Failed to parse SSE message:', error)
  }
}

// 处理进度更新
const handleProgressUpdate = (data: any) => {
  addLog(data.message, 'info')
  
  if (data.step !== undefined) {
    currentStep.value = data.step
  }

  if (currentReview.value) {
    currentReview.value.status = 'PROCESSING'
  }
}

// 处理部分结果
const handlePartialResult = (data: any) => {
  if (data.riskClauses) {
    addLog(`发现 ${data.riskClauses.length} 个风险条款`, 'warning')
  }
}

// 处理分析完成
const handleAnalysisComplete = (data: any) => {
  addLog('审查完成！', 'success')
  currentStep.value = 5
  
  if (currentReview.value) {
    currentReview.value.status = 'COMPLETED'
    currentReview.value.riskLevel = data.riskLevel
  }
  
  reviewResult.value = data
  closeSSEConnection()
}

// 处理分析错误
const handleAnalysisError = (data: any) => {
  addLog(`审查失败: ${data.message}`, 'error')
  
  if (currentReview.value) {
    currentReview.value.status = 'FAILED'
  }
  
  closeSSEConnection()
}

// 处理SSE错误
const handleSSEError = (event: Event) => {
  console.error('SSE connection error:', event)
  addLog('连接中断，请重新尝试', 'error')
  closeSSEConnection()
}

// 关闭SSE连接
const closeSSEConnection = () => {
  if (eventSource.value) {
    eventSource.value.close()
    eventSource.value = null
  }
}

// 下载报告
const downloadReport = async () => {
  if (!currentReview.value) return

  downloadLoading.value = true
  try {
    const response = await downloadReportApi(currentReview.value.id)
    
    // 创建下载链接
    const blob = new Blob([response.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${currentReview.value.filename}_审查报告.pdf`
    link.click()
    URL.revokeObjectURL(url)
    
    ElMessage.success('报告下载成功')
  } catch (error) {
    ElMessage.error('报告下载失败')
  } finally {
    downloadLoading.value = false
  }
}

// 重置审查
const resetReview = async () => {
  if (currentReview.value?.status === 'PROCESSING') {
    try {
      await ElMessageBox.confirm(
        '审查正在进行中，确定要重新开始吗？',
        '确认',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }

  closeSSEConnection()
  currentReview.value = null
  reviewResult.value = null
  selectedFile.value = null
  currentStep.value = 0
  reviewLogs.value = []
  uploadRef.value?.clearFiles()
}

// 添加日志
const addLog = (message: string, type: ReviewLog['type'] = 'info') => {
  reviewLogs.value.push({
    timestamp: new Date().toISOString(),
    message,
    type
  })
  
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
}

// 辅助函数
const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const formatLogTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour12: false })
}

const getStatusType = (status: string) => {
  const statusMap = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return statusMap[status as keyof typeof statusMap] || 'info'
}

const getStatusText = (status: string) => {
  const statusMap = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return statusMap[status as keyof typeof statusMap] || status
}

const getRiskLevelType = (level: string) => {
  const levelMap = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return levelMap[level as keyof typeof levelMap] || 'info'
}

const getRiskLevelText = (level: string) => {
  const levelMap = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return levelMap[level as keyof typeof levelMap] || level
}

const getRiskScoreColor = (score: number) => {
  if (score <= 30) return '#67c23a'
  if (score <= 70) return '#e6a23c'
  return '#f56c6c'
}

const renderMarkdown = (content: string) => {
  return marked(content)
}

// 组件卸载时清理资源
onUnmounted(() => {
  closeSSEConnection()
})
</script>

<style scoped>
.contract-review-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
  text-align: center;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.page-header p {
  margin: 0;
  color: var(--text-secondary);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 12px;
}

.upload-section {
  margin-bottom: 24px;
}

.upload-dragger {
  width: 100%;
}

.selected-file {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-name {
  font-weight: 500;
}

.file-size {
  color: var(--text-secondary);
  font-size: 14px;
}

.review-section {
  margin-bottom: 24px;
}

.file-summary {
  margin-bottom: 24px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.review-steps {
  margin: 32px 0;
}

.log-section {
  margin: 24px 0;
}

.log-section h4 {
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.log-container {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
  padding: 12px;
}

.log-item {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.5;
}

.log-time {
  color: var(--text-placeholder);
  white-space: nowrap;
  font-family: monospace;
}

.log-content {
  flex: 1;
}

.log-item.info .log-content {
  color: var(--text-regular);
}

.log-item.success .log-content {
  color: var(--success-color);
}

.log-item.warning .log-content {
  color: var(--warning-color);
}

.log-item.error .log-content {
  color: var(--danger-color);
}

.result-section {
  margin-top: 24px;
}

.result-section h4,
.result-section h5 {
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.risk-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 24px;
}

.risk-level,
.risk-score {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label {
  font-weight: 500;
  min-width: 120px;
}

.risk-clauses {
  margin-bottom: 24px;
}

.clauses-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.clause-item {
  margin-bottom: 0;
}

.clause-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.clause-type {
  font-weight: 500;
}

.clause-content p {
  margin: 8px 0;
  line-height: 1.6;
}

.summary-section {
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.summary-content {
  line-height: 1.6;
}

.error-section {
  margin-top: 24px;
}

@media (max-width: 768px) {
  .selected-file {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  
  .risk-overview {
    gap: 12px;
  }
  
  .risk-level,
  .risk-score {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .label {
    min-width: auto;
  }
}
</style>
