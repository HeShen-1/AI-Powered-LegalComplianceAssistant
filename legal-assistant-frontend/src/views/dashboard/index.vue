<template>
  <div class="dashboard-container">
    <!-- 欢迎区域 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2>👋 欢迎回来，{{ userStore.userInfo?.fullName || userStore.userInfo?.username }}！</h2>
          <p class="welcome-subtitle">
            今天是 {{ currentDate }}，让我们开始您的法律工作之旅
          </p>
        </div>
        <div class="welcome-actions">
          <el-button type="primary" size="large" @click="$router.push('/contract')">
            <el-icon><Document /></el-icon>
            开始审查合同
          </el-button>
          <el-button type="success" size="large" @click="$router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon>
            AI智能问答
          </el-button>
        </div>
      </div>
    </el-card>
    
    <!-- 数据统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon total">
              <el-icon size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.totalReviews }}</div>
              <div class="stat-label">总审查数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon completed">
              <el-icon size="32"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.completedReviews }}</div>
              <div class="stat-label">已完成</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon processing">
              <el-icon size="32"><Loading /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.processingReviews }}</div>
              <div class="stat-label">处理中</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon high-risk">
              <el-icon size="32"><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ stats.highRiskReviews }}</div>
              <div class="stat-label">高风险</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 主要内容区域 -->
    <el-row :gutter="20" class="main-content">
      <!-- 最近审查记录 -->
      <el-col :xs="24" :lg="14">
        <el-card class="recent-reviews" shadow="never">
          <template #header>
            <div class="card-header">
              <h4>📋 最近审查记录</h4>
              <el-button type="text" @click="$router.push('/history')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          
          <div v-if="recentReviews.length > 0" class="reviews-list">
            <div
              v-for="review in recentReviews"
              :key="review.id"
              class="review-item"
              @click="viewReview(review)"
            >
              <div class="review-icon">
                <el-icon size="24" color="#409EFF"><Document /></el-icon>
              </div>
              <div class="review-info">
                <div class="review-title">{{ review.originalFilename }}</div>
                <div class="review-meta">
                  <span class="review-time">{{ formatTime(review.createdAt) }}</span>
                  <el-tag
                    :type="getStatusType(review.reviewStatus)"
                    size="small"
                  >
                    {{ getStatusText(review.reviewStatus) }}
                  </el-tag>
                  <el-tag
                    v-if="review.riskLevel"
                    :type="getRiskType(review.riskLevel)"
                    size="small"
                  >
                    {{ getRiskText(review.riskLevel) }}
                  </el-tag>
                </div>
              </div>
              <div class="review-actions">
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无审查记录" />
        </el-card>
      </el-col>
      
      <!-- 快捷操作和系统信息 -->
      <el-col :xs="24" :lg="10">
        <!-- 快捷操作 -->
        <el-card class="quick-actions" shadow="never">
          <template #header>
            <h4>⚡ 快捷操作</h4>
          </template>
          
          <div class="actions-grid">
            <div class="action-item" @click="$router.push('/contract')">
              <div class="action-icon upload">
                <el-icon size="24"><UploadFilled /></el-icon>
              </div>
              <div class="action-text">
                <div class="action-title">上传合同</div>
                <div class="action-desc">开始新的合同审查</div>
              </div>
            </div>
            
            <div class="action-item" @click="$router.push('/chat')">
              <div class="action-icon chat">
                <el-icon size="24"><ChatDotRound /></el-icon>
              </div>
              <div class="action-text">
                <div class="action-title">AI问答</div>
                <div class="action-desc">咨询法律问题</div>
              </div>
            </div>
            
            <div class="action-item" @click="$router.push('/history')">
              <div class="action-icon history">
                <el-icon size="24"><Clock /></el-icon>
              </div>
              <div class="action-text">
                <div class="action-title">查看历史</div>
                <div class="action-desc">管理审查记录</div>
              </div>
            </div>
            
            <div class="action-item" @click="$router.push('/profile')">
              <div class="action-icon profile">
                <el-icon size="24"><User /></el-icon>
              </div>
              <div class="action-text">
                <div class="action-title">个人设置</div>
                <div class="action-desc">管理账户信息</div>
              </div>
            </div>
          </div>
        </el-card>
        
        <!-- 系统信息 -->
        <el-card class="system-info" shadow="never">
          <template #header>
            <h4>ℹ️ 系统信息</h4>
          </template>
          
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">系统版本：</span>
              <span class="info-value">v1.0.0</span>
            </div>
            <div class="info-item">
              <span class="info-label">AI模型：</span>
              <span class="info-value">DeepSeek</span>
            </div>
            <div class="info-item">
              <span class="info-label">知识库：</span>
              <span class="info-value">最新法律法规</span>
            </div>
            <div class="info-item">
              <span class="info-label">最后更新：</span>
              <span class="info-value">{{ lastUpdateTime }}</span>
            </div>
          </div>
          
          <div class="system-status">
            <el-alert
              title="系统运行正常"
              type="success"
              :closable="false"
              show-icon
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Document,
  ChatDotRound,
  CircleCheck,
  Loading,
  Warning,
  ArrowRight,
  UploadFilled,
  Clock,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { getMyReviewsApi } from '@/api/contractService'

