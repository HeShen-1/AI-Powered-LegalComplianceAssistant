<template>
  <div class="dashboard-container">
    <!-- 欢迎区域 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-text">
          <h1>欢迎回来，{{ userStore.userInfo?.fullName || userStore.userInfo?.username }}！</h1>
          <p>您的专业法律AI助手，为您提供智能的法律文档审查和咨询服务</p>
        </div>
        <div class="welcome-actions">
          <el-button type="primary" size="large" @click="$router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon>
            开始咨询
          </el-button>
          <el-button size="large" @click="$router.push('/contract')">
            <el-icon><Document /></el-icon>
            审查合同
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon chat-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.totalChats }}</div>
            <div class="stat-label">AI对话次数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon contract-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.totalReviews }}</div>
            <div class="stat-label">合同审查次数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon risk-icon">
            <el-icon><WarningFilled /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.riskIssues }}</div>
            <div class="stat-label">发现风险条款</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-content">
          <div class="stat-icon time-icon">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stats.savedHours }}</div>
            <div class="stat-label">节省工作时间(小时)</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 主要功能区域 -->
    <div class="main-content">
      <div class="left-column">
        <!-- 快速操作 -->
        <el-card class="quick-actions" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Lightning /></el-icon>
              <span>快速操作</span>
            </div>
          </template>

          <div class="action-grid">
            <div class="action-item" @click="$router.push('/chat')">
              <div class="action-icon">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="action-text">
                <h4>AI智能问答</h4>
                <p>专业法律咨询，即问即答</p>
              </div>
            </div>

            <div class="action-item" @click="$router.push('/contract')">
              <div class="action-icon">
                <el-icon><Document /></el-icon>
              </div>
              <div class="action-text">
                <h4>合同智能审查</h4>
                <p>AI深度分析，风险预警</p>
              </div>
            </div>

            <div class="action-item" @click="$router.push('/history')">
              <div class="action-icon">
                <el-icon><Clock /></el-icon>
              </div>
              <div class="action-text">
                <h4>历史记录</h4>
                <p>查看过往审查记录</p>
              </div>
            </div>

            <div class="action-item" @click="openKnowledgeCenter">
              <div class="action-icon">
                <el-icon><Reading /></el-icon>
              </div>
              <div class="action-text">
                <h4>法律知识库</h4>
                <p>海量法律法规查询</p>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 最近审查 -->
        <el-card class="recent-reviews" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><DocumentChecked /></el-icon>
              <span>最近审查</span>
              <el-button text type="primary" @click="$router.push('/history')">
                查看全部
              </el-button>
            </div>
          </template>

          <div v-if="recentReviews.length === 0" class="empty-state">
            <el-empty description="暂无审查记录" :image-size="80">
              <el-button type="primary" @click="$router.push('/contract')">
                开始审查
              </el-button>
            </el-empty>
          </div>

          <div v-else class="reviews-list">
            <div
              v-for="review in recentReviews"
              :key="review.id"
              class="review-item"
              @click="viewReviewDetail(review)"
            >
              <div class="review-info">
                <div class="review-name">{{ review.filename }}</div>
                <div class="review-time">
                  {{ formatTime(review.createdAt) }}
                </div>
              </div>
              <div class="review-status">
                <el-tag :type="getStatusType(review.status)">
                  {{ getStatusText(review.status) }}
                </el-tag>
                <el-tag
                  v-if="review.riskLevel"
                  :type="getRiskLevelType(review.riskLevel)"
                  size="small"
                >
                  {{ getRiskLevelText(review.riskLevel) }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <div class="right-column">
        <!-- 系统公告 -->
        <el-card class="announcements" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Bell /></el-icon>
              <span>系统公告</span>
            </div>
          </template>

          <div class="announcement-list">
            <div
              v-for="announcement in announcements"
              :key="announcement.id"
              class="announcement-item"
            >
              <div class="announcement-title">{{ announcement.title }}</div>
              <div class="announcement-content">{{ announcement.content }}</div>
              <div class="announcement-time">{{ formatTime(announcement.createdAt) }}</div>
            </div>
          </div>
        </el-card>

        <!-- 使用技巧 -->
        <el-card class="tips" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><QuestionFilled /></el-icon>
              <span>使用技巧</span>
            </div>
          </template>

          <div class="tips-list">
            <div
              v-for="tip in usageTips"
              :key="tip.id"
              class="tip-item"
            >
              <div class="tip-icon">
                <el-icon><InfoFilled /></el-icon>
              </div>
              <div class="tip-content">
                <h5>{{ tip.title }}</h5>
                <p>{{ tip.content }}</p>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { getMyReviewsApi } from '@/api/contractService'
import { ElMessage } from 'element-plus'
import type { ContractReview } from '@/types/api'
import {
  ChatDotRound,
  Document,
  WarningFilled,
  Clock,
  Lightning,
  DocumentChecked,
  Reading,
  Bell,
  QuestionFilled,
  InfoFilled
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

// 统计数据
const stats = reactive({
  totalChats: 0,
  totalReviews: 0,
  riskIssues: 0,
  savedHours: 0
})

// 最近审查记录
const recentReviews = ref<ContractReview[]>([])

// 系统公告
const announcements = ref([
  {
    id: 1,
    title: '系统升级通知',
    content: '系统将于本周六凌晨2:00-4:00进行维护升级，期间服务可能短暂中断。',
    createdAt: new Date().toISOString()
  },
  {
    id: 2,
    title: '新功能上线',
    content: 'AI智能问答功能全新升级，支持更准确的法律条文检索和分析。',
    createdAt: new Date(Date.now() - 86400000).toISOString()
  }
])

// 使用技巧
const usageTips = ref([
  {
    id: 1,
    title: '提问技巧',
    content: '在AI问答中，描述具体的法律场景能获得更准确的建议。'
  },
  {
    id: 2,
    title: '合同审查',
    content: '上传前请确保合同文件格式正确，支持PDF、Word等格式。'
  },
  {
    id: 3,
    title: '风险提醒',
    content: '高风险条款需要特别关注，建议咨询专业律师进一步确认。'
  }
])

// 加载仪表盘数据
const loadDashboardData = async () => {
  try {
    // 加载最近审查记录
    const reviewsResponse = await getMyReviewsApi({ page: 0, size: 5 })
    if (reviewsResponse.data.success) {
      recentReviews.value = reviewsResponse.data.data
      
      // 更新统计数据
      stats.totalReviews = reviewsResponse.data.totalElements
      stats.riskIssues = recentReviews.value.filter(r => r.riskLevel === 'HIGH').length
      stats.savedHours = Math.ceil(stats.totalReviews * 2.5) // 假设每次审查节省2.5小时
    }

    // 模拟其他统计数据
    stats.totalChats = Math.floor(Math.random() * 50) + 20
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  }
}

// 查看审查详情
const viewReviewDetail = (review: ContractReview) => {
  // 这里可以打开详情对话框或跳转到详情页
  ElMessage.info(`查看审查详情: ${review.filename}`)
}

// 打开知识中心
const openKnowledgeCenter = () => {
  ElMessage.info('知识中心功能开发中...')
}

// 辅助函数
const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 3600000) { // 1小时内
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) { // 1天内
    return `${Math.floor(diff / 3600000)}小时前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
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

// 组件挂载时加载数据
onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped>
.dashboard-container {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.welcome-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: white;
}

.welcome-text h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 300;
}

.welcome-text p {
  margin: 0;
  opacity: 0.9;
  font-size: 16px;
}

.welcome-actions {
  display: flex;
  gap: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  border: none;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}

.chat-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.contract-icon {
  background: linear-gradient(135deg, #f093fb, #f5576c);
}

.risk-icon {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}

.time-icon {
  background: linear-gradient(135deg, #43e97b, #38f9d7);
}

.stat-number {
  font-size: 32px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.main-content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.card-header .el-button {
  margin-left: auto;
}

.quick-actions {
  margin-bottom: 24px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.action-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: var(--primary-color);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.action-text h4 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: var(--text-primary);
}

.action-text p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.recent-reviews {
  margin-bottom: 24px;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.review-item:hover {
  border-color: var(--primary-color);
  background: #f8f9ff;
}

.review-name {
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.review-time {
  font-size: 14px;
  color: var(--text-secondary);
}

.review-status {
  display: flex;
  gap: 8px;
}

.announcements {
  margin-bottom: 24px;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item {
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #f8f9fa;
}

.announcement-title {
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.announcement-content {
  font-size: 14px;
  color: var(--text-regular);
  line-height: 1.5;
  margin-bottom: 8px;
}

.announcement-time {
  font-size: 12px;
  color: var(--text-placeholder);
}

.tips-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tip-item {
  display: flex;
  gap: 12px;
}

.tip-icon {
  color: var(--primary-color);
  font-size: 16px;
  margin-top: 2px;
}

.tip-content h5 {
  margin: 0 0 4px 0;
  font-size: 14px;
  color: var(--text-primary);
}

.tip-content p {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

@media (max-width: 1024px) {
  .main-content {
    grid-template-columns: 1fr;
  }
  
  .welcome-content {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }
  
  .action-grid {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
  }
  
  .welcome-actions {
    flex-direction: column;
    width: 100%;
    gap: 12px;
  }
  
  .welcome-actions .el-button {
    width: 100%;
  }
}
</style>