// 类型定义
interface ReviewRecord {
  id: number
  filename: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  createdAt: string
  completedAt?: string
}

interface DashboardStats {
  totalReviews: number
  completedReviews: number
  processingReviews: number
  highRiskReviews: number
}

const router = useRouter()
const userStore = useUserStore()

// 响应式数据
const stats = ref<DashboardStats>({
  totalReviews: 0,
  completedReviews: 0,
  processingReviews: 0,
  highRiskReviews: 0
})

const recentReviews = ref<ReviewRecord[]>([])

// 计算属性
const currentDate = computed(() => {
  return new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
})

const lastUpdateTime = computed(() => {
  return new Date().toLocaleDateString('zh-CN')
})

// 工具函数
const formatTime = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  
  if (minutes < 60) {
    return `${minutes}分钟前`
  } else if (hours < 24) {
    return `${hours}小时前`
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

const getStatusType = (status: string) => {
  const typeMap = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return typeMap[status as keyof typeof typeMap] || 'info'
}

const getStatusText = (status: string) => {
  const textMap = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return textMap[status as keyof typeof textMap] || status
}

const getRiskType = (level: string) => {
  const typeMap = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger'
  }
  return typeMap[level as keyof typeof typeMap] || 'info'
}

const getRiskText = (level: string) => {
  const textMap = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险'
  }
  return textMap[level as keyof typeof textMap] || level
}

// 数据获取
const fetchDashboardData = async () => {
  try {
    // 调用真实API获取审查记录
    // 注意：后端接收的参数是 page 和 size，page 从 0 开始
    const response = await getMyReviewsApi({ page: 0, size: 10 })
    
    if (response.data && response.data.data) {
      const reviews = response.data.data.content || []
      
      // 计算统计数据
      stats.value = {
        totalReviews: response.data.data.totalElements || 0,
        completedReviews: reviews.filter((r: any) => r.reviewStatus === 'COMPLETED').length,
        processingReviews: reviews.filter((r: any) => r.reviewStatus === 'PROCESSING').length,
        highRiskReviews: reviews.filter((r: any) => r.riskLevel === 'HIGH').length
      }
      
      // 获取最近5条审查记录
      recentReviews.value = reviews.slice(0, 5)
    } else {
      // 如果没有数据，初始化为空
      stats.value = {
        totalReviews: 0,
        completedReviews: 0,
        processingReviews: 0,
        highRiskReviews: 0
      }
      recentReviews.value = []
    }
  } catch (error) {
    console.error('Failed to fetch dashboard data:', error)
    // 初始化为空数据而不是显示错误
    stats.value = {
      totalReviews: 0,
      completedReviews: 0,
      processingReviews: 0,
      highRiskReviews: 0
    }
    recentReviews.value = []
  }
}

// 事件处理
const viewReview = (review: ReviewRecord) => {
  if (review.reviewStatus === 'COMPLETED') {
    router.push('/history')
  } else {
    ElMessage.info('审查尚未完成')
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchDashboardData()
})
</script>

<style scoped>
.dashboard-container {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 20px;
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 0;
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #2c3e50;
}

.welcome-subtitle {
  margin: 0;
  color: #7f8c8d;
  font-size: 14px;
}

.welcome-actions {
  display: flex;
  gap: 12px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon.total {
  background-color: #e3f2fd;
  color: #1976d2;
}

.stat-icon.completed {
  background-color: #e8f5e8;
  color: #388e3c;
}

.stat-icon.processing {
  background-color: #fff3e0;
  color: #f57c00;
}

.stat-icon.high-risk {
  background-color: #ffebee;
  color: #d32f2f;
}

.stat-info {
  flex: 1;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #2c3e50;
  margin-bottom: 4px;
}

.stat-label {
  color: #7f8c8d;
  font-size: 14px;
}

.main-content {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h4 {
  margin: 0;
  color: #2c3e50;
}

.reviews-list {
  max-height: 400px;
  overflow-y: auto;
}

.review-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
  border-bottom: 1px solid #f0f0f0;
}

.review-item:hover {
  background-color: #f8f9fa;
}

.review-item:last-child {
  border-bottom: none;
}

.review-icon {
  margin-right: 16px;
}

.review-info {
  flex: 1;
}

.review-title {
  font-weight: 500;
  color: #2c3e50;
  margin-bottom: 8px;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.review-time {
  color: #7f8c8d;
  font-size: 13px;
}

.review-actions {
  color: #c0c4cc;
}

.quick-actions {
  margin-bottom: 20px;
}

.actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.action-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #e4e7ed;
}

.action-item:hover {
  border-color: #409EFF;
  background-color: #f0f9ff;
}

.action-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.action-icon.upload {
  background-color: #e3f2fd;
  color: #1976d2;
}

.action-icon.chat {
  background-color: #e8f5e8;
  color: #388e3c;
}

.action-icon.history {
  background-color: #fff3e0;
  color: #f57c00;
}

.action-icon.profile {
  background-color: #f3e5f5;
  color: #7b1fa2;
}

.action-text {
  flex: 1;
}

.action-title {
  font-weight: 500;
  color: #2c3e50;
  margin-bottom: 4px;
}

.action-desc {
  color: #7f8c8d;
  font-size: 12px;
}

.system-info {
  height: fit-content;
}

.info-list {
  margin-bottom: 20px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  color: #7f8c8d;
  font-size: 14px;
}

.info-value {
  color: #2c3e50;
  font-size: 14px;
  font-weight: 500;
}

.system-status {
  margin-top: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .welcome-content {
    flex-direction: column;
    gap: 20px;
    text-align: center;
  }
  
  .welcome-actions {
    flex-direction: column;
    width: 100%;
  }
  
  .welcome-actions .el-button {
    width: 100%;
  }
  
  .stats-row .el-col {
    margin-bottom: 15px;
  }
  
  .actions-grid {
    grid-template-columns: 1fr;
  }
  
  .review-item {
    padding: 12px;
  }
  
  .review-meta {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .welcome-text h2 {
    font-size: 20px;
  }
  
  .stat-number {
    font-size: 24px;
  }
  
  .action-item {
    padding: 12px;
  }
  
  .action-icon {
    width: 40px;
    height: 40px;
  }
  
  .action-title {
    font-size: 14px;
  }
  
  .action-desc {
    font-size: 11px;
  }
}
</style>